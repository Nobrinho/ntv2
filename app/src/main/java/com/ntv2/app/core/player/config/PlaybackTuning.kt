package com.ntv2.app.core.player.config

data class PlaybackTuning(
    // Mantém uma margem maior para variações breves de rede e de I/O em TVs e celulares.
    val aheadWindowBytes: Long = 48L * 1024L * 1024L,
    val ioStallTimeoutMs: Long = 12_000L,
    // Janela deslizante no disco: o que fica guardado de um vídeo durante a reprodução.
    val diskHeadPinBytes: Long = 16L * 1024L * 1024L,
    val diskTailPinBytes: Long = 32L * 1024L * 1024L,
    val diskKeepBehindBytes: Long = 160L * 1024L * 1024L,
    val diskMinEvictBytes: Long = 32L * 1024L * 1024L
)
