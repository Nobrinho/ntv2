package com.ntv2.app.core.player.download

import com.ntv2.app.core.player.config.PlaybackTuning

class ProgressiveDownloadPlanner(
    private val tuning: PlaybackTuning
) {
    fun initialChunk(): Long = tuning.initialPreloadBytes

    fun computeTargetEndBytes(
        currentPositionMs: Long,
        durationMs: Long,
        expectedBytes: Long
    ): Long {
        if (durationMs <= 0L || expectedBytes <= 0L) {
            return tuning.initialPreloadBytes
        }
        val ratio = currentPositionMs.toDouble() / durationMs.toDouble()
        val currentByte = (expectedBytes * ratio).toLong().coerceIn(0L, expectedBytes)
        return (currentByte + tuning.aheadWindowBytes).coerceAtMost(expectedBytes)
    }

    fun chunkSize(): Long = tuning.downloadChunkBytes

    fun checkIntervalMs(): Long = tuning.downloadCheckIntervalMs
}
