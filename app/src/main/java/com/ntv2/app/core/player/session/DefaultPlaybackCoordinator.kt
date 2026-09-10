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
import com.ntv2.app.core.player.progress.PlaybackProgressStore
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
import java.io.File

private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

class DefaultPlaybackCoordinator(
    private val playbackDataSource: TelegramPlaybackDataSource,
    private val resourceManager: PlaybackResourceManager,
    private val cacheManager: PlaybackCacheManager,
    private val planner: ProgressiveDownloadPlanner,
    private val dataSourceFactory: GrowingFileDataSourceFactory,
    private val progressStore: PlaybackProgressStore
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
    private var progressJob: Job? = null
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
            if (playbackState == Player.STATE_ENDED) {
                // Assistido até o fim: limpa o progresso para não retomar no finzinho.
                currentMedia?.let { media -> scope.launch { progressStore.clear(media.mediaId) } }
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
        startProgressSaving(media)
        if (!handle.isDownloadComplete) {
            startProgressiveLoop(media)
        }
    }

    private fun startProgressSaving(media: PlaybackMedia) {
        progressJob?.cancel()
        // Roda na Main (ExoPlayer só pode ser lido na sua thread) e salva periodicamente,
        // para não perder a posição se o app for encerrado.
        progressJob = scope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(PROGRESS_SAVE_INTERVAL_MS)
                persistCurrentProgress(media)
            }
        }
    }

    private suspend fun persistCurrentProgress(media: PlaybackMedia) {
        val player = exoPlayer ?: return
        val positionMs = player.currentPosition
        val durationMs = player.duration.takeIf { it > 0L } ?: media.durationMs
        progressStore.onProgress(media.mediaId, positionMs, durationMs)
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
        val playerInstance = exoPlayer ?: return
        playerInstance.seekTo(positionMs)
        snapshotState.update { it.copy(state = PlaybackState.Buffering) }
        // O ExoPlayer reabre a fonte na nova posição e a GrowingFileDataSource solicita o range
        // necessário. Não emitimos DownloadFile aqui para não competir pelo offset único do TDLib.
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

    override fun discardMedia(fileId: Int) {
        if (fileId <= 0) return
        scope.launch { playbackDataSource.deleteFile(fileId) }
    }

    override fun release() {
        stopInternal(closeSession = true, deleteFile = true)
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
            // A GrowingFileDataSource é a ÚNICA a emitir DownloadFile (segue exatamente o que o
            // ExoPlayer lê). O TDLib com DownloadFile(offset, limit=0) já baixa adiante sozinho.
            // Não emitimos requisições concorrentes aqui: um DownloadFile com outro offset move o
            // offset único do TDLib e picota o download — era o que travava MKV (índice no fim),
            // pois o loop puxava o começo enquanto o player esperava o fim.
            while (true) {
                cacheManager.trimIfNeeded()
                kotlinx.coroutines.delay(planner.checkIntervalMs())
            }
        }
    }

    private fun stopInternal(closeSession: Boolean, deleteFile: Boolean = false) {
        observeJob?.cancel()
        observeJob = null
        downloadJob?.cancel()
        downloadJob = null
        progressJob?.cancel()
        progressJob = null

        val media = currentMedia
        // Captura a posição ANTES de parar o player (currentPosition zera após stop) e salva.
        if (media != null) {
            val positionMs = exoPlayer?.currentPosition ?: 0L
            val durationMs = exoPlayer?.duration?.takeIf { it > 0L } ?: media.durationMs
            scope.launch { progressStore.onProgress(media.mediaId, positionMs, durationMs) }
        }

        exoPlayer?.playWhenReady = false
        exoPlayer?.stop()

        if (closeSession && media != null) {
            val fileId = media.fileId
            scope.launch {
                // Ao sair da reprodução, remove o arquivo do TDLib para não acumular no
                // armazenamento (o Fire TV tem pouco espaço). Nas demais paradas, apenas fecha.
                if (deleteFile) {
                    playbackDataSource.deleteFile(fileId)
                } else {
                    playbackDataSource.close(fileId)
                }
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
