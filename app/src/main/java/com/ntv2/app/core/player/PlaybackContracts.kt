package com.ntv2.app.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

data class PlaybackMedia(
    val mediaId: String,
    val fileId: Int,
    val title: String,
    val durationMs: Long,
    val sourceUri: String,
    val startPositionMs: Long = 0L
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Preparing : PlaybackState
    data object Buffering : PlaybackState
    data object Ready : PlaybackState
    data object Paused : PlaybackState
    data object Ended : PlaybackState
    data class Error(
        val message: String,
        val recoverable: Boolean
    ) : PlaybackState
}

data class PlaybackSnapshot(
    val state: PlaybackState = PlaybackState.Idle,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val downloadedBytes: Long = 0L,
    val expectedBytes: Long? = null,
    val activeMediaId: String? = null
)

interface PlaybackCoordinator {
    val player: Player?
    val snapshot: StateFlow<PlaybackSnapshot>

    suspend fun prepare(media: PlaybackMedia)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun retry()
    fun stop()
    fun onAppStop()
    fun onAppResume()
    fun release()

    /** Cancela e remove o download de um arquivo, mesmo que a reprodução nunca tenha iniciado. */
    fun discardMedia(fileId: Int)
}
