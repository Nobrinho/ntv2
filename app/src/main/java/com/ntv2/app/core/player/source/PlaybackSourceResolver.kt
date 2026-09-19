package com.ntv2.app.core.player.source

import com.ntv2.app.core.player.telegram.TelegramPlaybackDataSource
import java.io.File

data class PlaybackSourceRequest(
    val mediaId: String,
    val fileId: Int
)

sealed interface MediaAvailability {
    data object MissingRequestData : MediaAvailability
    data object TdlibFileUnavailable : MediaAvailability
    data object LocalFileMissing : MediaAvailability
    data class Downloading(
        val localPath: String,
        val downloadedBytes: Long,
        val expectedBytes: Long,
        val minBytesForPlayback: Long
    ) : MediaAvailability
    data class Ready(
        val localPath: String,
        val downloadedBytes: Long,
        val expectedBytes: Long,
        val isDownloadComplete: Boolean
    ) : MediaAvailability
}

sealed interface PlaybackSource {
    data class TelegramFile(
        val mediaId: String,
        val fileId: Int,
        val localPath: String,
        val playbackUri: String,
        val downloadedBytes: Long,
        val expectedBytes: Long,
        val isDownloadComplete: Boolean
    ) : PlaybackSource
}

sealed interface PlaybackSourceResolution {
    data class Available(
        val source: PlaybackSource,
        val availability: MediaAvailability.Ready
    ) : PlaybackSourceResolution

    data class Missing(
        val availability: MediaAvailability,
        /** Detalhe técnico (ex.: erro do TDLib) para diagnóstico na tela de erro. */
        val detail: String? = null
    ) : PlaybackSourceResolution
}

interface PlaybackSourceResolver {
    suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution
}

class DefaultPlaybackSourceResolver(
    private val telegramPlaybackDataSource: TelegramPlaybackDataSource
) : PlaybackSourceResolver {

    private val minBytesForPlayback = 4L * 1024L * 1024L

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceResolution {
        if (request.mediaId.isBlank() || request.fileId <= 0) {
            return PlaybackSourceResolution.Missing(MediaAvailability.MissingRequestData)
        }

        val handle = telegramPlaybackDataSource.inspectFile(request.fileId)
            ?: return PlaybackSourceResolution.Missing(
                MediaAvailability.TdlibFileUnavailable,
                detail = telegramPlaybackDataSource.lastOpenError(request.fileId)
            )

        if (handle.localPath.isBlank()) {
            return PlaybackSourceResolution.Missing(MediaAvailability.TdlibFileUnavailable)
        }

        val localFile = File(handle.localPath)
        if (!localFile.exists()) {
            return PlaybackSourceResolution.Missing(MediaAvailability.LocalFileMissing)
        }

        val currentBytes = maxOf(handle.downloadedBytes, localFile.length())
        if (!handle.isDownloadComplete && currentBytes < minBytesForPlayback) {
            val missingBootstrap = (minBytesForPlayback - currentBytes).coerceAtLeast(0L)
            if (missingBootstrap > 0L) {
                telegramPlaybackDataSource.requestChunk(
                    fileId = request.fileId,
                    offsetBytes = currentBytes,
                    lengthBytes = missingBootstrap,
                    priority = 32
                )
            }
            return PlaybackSourceResolution.Missing(
                MediaAvailability.Downloading(
                    localPath = handle.localPath,
                    downloadedBytes = currentBytes,
                    expectedBytes = handle.expectedBytes,
                    minBytesForPlayback = minBytesForPlayback
                )
            )
        }

        val ready = MediaAvailability.Ready(
            localPath = handle.localPath,
            downloadedBytes = handle.downloadedBytes,
            expectedBytes = handle.expectedBytes,
            isDownloadComplete = handle.isDownloadComplete
        )

        return PlaybackSourceResolution.Available(
            source = PlaybackSource.TelegramFile(
                mediaId = request.mediaId,
                fileId = request.fileId,
                localPath = handle.localPath,
                playbackUri = "tgfile://video/${request.fileId}",
                downloadedBytes = currentBytes,
                expectedBytes = handle.expectedBytes,
                isDownloadComplete = handle.isDownloadComplete
            ),
            availability = ready
        )
    }
}
