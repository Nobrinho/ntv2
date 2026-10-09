@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.player.exoplayer

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.media3.common.C
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
    private val context: Context,
    /** Teto do buffer em RAM (ver StreamProfiles.ramBufferBytes). */
    private val ramBufferBytes: Int = 64 * 1024 * 1024,
    /** Hardware ou software para o vídeo (troca para software se o hardware falhar no vídeo). */
    private val videoDecoderPolicy: VideoDecoderPolicy = VideoDecoderPolicy()
) : ExoPlayerProvider {

    override fun create(): ExoPlayer {
        // Buffer em RAM limitado em BYTES (vale até 4K remux, ~10 MB/s): com prioridade ao tempo,
        // 45 s de um 4K passavam de 400 MB e estouravam o heap do Fire TV. O colchão contra rede
        // instável fica no disco (janela à frente do StreamProfile), não aqui. Após um rebuffer
        // espera mais (6 s) para não entrar em ciclo tocar/travar com a rede no limite.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                30_000,
                2_500,
                6_000
            )
            .setTargetBufferBytes(ramBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        // Habilita os decoders de extensão (FfmpegAudioRenderer do módulo :ffmpeg-decoder),
        // carregados por reflexão pelo DefaultRenderersFactory. Assim faixas de áudio que o
        // hardware não decodifica (AC3/EAC3/DTS/TrueHD) tocam por software.
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(videoDecoderPolicy.codecSelector)
            // Se o 1º decodificador não inicializar, tenta o próximo da lista em vez de falhar.
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            // Mantém Wi‑Fi (WifiLock) e CPU acordados enquanto toca. Sem isso o Fire OS põe o Wi‑Fi
            // em economia no meio do filme e os sockets do TDLib morrem em silêncio.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Legenda: só português entra sozinha. Sem isso valia a faixa marcada como "padrão" no
        // arquivo (num MKV do Vingadores era a turca). Legenda "forçada" no idioma do áudio segue valendo.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguages("pt-BR", "pt", "pob")
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        // Engasgos (nível W/I, tag NtvPlayer) em todos os builds. No debug também o EventLogger
        // completo — mas o nível D dele não aparece no logcat do Fire OS, por isso o StutterLogger.
        player.addAnalyticsListener(StutterLogger)
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            player.addAnalyticsListener(EventLogger("NtvPlayer"))
        }

        return player
    }
}

/**
 * Registra os engasgos reais (tag NtvPlayer): quadros descartados, áudio sem dados, buffering no meio
 * da reprodução e qual decodificador foi escolhido. Permite correlacionar um engasgo com outros
 * eventos do log (ex.: liberação de disco NtvDiskWindow).
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

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        Log.i(TAG, "decodificador de vídeo: $decoderName (${initializationDurationMs}ms)")
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        if (state == Player.STATE_BUFFERING && eventTime.currentPlaybackPositionMs > 0L) {
            Log.w(TAG, "carregando no meio da reprodução (pos ${eventTime.currentPlaybackPositionMs}ms)")
        }
    }
}
