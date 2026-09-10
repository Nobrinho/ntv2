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
    val seekMinutes: Int = 5
)

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
    data object SeekForward : PlayerScreenAction
    data object SeekBack : PlayerScreenAction
    data object OnAppStop : PlayerScreenAction
    data object OnAppResume : PlayerScreenAction
    data object Release : PlayerScreenAction
}

class PlayerScreenViewModel(
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerScreenUiState())
    val uiState: StateFlow<PlayerScreenUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    val player: Player? get() = playbackController.player
    private var prepareJob: Job? = null

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
                _uiState.update {
                    it.copy(
                        title = action.title,
                        channelName = action.channelName,
                        durationSeconds = action.durationSeconds,
                        fileName = action.fileName,
                        thumbnailPath = action.thumbnailPath,
                        statusMessage = "preparando reprodução…"
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
            PlayerScreenAction.SeekForward -> {
                if (uiState.value.isPlaceholderMode) return
                val current = uiState.value.snapshot.currentPositionMs
                val seek = uiState.value.seekMinutes * 60_000L
                playbackController.seekTo(current + seek)
            }

            PlayerScreenAction.SeekBack -> {
                if (uiState.value.isPlaceholderMode) return
                val current = uiState.value.snapshot.currentPositionMs
                val seek = uiState.value.seekMinutes * 60_000L
                playbackController.seekTo((current - seek).coerceAtLeast(0L))
            }

            PlayerScreenAction.OnAppStop -> if (!uiState.value.isPlaceholderMode) playbackController.onAppStop()
            PlayerScreenAction.OnAppResume -> if (!uiState.value.isPlaceholderMode) playbackController.onAppResume()
            PlayerScreenAction.Release -> playbackController.release()
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        prepareJob?.cancel()
        playbackController.release()
        super.onCleared()
    }

    /**
     * Tenta preparar a reprodução e, enquanto o arquivo ainda está baixando (fonte indisponível),
     * reexecuta periodicamente até haver bytes suficientes — assim a tela inicia sozinha sem o
     * usuário precisar voltar e reabrir o vídeo.
     */
    private fun startPrepareLoop(request: PlaybackPrepareRequest) {
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            var attempt = 0
            val maxAttempts = 60 // ~60s aguardando bytes iniciais
            while (true) {
                when (val result = playbackController.prepare(request)) {
                    PlaybackPrepareResult.Started -> {
                        _uiState.update {
                            it.copy(isPlaceholderMode = false, statusMessage = "reproduzindo")
                        }
                        return@launch
                    }

                    is PlaybackPrepareResult.MissingSource -> {
                        _uiState.update {
                            it.copy(
                                isPlaceholderMode = true,
                                statusMessage = messageForMissingSource(result.availability)
                            )
                        }
                        val retriable = when (result.availability) {
                            is MediaAvailability.Downloading,
                            MediaAvailability.LocalFileMissing,
                            MediaAvailability.TdlibFileUnavailable -> true
                            else -> false
                        }
                        if (!retriable || attempt++ >= maxAttempts) return@launch
                        delay(1_000L)
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
                        return@launch
                    }
                }
            }
        }
    }

    private fun messageForMissingSource(availability: MediaAvailability): String {
        return when (availability) {
            MediaAvailability.MissingRequestData -> "mídia inválida para reprodução"
            MediaAvailability.TdlibFileUnavailable -> "arquivo indisponível no Telegram"
            MediaAvailability.LocalFileMissing -> "arquivo local ainda não preparado"
            is MediaAvailability.Downloading -> "arquivo em preparação (${availability.downloadedBytes / (1024 * 1024)} MB)"
            is MediaAvailability.Ready -> "reprodução ainda não inicializada"
        }
    }
}

class PlayerScreenViewModelFactory(
    private val playbackController: PlaybackController
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerScreenViewModel::class.java)) {
            return PlayerScreenViewModel(playbackController) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
