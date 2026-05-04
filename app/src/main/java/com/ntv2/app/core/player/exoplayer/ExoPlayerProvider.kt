package com.ntv2.app.core.player.exoplayer

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

interface ExoPlayerProvider {
    fun create(): ExoPlayer
}

class DefaultExoPlayerProvider(
    private val context: Context
) : ExoPlayerProvider {

    @OptIn(UnstableApi::class)
    override fun create(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                7_000,
                30_000,
                1_200,
                2_200
            )
            .setTargetBufferBytes(8 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .build()
    }
}
