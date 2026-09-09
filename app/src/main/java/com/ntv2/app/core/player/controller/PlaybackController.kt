package com.ntv2.app.core.player.controller

import androidx.media3.common.Player
import com.ntv2.app.core.player.PlaybackCoordinator
import com.ntv2.app.core.player.PlaybackMedia
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.source.MediaAvailability
import com.ntv2.app.core.player.source.PlaybackSource
import com.ntv2.app.core.player.source.PlaybackSourceResolution
import com.ntv2.app.core.player.source.PlaybackSourceRequest
import com.ntv2.app.core.player.source.PlaybackSourceResolver
import kotlinx.coroutines.flow.StateFlow

data class PlaybackPrepareRequest(
    val mediaId: String,
    val fileId: Int,
    val title: String,
    val durationSeconds: Int
)

sealed interface PlaybackPrepareResult {
    data object Started : PlaybackPrepareResult
    data class MissingSource(
        val availability: MediaAvailability
    ) : PlaybackPrepareResult
    data class Failed(
        val stage: PlaybackPrepareErrorStage,
        val message: String
    ) : PlaybackPrepareResult
}

enum class PlaybackPrepareErrorStage {
    PrepareCoordinator
}

interface PlaybackController {
    val player: Player?
    val snapshot: StateFlow<PlaybackSnapshot>
    suspend fun prepare(request: PlaybackPrepareRequest): PlaybackPrepareResult
    fun play()
    fun pause()
    fun retry()
    fun seekTo(positionMs: Long)
    fun onAppStop()
    fun onAppResume()
    fun release()
}

class DefaultPlaybackController(
    private val coordinator: PlaybackCoordinator,
    private val sourceResolver: PlaybackSourceResolver
) : PlaybackController {

    override val player: Player? get() = coordinator.player

    override val snapshot: StateFlow<PlaybackSnapshot> = coordinator.snapshot

    override suspend fun prepare(request: PlaybackPrepareRequest): PlaybackPrepareResult {
        val resolution = sourceResolver.resolve(
            PlaybackSourceRequest(
                mediaId = request.mediaId,
                fileId = request.fileId
            )
        )

        val source = when (resolution) {
            is PlaybackSourceResolution.Available -> resolution.source
            is PlaybackSourceResolution.Missing -> {
                return PlaybackPrepareResult.MissingSource(resolution.availability)
            }
        }

        val media = when (source) {
            is PlaybackSource.TelegramFile -> PlaybackMedia(
                mediaId = source.mediaId,
                fileId = source.fileId,
                title = request.title,
                durationMs = request.durationSeconds * 1_000L,
                sourceUri = source.playbackUri
            )
        }

        return runCatching {
            coordinator.prepare(media)
            PlaybackPrepareResult.Started
        }.getOrElse { error ->
            PlaybackPrepareResult.Failed(
                stage = PlaybackPrepareErrorStage.PrepareCoordinator,
                message = error.message ?: "Falha ao inicializar reprodução"
            )
        }
    }

    override fun play() = coordinator.play()

    override fun pause() = coordinator.pause()

    override fun retry() = coordinator.retry()

    override fun seekTo(positionMs: Long) = coordinator.seekTo(positionMs)

    override fun onAppStop() = coordinator.onAppStop()

    override fun onAppResume() = coordinator.onAppResume()

    override fun release() = coordinator.release()
}
