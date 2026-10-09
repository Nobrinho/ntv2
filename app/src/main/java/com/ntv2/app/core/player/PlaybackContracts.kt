package com.ntv2.app.core.player

import androidx.media3.common.Player
import com.ntv2.app.core.multipart.PartsSnapshot
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
        val lowStorage: Boolean = false,
        /** Filme dividido: a parte que faltou/falhou (a tela diz qual); null = outro erro. */
        val partUnavailable: com.ntv2.app.core.multipart.PartUnavailableException? = null,
        /** O decodificador do aparelho não reproduz o vídeo (ex.: "VP9 3840×1606"); null = outro erro. */
        val unsupportedVideo: String? = null
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
    val videoFrameRate: Float = 0f,
    val videoMimeType: String? = null,
    /** O arquivo tem vídeo, mas nenhuma trilha dele pode ser decodificada neste aparelho (toca só o áudio). */
    val videoUnsupported: Boolean = false,
    /** Profundidade de cor (bits) e HDR ("HDR10"/"HLG"; null = SDR ou desconhecido), vistos pelo ExoPlayer. */
    val videoBitDepth: Int = 0,
    val videoHdr: String? = null,
    /** Dolby Vision: perfil cuja camada base HEVC está tocando no lugar da trilha DV (ver DolbyVisionFallback). */
    val dolbyVisionBaseLayerProfile: Int? = null,
    /** Áudio em uso (formato e canais); o nome da faixa está em [audios]. */
    val audioMimeType: String? = null,
    val audioChannels: Int = 0,
    val audios: List<MediaTrackOption> = emptyList(),
    val subtitles: List<MediaTrackOption> = emptyList()
) {
    /** Nome curto do formato do vídeo para mensagens ("Dolby Vision 4K"), ou null se desconhecido. */
    val videoFormatLabel: String?
        get() {
            val codec = when (videoMimeType) {
                "video/dolby-vision" -> "Dolby Vision"
                "video/hevc" -> "HEVC"
                "video/avc" -> "H.264"
                "video/av01" -> "AV1"
                "video/x-vnd.on2.vp9" -> "VP9"
                "video/mpeg2" -> "MPEG-2"
                null -> null
                else -> videoMimeType.substringAfter('/')
            }
            val resolution = when {
                videoHeight >= 2000 -> "4K"
                videoHeight >= 1000 -> "1080p"
                videoHeight > 0 -> "${videoHeight}p"
                else -> null
            }
            return listOfNotNull(codec, resolution).joinToString(" ").ifEmpty { null }
        }

    companion object {
        /** [videoTrackSupport]: uma entrada por trilha de vídeo (aceita, mesmo que acima da capacidade). */
        fun isVideoUnsupported(videoTrackSupport: List<Boolean>): Boolean =
            videoTrackSupport.isNotEmpty() && videoTrackSupport.none { it }
    }
}

data class PlaybackSnapshot(
    val state: PlaybackState = PlaybackState.Idle,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val downloadedBytes: Long = 0L,
    val expectedBytes: Long? = null,
    val activeMediaId: String? = null,
    val tracks: MediaTracksInfo = MediaTracksInfo(),
    /** Filme dividido em partes: onde se está e o que já foi baixado do filme todo (null = arquivo único). */
    val parts: PartsSnapshot? = null,
    /** Progresso de download que NUNCA cai (descartar uma parte não vira "velocidade negativa"): mede velocidade. */
    val progressBytes: Long = 0L,
    /** Progresso da parte em leitura: detecta download parado sem a pré-carga da próxima parte mascarar. */
    val stallBytes: Long = 0L
)

interface PlaybackCoordinator {
    val player: Player?
    val snapshot: StateFlow<PlaybackSnapshot>

    suspend fun prepare(media: PlaybackMedia)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun retry()
    /** Prepara de novo o vídeo atual a partir de [positionMs] (ex.: volta do Chromecast). */
    fun retryAt(positionMs: Long)
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
