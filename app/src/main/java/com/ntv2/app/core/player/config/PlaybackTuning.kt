package com.ntv2.app.core.player.config

data class PlaybackTuning(
    // Janela à frente padrão (antes de conhecer o bitrate); por vídeo vem de [StreamProfile].
    val aheadWindowBytes: Long = 48L * 1024L * 1024L,
    // Maior que o watchdog da tela (que reconecta e reinicia antes): o DataSource só desiste se a
    // recuperação da tela não resolveu. Com os dois em 12 s competiam e o vídeo não voltava.
    val ioStallTimeoutMs: Long = 30_000L,
    // Buffer do ExoPlayer em RAM (limitado em bytes, ver StreamProfiles.ramBufferBytes).
    val ramBufferBytes: Int = StreamProfiles.ramBufferBytes(Runtime.getRuntime().maxMemory()),
    // Janela deslizante no disco: o que fica guardado de um vídeo durante a reprodução.
    val diskHeadPinBytes: Long = 16L * 1024L * 1024L,
    val diskTailPinBytes: Long = 32L * 1024L * 1024L,
    val diskKeepBehindBytes: Long = 160L * 1024L * 1024L,
    val diskMinEvictBytes: Long = 32L * 1024L * 1024L
)
