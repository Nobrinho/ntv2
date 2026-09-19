@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.exoplayer

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.Player
import android.util.Log

interface ExoPlayerProvider {
    fun create(): ExoPlayer
}

class DefaultExoPlayerProvider(
    private val context: Context
) : ExoPlayerProvider {

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
        if (debuggable) {
            player.addAnalyticsListener(EventLogger("NtvPlayer"))
        } else {
            player.addAnalyticsListener(StutterLogger)
        }

        return player
    }
}

/**
 * Registra só os engasgos reais (tag NtvPlayer, nível W) na release, onde o EventLogger completo
 * fica desligado: quadros descartados, áudio sem dados e buffering no meio da reprodução. Permite
 * correlacionar um engasgo com outros eventos do log (ex.: liberação de disco NtvDiskWindow).
 */
private object StutterLogger : AnalyticsListener {
    private const val TAG = "NtvPlayer"

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        Log.w(TAG, "quadros descartados: $droppedFrames em ${elapsedMs}ms (pos ${eventTime.currentPlaybackPositionMs}ms)")
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long
    ) {
        Log.w(TAG, "áudio sem dados: ${elapsedSinceLastFeedMs}ms sem alimentar (pos ${eventTime.currentPlaybackPositionMs}ms)")
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        if (state == Player.STATE_BUFFERING && eventTime.currentPlaybackPositionMs > 0L) {
            Log.w(TAG, "carregando no meio da reprodução (pos ${eventTime.currentPlaybackPositionMs}ms)")
        }
    }
}
