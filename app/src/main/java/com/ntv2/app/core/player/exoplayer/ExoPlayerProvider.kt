package com.ntv2.app.core.player.exoplayer

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger

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

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .build()

        // Em builds debug, registra no logcat (tag "NtvPlayer") estados, underruns de áudio,
        // frames descartados e erros de carga, para diagnosticar engasgos.
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) player.addAnalyticsListener(EventLogger("NtvPlayer"))

        return player
    }
}
