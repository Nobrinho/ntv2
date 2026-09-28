package com.ntv2.app.core.player.config

/**
 * Dimensionamento por vídeo, a partir do bitrate médio (tamanho ÷ duração). Lógica pura (testável
 * na JVM). Cobre de SD até 4K remux (~80 Mbps ≈ 10 MB/s).
 *
 * Divisão de papéis:
 *  - RAM (buffer do ExoPlayer, [PlaybackTuning.ramBufferBytes]) é pequena e limitada em BYTES — num
 *    4K 45 s de buffer não cabem no heap do Fire TV;
 *  - o colchão contra oscilação de rede fica no DISCO: [aheadWindowBytes] à frente da leitura;
 *  - [keepBehindBytes] precisa cobrir o que está no buffer da RAM (a leitura do disco está à frente
 *    da posição tocada) + uma margem para voltar alguns segundos. Antes eram 160 MB fixos: num
 *    remux de 40 Mbps o buffer sozinho passava disso e voltar 10 s caía num trecho já apagado.
 */
data class StreamProfile(
    val bytesPerSecond: Long,
    val aheadWindowBytes: Long,
    val keepBehindBytes: Long,
    /** false quando há espaço para o filme inteiro: não libera disco (sem punch hole nem rebaixar). */
    val diskWindowEnabled: Boolean
)

object StreamProfiles {
    private const val MB = 1024L * 1024L

    /** Sem duração conhecida: supõe um 1080p "pesado" (~40 Mbps). */
    const val FALLBACK_BYTES_PER_SECOND = 5L * MB
    /** Teto realista do bitrate médio (4K remux ~80 Mbps). */
    const val MAX_BYTES_PER_SECOND = 10L * MB

    /** Segundos de vídeo mantidos baixados à frente, no disco. */
    const val AHEAD_SECONDS = 60L
    const val MIN_AHEAD_BYTES = 48L * MB
    const val MAX_AHEAD_BYTES = 512L * MB

    /** Segundos de vídeo guardados atrás da posição tocada (voltar sem baixar de novo). */
    const val BEHIND_SECONDS = 30L
    const val MIN_KEEP_BEHIND_BYTES = 160L * MB
    const val MAX_KEEP_BEHIND_BYTES = 640L * MB

    /** Folga exigida além do tamanho do filme para dispensar a janela de disco. */
    const val FULL_FILE_HEADROOM_BYTES = 256L * MB

    fun estimateBytesPerSecond(expectedBytes: Long, durationMs: Long): Long {
        if (expectedBytes <= 0L || durationMs < 60_000L) return FALLBACK_BYTES_PER_SECOND
        return (expectedBytes * 1000L / durationMs).coerceIn(MB / 8, MAX_BYTES_PER_SECOND)
    }

    /**
     * @param freeBytes espaço livre na partição do TDLib; null = desconhecido (mantém a janela).
     * @param downloadFloor livre mínimo que o download nunca consome (ver StorageBudget).
     */
    fun forMedia(
        expectedBytes: Long,
        durationMs: Long,
        ramBufferBytes: Long,
        freeBytes: Long?,
        downloadFloor: Long
    ): StreamProfile {
        val bps = estimateBytesPerSecond(expectedBytes, durationMs)
        val ahead = (bps * AHEAD_SECONDS).coerceIn(MIN_AHEAD_BYTES, MAX_AHEAD_BYTES)
        val behind = (ramBufferBytes + bps * BEHIND_SECONDS).coerceIn(MIN_KEEP_BEHIND_BYTES, MAX_KEEP_BEHIND_BYTES)
        val fitsWhole = freeBytes != null && expectedBytes > 0L &&
            freeBytes - downloadFloor >= expectedBytes + FULL_FILE_HEADROOM_BYTES
        return StreamProfile(
            bytesPerSecond = bps,
            aheadWindowBytes = ahead,
            keepBehindBytes = behind,
            diskWindowEnabled = !fitsWhole
        )
    }

    /** Buffer do ExoPlayer em RAM: 1/4 do heap, entre 32 e 96 MB (Fire TV Stick: heap ~256 MB → 64 MB). */
    fun ramBufferBytes(maxHeapBytes: Long): Int =
        (maxHeapBytes / 4L).coerceIn(32L * MB, 96L * MB).toInt()
}
