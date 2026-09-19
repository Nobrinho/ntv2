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
        val recoverable: Boolean,
        /** Parou por falta de espaço no aparelho (a tela explica e sugere liberar espaço). */
        val lowStorage: Boolean = false
    ) : PlaybackState
}

/** Uma faixa de áudio ou legenda disponível na mídia (detectada pelo ExoPlayer ao abrir). */
data class MediaTrackOption(
    /** Identificador interno "grupo:faixa" usado para reaplicar a seleção. */
    val id: String,
    val label: String,
    val isSelected: Boolean
)

/** Faixas/detalhes da mídia atual, preenchidos após o ExoPlayer parsear o arquivo. */
data class MediaTracksInfo(
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val audios: List<MediaTrackOption> = emptyList(),
    val subtitles: List<MediaTrackOption> = emptyList()
) {
    /** true se há uma legenda ativa no momento. */
}

data class PlaybackSnapshot(
    val state: PlaybackState = PlaybackState.Idle,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val downloadedBytes: Long = 0L,
    val expectedBytes: Long? = null,
    val activeMediaId: String? = null,
    val tracks: MediaTracksInfo = MediaTracksInfo()
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

    /** Seleciona a faixa de áudio pelo id de [MediaTrackOption]. */
    fun selectAudioTrack(id: String)

    /** Seleciona a legenda pelo id de [MediaTrackOption]; null desliga a legenda. */
    fun selectTextTrack(id: String?)

    /** Cancela e remove o download de um arquivo, mesmo que a reprodução nunca tenha iniciado. */
    fun discardMedia(fileId: Int)

    /**
     * Destrava um download parado ANTES da reprodução começar: cancela no TDLib mantendo o parcial;
     * a próxima tentativa de preparo pede o arquivo de novo e retoma de onde parou.
     */
    suspend fun restartDownload(fileId: Int)
}
