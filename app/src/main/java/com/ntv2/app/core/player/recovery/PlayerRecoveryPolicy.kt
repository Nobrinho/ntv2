package com.ntv2.app.core.player.recovery

import com.ntv2.app.core.player.exoplayer.DecoderWindows
import com.ntv2.app.core.player.exoplayer.VideoDecoderPolicy

/** O que o coordinator deve fazer diante de uma falha durante a reprodução. */
sealed interface RecoveryAction {
    /** Nada a fazer (ex.: limite atingido para um problema que não vira erro, como congelamento). */
    data object None : RecoveryAction

    /** Limite de tentativas atingido: mostrar o erro. */
    data object GiveUp : RecoveryAction

    /**
     * O hardware falha sempre no mesmo ponto e o vídeo é grande demais para o software (> 1080p):
     * recriar não adianta. Mostrar o erro já, dizendo que o aparelho não reproduz este vídeo.
     */
    data object Unsupported : RecoveryAction

    /**
     * Recriar só o decodificador em [positionMs] (o download segue). [useSoftware] escolhe o
     * decodificador; [skippedMs] > 0 = pulou um trecho que nem o software decodifica;
     * [newBadPositionMs] = ponto de falha do hardware a gravar na memória do vídeo.
     */
    data class RestartDecoder(
        val positionMs: Long,
        val useSoftware: Boolean,
        val skippedMs: Long = 0L,
        val newBadPositionMs: Long? = null
    ) : RecoveryAction

    /** Reconectar o TDLib e reabrir o vídeo em [positionMs] depois de [delayMs]. */
    data class Reopen(val positionMs: Long, val delayMs: Long) : RecoveryAction
}

/**
 * Regras de recuperação do player num lugar só (lógica pura, testável na JVM). Antes ficavam
 * espalhadas pelo coordinator em contadores soltos — daí bugs como o limite que nunca zerava.
 *
 * - Erro do decodificador: recria; no hardware, grava o ponto e liga o software em volta dele
 *   ([DecoderWindows]); já no software, o trecho está corrompido → pula à frente (pulo crescente).
 * - Congelamento (sem quadros novos com o relógio andando): igual ao erro de hardware.
 * - Rajada de quadros descartados (VP9 no AFTKM): recria, com intervalo mínimo entre recriações.
 * - Erro de rede/arquivo: reconecta e reabre, com espera crescente.
 * - [HEALTHY_RESET_MS] tocando bem zera os limites; outro vídeo zera tudo.
 */
class PlayerRecoveryPolicy {

    companion object {
        const val MAX_DECODER_ERRORS = 6
        /** Falhas seguidas do hardware no mesmo ponto, sem software possível, até desistir. */
        const val MAX_SAME_SPOT_HW_ERRORS = 3
        const val SAME_SPOT_MS = 2_000L
        const val DECODER_ERROR_SKIP_MS = 2_000L
        const val MAX_FREEZES = 5
        const val DROP_BURST_COOLDOWN_MS = 30_000L
        const val MAX_IO_ERRORS = 3
        const val IO_BACKOFF_MS = 2_000L
        const val HEALTHY_RESET_MS = 60_000L
    }

    private var mediaId: String? = null
    private var decoderErrors = 0
    private var lastHwErrorAt: Long? = null
    private var sameSpotHwErrors = 0
    private var freezes = 0
    private var ioErrors = 0
    private var lastDropBurstAt: Long? = null
    private var healthyForMs = 0L

    /** Falhas do decodificador neste vídeo (congelamentos + erros); não zera com o tempo. */
    var decoderIncidents = 0
        private set

    /** Posições (ms) em que o hardware falhou neste vídeo (memória + desta sessão). */
    var badPositions: List<Long> = emptyList()
        private set

    val freezeCount: Int get() = freezes
    val ioErrorCount: Int get() = ioErrors

    /**
     * Início de um preparo. Outro vídeo: zera os contadores e carrega os pontos de falha lembrados
     * ([remembered] só é chamado nesse caso). Retorna true se é outro vídeo.
     */
    fun startMedia(id: String, remembered: () -> List<Long>): Boolean {
        if (id == mediaId) return false
        mediaId = id
        decoderErrors = 0
        lastHwErrorAt = null
        sameSpotHwErrors = 0
        freezes = 0
        ioErrors = 0
        lastDropBurstAt = null
        healthyForMs = 0L
        decoderIncidents = 0
        badPositions = remembered()
        return true
    }

    /** Usar o decodificador de software em [positionMs]? (dentro de uma janela de falha, até 1080p) */
    fun softwareWanted(positionMs: Long, videoHeight: Int): Boolean =
        VideoDecoderPolicy.softwareCapable(videoHeight) &&
            DecoderWindows.softwareUntil(positionMs, badPositions) != null

    fun onDecoderError(positionMs: Long, durationMs: Long, videoHeight: Int, usingSoftware: Boolean): RecoveryAction {
        if (decoderErrors >= MAX_DECODER_ERRORS) return RecoveryAction.GiveUp
        // Ex.: VP9 4K que o decodificador do Fire TV recusa logo ao configurar: cada recriação falhava
        // de novo em 0 ms e o usuário via só o pôster por ~25 s até o limite geral.
        if (!usingSoftware && !VideoDecoderPolicy.softwareCapable(videoHeight)) {
            val last = lastHwErrorAt
            sameSpotHwErrors = if (last != null && kotlin.math.abs(positionMs - last) < SAME_SPOT_MS) sameSpotHwErrors + 1 else 1
            lastHwErrorAt = positionMs
            if (sameSpotHwErrors >= MAX_SAME_SPOT_HW_ERRORS) return RecoveryAction.Unsupported
        }
        decoderErrors++
        val skipMs = if (usingSoftware) DECODER_ERROR_SKIP_MS * decoderErrors else 0L
        val resumeAt = (positionMs + skipMs).let { if (durationMs > 0L) it.coerceAtMost(durationMs - 1_000L) else it }
        val newBad = recordIncident(positionMs, videoHeight, usingSoftware)
        return RecoveryAction.RestartDecoder(
            positionMs = resumeAt,
            useSoftware = usingSoftware || softwareWanted(positionMs, videoHeight),
            skippedMs = skipMs,
            newBadPositionMs = newBad
        )
    }

    fun onFreeze(positionMs: Long, videoHeight: Int, usingSoftware: Boolean): RecoveryAction {
        if (freezes >= MAX_FREEZES) return RecoveryAction.None
        freezes++
        val newBad = recordIncident(positionMs, videoHeight, usingSoftware)
        return RecoveryAction.RestartDecoder(
            positionMs = positionMs,
            useSoftware = usingSoftware || softwareWanted(positionMs, videoHeight),
            newBadPositionMs = newBad
        )
    }

    /** Rajada de quadros descartados num aparelho/codec conhecido por isso (VP9 no AFTKM). */
    fun onDropBurst(positionMs: Long, nowMs: Long, usingSoftware: Boolean): RecoveryAction {
        val last = lastDropBurstAt
        if (freezes >= MAX_FREEZES || (last != null && nowMs - last < DROP_BURST_COOLDOWN_MS)) {
            return RecoveryAction.None
        }
        freezes++
        lastDropBurstAt = nowMs
        return RecoveryAction.RestartDecoder(positionMs = positionMs, useSoftware = usingSoftware)
    }

    fun onIoError(positionMs: Long): RecoveryAction {
        if (ioErrors >= MAX_IO_ERRORS) return RecoveryAction.GiveUp
        ioErrors++
        return RecoveryAction.Reopen(positionMs = positionMs, delayMs = IO_BACKOFF_MS * ioErrors)
    }

    /** A cada segundo tocando: [healthy] = sem congelar e sem rajada de descarte. */
    fun onPlayingSecond(healthy: Boolean) {
        healthyForMs = if (healthy) healthyForMs + 1_000L else 0L
        if (healthyForMs >= HEALTHY_RESET_MS) {
            decoderErrors = 0
            lastHwErrorAt = null
            sameSpotHwErrors = 0
            freezes = 0
            ioErrors = 0
        }
    }

    /** Pausado, carregando ou parado: a contagem de "tocando bem" recomeça. */
    fun onNotPlaying() {
        healthyForMs = 0L
    }

    /** Falha do HARDWARE (até 1080p) grava o ponto; falha já no software só conta. */
    private fun recordIncident(positionMs: Long, videoHeight: Int, usingSoftware: Boolean): Long? {
        decoderIncidents++
        if (usingSoftware || !VideoDecoderPolicy.softwareCapable(videoHeight)) return null
        badPositions = DecoderWindows.addPoint(badPositions, positionMs)
        return positionMs
    }
}
