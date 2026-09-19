package com.ntv2.app.core.player.config

data class PlaybackTuning(
    val aheadWindowBytes: Long = 24L * 1024L * 1024L,
    val ioStallTimeoutMs: Long = 12_000L,
    // Janela deslizante no disco: o que fica guardado de um vídeo durante a reprodução.
    val diskHeadPinBytes: Long = 16L * 1024L * 1024L,
    val diskTailPinBytes: Long = 32L * 1024L * 1024L,
    val diskKeepBehindBytes: Long = 160L * 1024L * 1024L,
    val diskMinEvictBytes: Long = 32L * 1024L * 1024L
)
