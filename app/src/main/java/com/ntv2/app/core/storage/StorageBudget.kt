package com.ntv2.app.core.storage

import kotlin.math.min

/**
 * Limites de disco derivados do tamanho da partição. O Android (e o Fire OS) avisa "armazenamento
 * baixo" quando o livre cai abaixo de min(10% do total, 500 MB). O app precisa parar de baixar
 * ANTES disso — o guarda antigo (300 MB fixos) deixava o disco passar do limiar do sistema e o
 * Fire OS mostrava o aviso sugerindo desinstalar apps.
 */
object StorageBudget {
    private const val MB = 1024L * 1024L
    private const val SYSTEM_LOW_PERCENT = 10L
    private const val SYSTEM_LOW_MAX_BYTES = 500L * MB

    /** Folga acima do limiar do sistema que nunca consumimos com download de vídeo. */
    const val SAFETY_MARGIN_BYTES = 200L * MB

    /** Espaço extra exigido para INICIAR uma reprodução (janela à frente + trecho inicial + folga). */
    const val START_HEADROOM_BYTES = 150L * MB

    /** Teto do cache de pôsteres/miniaturas/avatares do TDLib. */
    const val IMAGE_CACHE_MAX_BYTES = 150L * MB

    /** Teto do cache de imagens em situação de emergência (disco baixo). */
    const val IMAGE_CACHE_EMERGENCY_BYTES = 40L * MB

    /** Limiar em que o sistema passa a avisar armazenamento baixo. */
    fun systemLowThreshold(totalBytes: Long): Long =
        min(totalBytes.coerceAtLeast(0L) * SYSTEM_LOW_PERCENT / 100L, SYSTEM_LOW_MAX_BYTES)

    /** Abaixo desse livre o player para de baixar adiante (degrada em vez de lotar o aparelho). */
    fun downloadFloor(totalBytes: Long): Long = systemLowThreshold(totalBytes) + SAFETY_MARGIN_BYTES

    /** Livre mínimo para começar a tocar um vídeo. */
    fun startFloor(totalBytes: Long): Long = downloadFloor(totalBytes) + START_HEADROOM_BYTES

    fun canDownload(freeBytes: Long, totalBytes: Long): Boolean = freeBytes >= downloadFloor(totalBytes)

    fun canStartPlayback(freeBytes: Long, totalBytes: Long): Boolean = freeBytes >= startFloor(totalBytes)
}
