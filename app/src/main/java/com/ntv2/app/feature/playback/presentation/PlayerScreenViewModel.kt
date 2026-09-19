package com.ntv2.app.feature.playback.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.PlaybackState
import com.ntv2.app.core.player.controller.PlaybackController
import com.ntv2.app.core.player.controller.PlaybackPrepareRequest
import com.ntv2.app.core.player.controller.PlaybackPrepareResult
import com.ntv2.app.core.player.source.MediaAvailability
import com.ntv2.app.feature.media.domain.MediaDetailsCache
import com.ntv2.app.feature.media.domain.MovieDetails
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerScreenUiState(
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val title: String = "",
    val channelName: String = "",
    val durationSeconds: Int = 0,
    val fileName: String? = null,
    val thumbnailPath: String? = null,
    val isPlaceholderMode: Boolean = true,
    val statusMessage: String = "reprodução ainda não inicializada",
    /** Detalhes ricos (pôster/sinopse/metadados) do filme, quando disponíveis. */
    val details: MovieDetails? = null,
    /** Progresso do download enquanto o vídeo não começa (null = não exibir). */
    val downloadProgress: DownloadProgress? = null,
    /** Aviso extra durante a espera (ex.: vídeo que precisa baixar mais antes de tocar). */
    val loadingHint: String? = null,
    /** Falha ao carregar: a tela mostra o motivo e os botões Tentar novamente / Voltar. */
    val loadError: PlayerLoadError? = null
)

data class DownloadProgress(
    val downloadedBytes: Long,
    val expectedBytes: Long,
    val bytesPerSecond: Long
)

data class PlayerLoadError(
    val title: String,
    val message: String,
    /** Detalhe técnico (ex.: erro do TDLib), exibido em letra menor. */
    val detail: String? = null
)

// Download sem nenhum byte novo por esse tempo = parado: reinicia no TDLib (até MAX_RESTARTS vezes).
private const val STALL_RESTART_MS = 12_000L
// Depois de esgotar as reinicializações, esse tempo parado vira erro.
private const val STALL_FAIL_MS = 20_000L
private const val MAX_RESTARTS = 2
// Tocando há esse tempo sem o 1º quadro, mas baixando: avisa que o vídeo precisa de mais dados.
private const val SLOW_START_HINT_MS = 20_000L

sealed interface PlayerScreenAction {
    data class Prepare(
        val mediaId: String,
        val fileId: Int,
        val title: String,
        val channelName: String,
        val durationSeconds: Int,
        val fileName: String?,
        val thumbnailPath: String?
    ) : PlayerScreenAction

    data object Play : PlayerScreenAction
    data object Pause : PlayerScreenAction
    data object Retry : PlayerScreenAction
    data class SeekBy(val deltaMs: Long) : PlayerScreenAction
    data class SeekTo(val positionMs: Long) : PlayerScreenAction
    data class SelectAudio(val id: String) : PlayerScreenAction
    /** id null desliga a legenda. */
    data class SelectSubtitle(val id: String?) : PlayerScreenAction
    data object OnAppStop : PlayerScreenAction
    data object OnAppResume : PlayerScreenAction
    data object Release : PlayerScreenAction
    /** "Tentar novamente" da tela de erro: retoma o download/preparo de onde parou. */
    data object RetryLoad : PlayerScreenAction
}

class PlayerScreenViewModel(
    private val playbackController: PlaybackController,
    private val mediaDetailsCache: MediaDetailsCache? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerScreenUiState())
    val uiState: StateFlow<PlayerScreenUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    val player: Player? get() = playbackController.player
    private var prepareJob: Job? = null
    private var watchdogJob: Job? = null
    private var lastRequest: PlaybackPrepareRequest? = null
    private var preparingFileId: Int = 0

    init {
        observeJob = viewModelScope.launch {
            playbackController.snapshot.collect { snapshot ->
                _uiState.update { it.copy(snapshot = snapshot) }
            }
        }
    }

    fun onAction(action: PlayerScreenAction) {
        when (action) {
            is PlayerScreenAction.Prepare -> {
                preparingFileId = action.fileId
                val details = mediaDetailsCache?.get(action.mediaId)
                _uiState.update {
                    it.copy(
                        title = details?.title ?: action.title,
                        channelName = action.channelName,
                        durationSeconds = action.durationSeconds,
                        fileName = action.fileName,
                        thumbnailPath = details?.posterPath ?: action.thumbnailPath,
                        statusMessage = "preparando reprodução…",
                        details = details
                    )
                }
                startPrepareLoop(
                    PlaybackPrepareRequest(
                        mediaId = action.mediaId,
                        fileId = action.fileId,
                        title = action.title,
                        durationSeconds = action.durationSeconds
                    )
                )
            }

            PlayerScreenAction.Play -> if (!uiState.value.isPlaceholderMode) playbackController.play()
            PlayerScreenAction.Pause -> if (!uiState.value.isPlaceholderMode) playbackController.pause()
            PlayerScreenAction.Retry -> if (!uiState.value.isPlaceholderMode) playbackController.retry()
            is PlayerScreenAction.SeekBy -> {
                if (uiState.value.isPlaceholderMode) return
                // Usa a posição REAL do player (o snapshot só atualiza em mudanças de estado).
                val current = playbackController.player?.currentPosition
                    ?: uiState.value.snapshot.currentPositionMs
                playbackController.seekTo((current + action.deltaMs).coerceAtLeast(0L))
            }
            is PlayerScreenAction.SeekTo -> {
                if (uiState.value.isPlaceholderMode) return
                playbackController.seekTo(action.positionMs.coerceAtLeast(0L))
            }

            is PlayerScreenAction.SelectAudio -> if (!uiState.value.isPlaceholderMode) {
                playbackController.selectAudioTrack(action.id)
            }
            is PlayerScreenAction.SelectSubtitle -> if (!uiState.value.isPlaceholderMode) {
                playbackController.selectTextTrack(action.id)
            }

            PlayerScreenAction.OnAppStop -> if (!uiState.value.isPlaceholderMode) playbackController.onAppStop()
            PlayerScreenAction.OnAppResume -> if (!uiState.value.isPlaceholderMode) playbackController.onAppResume()
            PlayerScreenAction.Release -> playbackController.release()
            PlayerScreenAction.RetryLoad -> {
                val request = lastRequest ?: return
                _uiState.update { it.copy(loadError = null, loadingHint = null) }
                if (uiState.value.isPlaceholderMode) {
                    startPrepareLoop(request)
                } else {
                    playbackController.retry()
                    startWatchdog()
                }
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        prepareJob?.cancel()
        watchdogJob?.cancel()
        playbackController.release()
        // Garante o cancelamento do download mesmo se a reprodução nunca iniciou (evita downloads
        // órfãos que acumulam e travam os próximos vídeos).
        playbackController.discardMedia(preparingFileId)
        super.onCleared()
    }

    /**
     * Prepara a reprodução; enquanto o arquivo ainda baixa os bytes iniciais, reexecuta a cada 1s.
     * Não há mais limite fixo de tempo: só desiste quando o download PARA de avançar. Download
     * parado é reiniciado no TDLib algumas vezes antes de virar erro (com o motivo real).
     */
    private fun startPrepareLoop(request: PlaybackPrepareRequest) {
        lastRequest = request
        prepareJob?.cancel()
        watchdogJob?.cancel()
        _uiState.update { it.copy(loadError = null, loadingHint = null, downloadProgress = null) }
        prepareJob = viewModelScope.launch {
            var lastBytes = -1L
            var lastProgressAt = now()
            var restarts = 0
            val speed = SpeedMeter()
            var detail: String? = null
            while (true) {
                when (val result = playbackController.prepare(request)) {
                    PlaybackPrepareResult.Started -> {
                        _uiState.update {
                            it.copy(isPlaceholderMode = false, statusMessage = "reproduzindo", downloadProgress = null)
                        }
                        startWatchdog()
                        return@launch
                    }

                    is PlaybackPrepareResult.MissingSource -> {
                        val availability = result.availability
                        result.detail?.let { detail = it }
                        val t = now()
                        when (availability) {
                            is MediaAvailability.Downloading -> {
                                if (availability.downloadedBytes > lastBytes) {
                                    lastBytes = availability.downloadedBytes
                                    lastProgressAt = t
                                }
                                val progress = DownloadProgress(
                                    downloadedBytes = availability.downloadedBytes,
                                    expectedBytes = availability.expectedBytes,
                                    bytesPerSecond = speed.sample(availability.downloadedBytes, t)
                                )
                                _uiState.update {
                                    it.copy(
                                        isPlaceholderMode = true,
                                        statusMessage = "baixando o início do vídeo…",
                                        downloadProgress = progress
                                    )
                                }
                            }
                            MediaAvailability.TdlibFileUnavailable,
                            MediaAvailability.LocalFileMissing -> _uiState.update {
                                it.copy(isPlaceholderMode = true, statusMessage = messageForMissingSource(availability))
                            }
                            else -> {
                                fail(
                                    PlayerLoadError(
                                        title = "Vídeo inválido",
                                        message = "Os dados deste vídeo estão incompletos. Volte e abra de novo."
                                    )
                                )
                                return@launch
                            }
                        }

                        val stalledFor = t - lastProgressAt
                        if (stalledFor >= STALL_RESTART_MS && restarts < MAX_RESTARTS) {
                            restarts++
                            playbackController.restartDownload(request.fileId)
                            lastProgressAt = t
                            _uiState.update {
                                it.copy(statusMessage = "download parado — reconectando ($restarts de $MAX_RESTARTS)…")
                            }
                        } else if (stalledFor >= STALL_FAIL_MS && restarts >= MAX_RESTARTS) {
                            val neverStarted = lastBytes <= 0L
                            fail(
                                if (availability is MediaAvailability.TdlibFileUnavailable && neverStarted) {
                                    PlayerLoadError(
                                        title = "Arquivo indisponível no Telegram",
                                        message = "O Telegram não liberou este arquivo. A postagem pode ter sido " +
                                            "removida ou o canal pode ter restrições.",
                                        detail = detail
                                    )
                                } else {
                                    PlayerLoadError(
                                        title = "Download parado",
                                        message = "O Telegram parou de enviar este vídeo" +
                                            (if (lastBytes > 0L) " (${formatBytes(lastBytes)} recebidos)" else "") +
                                            ". Tente novamente em instantes.",
                                        detail = detail
                                    )
                                }
                            )
                            return@launch
                        }
                        delay(1_000L)
                    }

                    is PlaybackPrepareResult.InsufficientStorage -> {
                        fail(lowStorageError(result.freeBytes, result.requiredBytes))
                        return@launch
                    }

                    is PlaybackPrepareResult.Failed -> {
                        _uiState.update {
                            it.copy(
                                isPlaceholderMode = true,
                                statusMessage = result.message,
                                snapshot = it.snapshot.copy(
                                    state = PlaybackState.Error(
                                        message = result.message,
                                        recoverable = true
                                    )
                                )
                            )
                        }
                        fail(
                            PlayerLoadError(
                                title = "Não foi possível abrir o vídeo",
                                message = "O player não conseguiu iniciar este arquivo.",
                                detail = result.message
                            )
                        )
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * Depois que o player iniciou: vigia a espera pelo 1º quadro / buffer. Download parado →
     * reinicia a reprodução (até MAX_RESTARTS) e depois vira erro; download andando mas demorando
     * → aviso de que o vídeo precisa baixar mais; erro do ExoPlayer → tela de erro.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            val startedAt = now()
            var lastBytes = playbackController.snapshot.value.downloadedBytes
            var lastProgressAt = startedAt
            var restarts = 0
            var everPlayed = false
            val speed = SpeedMeter()
            while (true) {
                delay(1_000L)
                val snap = playbackController.snapshot.value
                val t = now()
                val state = snap.state
                if (state is PlaybackState.Error && state.lowStorage) {
                    fail(lowStorageError(freeBytes = null, requiredBytes = null))
                    return@launch
                }
                if (state is PlaybackState.Error) {
                    fail(
                        PlayerLoadError(
                            title = "Erro na reprodução",
                            message = "O vídeo não pôde ser reproduzido neste aparelho ou o arquivo está corrompido.",
                            detail = state.message
                        )
                    )
                    return@launch
                }
                if (state == PlaybackState.Ready || state == PlaybackState.Paused || state == PlaybackState.Ended) {
                    everPlayed = everPlayed || state != PlaybackState.Paused || snap.currentPositionMs > 0L
                }
                val waiting = state == PlaybackState.Buffering || state == PlaybackState.Preparing
                val complete = snap.expectedBytes?.let { it > 0L && snap.downloadedBytes >= it } == true
                if (!waiting || complete) {
                    lastProgressAt = t
                    lastBytes = snap.downloadedBytes
                    if (uiState.value.downloadProgress != null || uiState.value.loadingHint != null) {
                        _uiState.update { it.copy(downloadProgress = null, loadingHint = null) }
                    }
                    continue
                }
                if (snap.downloadedBytes > lastBytes) {
                    lastBytes = snap.downloadedBytes
                    lastProgressAt = t
                }
                val hint = if (!everPlayed && t - startedAt >= SLOW_START_HINT_MS) {
                    "Este vídeo precisa baixar mais dados antes de começar (índice no fim do arquivo)."
                } else null
                _uiState.update {
                    it.copy(
                        downloadProgress = DownloadProgress(
                            downloadedBytes = snap.downloadedBytes,
                            expectedBytes = snap.expectedBytes ?: 0L,
                            bytesPerSecond = speed.sample(snap.downloadedBytes, t)
                        ),
                        loadingHint = hint
                    )
                }
                val stalledFor = t - lastProgressAt
                if (stalledFor >= STALL_RESTART_MS && restarts < MAX_RESTARTS) {
                    restarts++
                    lastProgressAt = t
                    playbackController.retry()
                } else if (stalledFor >= STALL_FAIL_MS && restarts >= MAX_RESTARTS) {
                    fail(
                        PlayerLoadError(
                            title = "Download parado",
                            message = "O Telegram parou de enviar este vídeo (${formatBytes(lastBytes)} recebidos). " +
                                "Tente novamente em instantes."
                        )
                    )
                    return@launch
                }
            }
        }
    }

    private fun lowStorageError(freeBytes: Long?, requiredBytes: Long?): PlayerLoadError {
        val detail = if (freeBytes != null && requiredBytes != null) {
            "Livre: ${formatBytes(freeBytes)} · necessário: ${formatBytes(requiredBytes)}"
        } else null
        return PlayerLoadError(
            title = "Pouco espaço no aparelho",
            message = "O app já limpou o próprio cache, mas o armazenamento continua quase cheio. " +
                "Libere espaço em Configurações › Aplicativos e tente novamente.",
            detail = detail
        )
    }

    private fun fail(error: PlayerLoadError) {
        _uiState.update { it.copy(loadError = error, downloadProgress = null, loadingHint = null) }
    }

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()

    /** Velocidade de download suavizada (média móvel) a partir de amostras de bytes acumulados. */
    private class SpeedMeter {
        private var lastBytes = -1L
        private var lastAt = 0L
        private var speed = 0L
        fun sample(bytes: Long, at: Long): Long {
            if (lastBytes < 0L) {
                lastBytes = bytes
                lastAt = at
                return speed
            }
            val dt = at - lastAt
            if (dt >= 900L) {
                val instant = (bytes - lastBytes).coerceAtLeast(0L) * 1000L / dt
                speed = if (speed == 0L) instant else (speed * 2 + instant) / 3
                lastBytes = bytes
                lastAt = at
            }
            return speed
        }
    }

    private fun messageForMissingSource(availability: MediaAvailability): String {
        return when (availability) {
            MediaAvailability.MissingRequestData -> "mídia inválida para reprodução"
            // Estados transitórios do início (o TDLib ainda está criando/baixando o arquivo).
            // Antes diziam "indisponível", o que confundia — o fluxo segue normal e o vídeo roda.
            MediaAvailability.TdlibFileUnavailable -> "preparando reprodução…"
            MediaAvailability.LocalFileMissing -> "preparando reprodução…"
            is MediaAvailability.Downloading -> "baixando… (${availability.downloadedBytes / (1024 * 1024)} MB)"
            is MediaAvailability.Ready -> "reprodução ainda não inicializada"
        }
    }
}

class PlayerScreenViewModelFactory(
    private val playbackController: PlaybackController,
    private val mediaDetailsCache: MediaDetailsCache? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerScreenViewModel::class.java)) {
            return PlayerScreenViewModel(playbackController, mediaDetailsCache) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/** "12,3 MB", "1,4 GB", "850 KB". */
internal fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val br = java.util.Locale("pt", "BR")
    return when {
        bytes >= gb -> String.format(br, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(br, "%.1f MB", bytes / mb)
        else -> String.format(br, "%.0f KB", bytes / kb)
    }
}
