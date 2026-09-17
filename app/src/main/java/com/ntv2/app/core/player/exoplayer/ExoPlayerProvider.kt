package com.ntv2.app.core.player.exoplayer

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
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

        // Habilita os decoders de extensão (FfmpegAudioRenderer do módulo :ffmpeg-decoder),
        // carregados por reflexão pelo DefaultRenderersFactory. Assim faixas de áudio que o
        // hardware não decodifica (AC3/EAC3/DTS/TrueHD) tocam por software.
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .build()
    }
}
