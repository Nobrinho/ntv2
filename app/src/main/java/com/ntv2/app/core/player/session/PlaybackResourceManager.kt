package com.ntv2.app.core.player.session

import androidx.media3.exoplayer.ExoPlayer
import com.ntv2.app.core.player.exoplayer.ExoPlayerProvider

class PlaybackResourceManager(
    private val exoPlayerProvider: ExoPlayerProvider
) {
    private var activePlayer: ExoPlayer? = null

    fun acquire(): ExoPlayer {
        val current = activePlayer
        if (current != null) {
            return current
        }
        return exoPlayerProvider.create().also { created ->
            activePlayer = created
        }
    }

    fun release() {
        activePlayer?.release()
        activePlayer = null
    }
}
