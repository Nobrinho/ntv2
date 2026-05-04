package com.ntv2.app.core.player.config

data class PlaybackTuning(
    val initialPreloadBytes: Long = 6L * 1024L * 1024L,
    val downloadChunkBytes: Long = 4L * 1024L * 1024L,
    val aheadWindowBytes: Long = 24L * 1024L * 1024L,
    val downloadCheckIntervalMs: Long = 1_000L,
    val ioPollIntervalMs: Long = 200L,
    val ioStallTimeoutMs: Long = 12_000L,
    val cacheMaxBytes: Long = 512L * 1024L * 1024L,
    val cacheTrimTargetBytes: Long = 420L * 1024L * 1024L,
    val maxCachedFiles: Int = 6
)
