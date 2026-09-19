package com.ntv2.app.core.player.controller

import androidx.media3.common.Player
import com.ntv2.app.core.player.PlaybackCoordinator
import com.ntv2.app.core.player.PlaybackMedia
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.core.player.source.MediaAvailability
import com.ntv2.app.core.player.source.PlaybackSource
import com.ntv2.app.core.player.source.PlaybackSourceResolution
import com.ntv2.app.core.player.source.PlaybackSourceRequest
import com.ntv2.app.core.player.source.PlaybackSourceResolver
import com.ntv2.app.core.storage.PlaybackStorageGuard
import com.ntv2.app.core.storage.StorageBudget
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
        val availability: MediaAvailability,
        val detail: String? = null
    ) : PlaybackPrepareResult
    /** Espaço livre abaixo do mínimo para tocar, mesmo após a limpeza automática. */
    data class InsufficientStorage(
        val freeBytes: Long,
        val requiredBytes: Long
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
    fun selectAudioTrack(id: String)
    fun selectTextTrack(id: String?)
    fun discardMedia(fileId: Int)

    /** Download travado: cancela e pede de novo ao TDLib (retoma de onde parou). */
    suspend fun restartDownload(fileId: Int)
}

class DefaultPlaybackController(
    private val coordinator: PlaybackCoordinator,
    private val sourceResolver: PlaybackSourceResolver,
    private val progressStore: PlaybackProgressStore,
    private val storageGuard: PlaybackStorageGuard? = null
) : PlaybackController {

    override val player: Player? get() = coordinator.player

    @Volatile
    private var admittedFileId: Int = 0

    override val snapshot: StateFlow<PlaybackSnapshot> = coordinator.snapshot

    override suspend fun prepare(request: PlaybackPrepareRequest): PlaybackPrepareResult {
        // Checagem ANTES de abrir o arquivo (abrir já começa a baixar): sem espaço, limpa caches;
        // se ainda faltar, não inicia — em vez de lotar o aparelho no meio do filme.
        // Checa só na 1ª tentativa de cada arquivo: o prepare é repetido a cada 1s enquanto o
        // início baixa, e esse próprio download não deve reprovar o vídeo já admitido.
        if (request.fileId != admittedFileId) {
            storageGuard?.ensureSpaceForPlayback()?.let { storage ->
                if (!storage.canStartPlayback) {
                    return PlaybackPrepareResult.InsufficientStorage(
                        freeBytes = storage.freeBytes,
                        requiredBytes = StorageBudget.startFloor(storage.totalBytes)
                    )
                }
            }
            admittedFileId = request.fileId
        }
        val resolution = sourceResolver.resolve(
            PlaybackSourceRequest(
                mediaId = request.mediaId,
                fileId = request.fileId
            )
        )

        val source = when (resolution) {
            is PlaybackSourceResolution.Available -> resolution.source
            is PlaybackSourceResolution.Missing -> {
                return PlaybackPrepareResult.MissingSource(resolution.availability, resolution.detail)
            }
        }

        val durationMs = request.durationSeconds * 1_000L
        val media = when (source) {
            is PlaybackSource.TelegramFile -> PlaybackMedia(
                mediaId = source.mediaId,
                fileId = source.fileId,
                title = request.title,
                durationMs = durationMs,
                sourceUri = source.playbackUri,
                // Retoma de onde parou (0 se não houver progresso útil salvo).
                startPositionMs = progressStore.resumePositionMs(source.mediaId, durationMs)
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

    override fun release() {
        admittedFileId = 0
        coordinator.release()
    }

    override fun selectAudioTrack(id: String) = coordinator.selectAudioTrack(id)

    override fun selectTextTrack(id: String?) = coordinator.selectTextTrack(id)

    override fun discardMedia(fileId: Int) = coordinator.discardMedia(fileId)

    override suspend fun restartDownload(fileId: Int) = coordinator.restartDownload(fileId)
}
