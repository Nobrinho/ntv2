package com.ntv2.app.core.player.session

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ntv2.app.core.player.PlaybackCoordinator
import com.ntv2.app.core.player.PlaybackMedia
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.PlaybackState
import com.ntv2.app.core.player.cache.PlaybackCacheManager
import com.ntv2.app.core.player.download.ProgressiveDownloadPlanner
import com.ntv2.app.core.player.io.GrowingFileDataSourceFactory
import com.ntv2.app.core.player.telegram.TelegramPlaybackDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DefaultPlaybackCoordinator(
    private val playbackDataSource: TelegramPlaybackDataSource,
    private val resourceManager: PlaybackResourceManager,
    private val cacheManager: PlaybackCacheManager,
    private val planner: ProgressiveDownloadPlanner,
    private val dataSourceFactory: GrowingFileDataSourceFactory
) : PlaybackCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotState = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = snapshotState

    override val player: Player?
        get() = exoPlayer

    private var exoPlayer: ExoPlayer? = null
    private var currentMedia: PlaybackMedia? = null
    private var observeJob: Job? = null
    private var downloadJob: Job? = null
    private var wasPlayingBeforeStop: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val mapped = when (playbackState) {
                Player.STATE_IDLE -> PlaybackState.Idle
                Player.STATE_BUFFERING -> PlaybackState.Buffering
                Player.STATE_READY -> if (exoPlayer?.isPlaying == true) PlaybackState.Ready else PlaybackState.Paused
                Player.STATE_ENDED -> PlaybackState.Ended
                else -> PlaybackState.Idle
            }
            snapshotState.update {
                it.copy(
                    state = mapped,
                    isPlaying = exoPlayer?.isPlaying == true,
                    currentPositionMs = exoPlayer?.currentPosition ?: it.currentPositionMs,
                    bufferedPositionMs = exoPlayer?.bufferedPosition ?: it.bufferedPositionMs
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            snapshotState.update {
                it.copy(
                    state = PlaybackState.Error(
                        message = error.localizedMessage ?: "Falha de reprodução",
                        recoverable = true
                    ),
                    isPlaying = false
                )
            }
        }
    }

    override suspend fun prepare(media: PlaybackMedia) {
        stopInternal(closeSession = true)
        cacheManager.trimIfNeeded()

        snapshotState.update {
            it.copy(
                state = PlaybackState.Preparing,
                isPlaying = false,
                activeMediaId = media.mediaId,
                currentPositionMs = media.startPositionMs
            )
        }

        currentMedia = media
        val handle = playbackDataSource.open(media.fileId)
        val activeFile = File(handle.localPath)
        cacheManager.markActivePlaybackFile(activeFile)
        cacheManager.touch(activeFile)
        snapshotState.update {
            it.copy(
                downloadedBytes = handle.downloadedBytes,
                expectedBytes = handle.expectedBytes
            )
        }

        if (!handle.isDownloadComplete) {
            // Preload mínimo para reduzir tempo de start sem download integral.
            playbackDataSource.requestChunk(
                fileId = media.fileId,
                offsetBytes = 0L,
                lengthBytes = planner.initialChunk(),
                priority = 2
            )
        }

        val playerInstance = resourceManager.acquire()
        exoPlayer = playerInstance
        playerInstance.clearMediaItems()
        playerInstance.removeListener(playerListener)
        playerInstance.addListener(playerListener)

        val mediaItem = MediaItem.Builder()
            .setMediaId(media.mediaId)
            .setUri(Uri.parse(media.sourceUri))
            .build()

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        playerInstance.setMediaSource(mediaSource)
        playerInstance.prepare()
        playerInstance.seekTo(media.startPositionMs)
        playerInstance.playWhenReady = true

        observeFileState(media)
        if (!handle.isDownloadComplete) {
            startProgressiveLoop(media)
        }
    }

    override fun play() {
        exoPlayer?.playWhenReady = true
        snapshotState.update { it.copy(state = PlaybackState.Ready, isPlaying = true) }
    }

    override fun pause() {
        exoPlayer?.playWhenReady = false
        snapshotState.update { it.copy(state = PlaybackState.Paused, isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        val media = currentMedia ?: return
        val playerInstance = exoPlayer ?: return

        playerInstance.seekTo(positionMs)
        snapshotState.update { it.copy(state = PlaybackState.Buffering) }

        val expectedBytes = snapshot.value.expectedBytes ?: return
        val targetEnd = planner.computeTargetEndBytes(
            currentPositionMs = positionMs,
            durationMs = media.durationMs,
            expectedBytes = expectedBytes
        )
        val downloaded = playbackDataSource.downloadedBytes(media.fileId)
        if (downloaded < targetEnd) {
            scope.launch {
                playbackDataSource.requestChunk(
                    fileId = media.fileId,
                    offsetBytes = downloaded,
                    lengthBytes = targetEnd - downloaded,
                    priority = 3
                )
            }
        }
    }

    override fun retry() {
        val media = currentMedia ?: return
        val position = exoPlayer?.currentPosition ?: media.startPositionMs
        scope.launch {
            prepare(media.copy(startPositionMs = position))
        }
    }

    override fun stop() {
        stopInternal(closeSession = false)
    }

    override fun onAppStop() {
        wasPlayingBeforeStop = exoPlayer?.isPlaying == true
        pause()
    }

    override fun onAppResume() {
        if (wasPlayingBeforeStop) {
            play()
        }
    }

    override fun release() {
        stopInternal(closeSession = true)
        exoPlayer?.removeListener(playerListener)
        exoPlayer = null
        resourceManager.release()
        snapshotState.value = PlaybackSnapshot()
    }

    private fun observeFileState(media: PlaybackMedia) {
        observeJob?.cancel()
        observeJob = scope.launch {
            playbackDataSource.observe(media.fileId).collect { fileState ->
                snapshotState.update {
                    it.copy(
                        downloadedBytes = fileState.downloadedBytes,
                        expectedBytes = fileState.expectedBytes
                    )
                }
                cacheManager.touch(File(fileState.localPath))
            }
        }
    }

    private fun startProgressiveLoop(media: PlaybackMedia) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            while (true) {
                val expected = playbackDataSource.expectedBytes(media.fileId) ?: 0L
                if (expected <= 0L) {
                    kotlinx.coroutines.delay(planner.checkIntervalMs())
                    continue
                }

                val position = withContext(Dispatchers.Main.immediate) {
                    exoPlayer?.currentPosition ?: 0L
                }
                val targetEnd = planner.computeTargetEndBytes(
                    currentPositionMs = position,
                    durationMs = media.durationMs,
                    expectedBytes = expected
                )
                val downloaded = playbackDataSource.downloadedBytes(media.fileId)
                if (downloaded < targetEnd) {
                    val missing = targetEnd - downloaded
                    playbackDataSource.requestChunk(
                        fileId = media.fileId,
                        offsetBytes = downloaded,
                        lengthBytes = minOf(planner.chunkSize(), missing),
                        priority = 1
                    )
                }

                cacheManager.trimIfNeeded()
                kotlinx.coroutines.delay(planner.checkIntervalMs())
            }
        }
    }

    private fun stopInternal(closeSession: Boolean) {
        observeJob?.cancel()
        observeJob = null
        downloadJob?.cancel()
        downloadJob = null

        exoPlayer?.playWhenReady = false
        exoPlayer?.stop()

        val media = currentMedia
        if (closeSession && media != null) {
            scope.launch {
                playbackDataSource.close(media.fileId)
            }
            currentMedia = null
        }
        cacheManager.markActivePlaybackFile(null)

        snapshotState.update {
            it.copy(
                state = PlaybackState.Idle,
                isPlaying = false
            )
        }
    }
}
