package com.ntv2.app.core.player.recovery

/** O que a tela deve fazer com o download parado. */
sealed interface StallAction {
    data object None : StallAction

    /** Parado há um tempo: pedir ao TDLib para refazer as conexões (sockets mortos). */
    data object RefreshNetwork : StallAction

    /** Parado demais: reiniciar (tentativa [attempt] de [StallPolicy.maxRestarts]). */
    data class Restart(val attempt: Int) : StallAction

    /** Reinícios esgotados e ainda parado: erro "download parado" ([lastBytes] recebidos). */
    data class FailStalled(val lastBytes: Long) : StallAction

    /** Parado por falta de espaço (o player deixa de baixar de propósito) e a limpeza não resolveu. */
    data object FailLowStorage : StallAction
}

/**
 * Decide o que fazer com um download parado (lógica pura, testável na JVM). Usada pela tela antes do
 * vídeo começar (baixando o início) e durante a reprodução (esperando bytes).
 *
 * - Progresso = QUALQUER mudança no total baixado (ele volta a 0 quando a cópia local é recriada) ou
 *   TDLib reconectando (esse tempo não conta).
 * - Falta de espaço não é rede: não reconecta nem reinicia; dá tempo à limpeza automática e, se não
 *   resolver, vira erro de armazenamento.
 * - Tocando bem por [healthyResetMs], os reinícios zeram (o limite não vale pelo filme inteiro).
 *
 * @param initialBytes total baixado no início (-1 = nada ainda).
 */
class StallPolicy(
    nowMs: Long,
    initialBytes: Long,
    val maxRestarts: Int = 2,
    private val healthyResetMs: Long = 60_000L
) {
    var lastBytes: Long = initialBytes
        private set
    var restarts: Int = 0
        private set

    private var lastProgressAt = nowMs
    private var healthySince = nowMs
    private var networkRefreshed = false

    /** Parado há quanto tempo (ms). */
    fun stalledForMs(nowMs: Long): Long = nowMs - lastProgressAt

    /**
     * @param waiting o player está esperando bytes (carregando ou baixando o início).
     * @param storageBlocked espaço abaixo do piso de download.
     * @param refreshAfterMs parado há esse tempo → reconectar (null = não reconecta antes do reinício;
     *   no início do vídeo o 1º contato com o servidor do canal pode demorar e reconectar atrapalha).
     */
    fun tick(
        nowMs: Long,
        waiting: Boolean,
        downloadedBytes: Long,
        networkReady: Boolean,
        storageBlocked: Boolean,
        restartAfterMs: Long,
        failAfterMs: Long,
        refreshAfterMs: Long?
    ): StallAction {
        if (!waiting) {
            lastProgressAt = nowMs
            lastBytes = downloadedBytes
            networkRefreshed = false
            if (restarts > 0 && nowMs - healthySince >= healthyResetMs) restarts = 0
            return StallAction.None
        }
        healthySince = nowMs
        if (downloadedBytes != lastBytes || !networkReady) {
            lastBytes = downloadedBytes
            lastProgressAt = nowMs
            networkRefreshed = false
        }
        val stalledFor = nowMs - lastProgressAt
        // Checa espaço junto com a 1ª reação (reconectar ou reiniciar), antes de culpar a rede.
        val storageCheckAfter = minOf(refreshAfterMs ?: restartAfterMs, restartAfterMs)
        if (storageBlocked && stalledFor >= storageCheckAfter) {
            return if (stalledFor >= failAfterMs) StallAction.FailLowStorage else StallAction.None
        }
        if (refreshAfterMs != null && stalledFor >= refreshAfterMs && !networkRefreshed) {
            networkRefreshed = true
            return StallAction.RefreshNetwork
        }
        if (stalledFor >= restartAfterMs && restarts < maxRestarts) {
            restarts++
            lastProgressAt = nowMs
            networkRefreshed = false
            return StallAction.Restart(restarts)
        }
        if (stalledFor >= failAfterMs && restarts >= maxRestarts) {
            return StallAction.FailStalled(lastBytes)
        }
        return StallAction.None
    }
}
