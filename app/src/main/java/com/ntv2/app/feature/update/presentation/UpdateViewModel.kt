package com.ntv2.app.feature.update.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.update.data.ApkDownloadManager
import com.ntv2.app.feature.update.data.ApkDownloadState
import com.ntv2.app.feature.update.data.UpdatePreferences
import com.ntv2.app.feature.update.domain.AppUpdate
import com.ntv2.app.feature.update.domain.UpdateAvailability
import com.ntv2.app.feature.update.domain.UpdateRepository
import com.ntv2.app.feature.update.installer.ApkVerifier
import com.ntv2.app.feature.update.installer.AppUpdateInstaller
import com.ntv2.app.feature.update.installer.InstallLaunchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdateStage {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, WAITING_FOR_DOWNLOAD, DOWNLOADING,
    VERIFYING, READY_TO_INSTALL, PERMISSION_REQUIRED, UNSUPPORTED, ERROR
}

data class UpdateUiState(
    val stage: UpdateStage = UpdateStage.IDLE,
    val update: AppUpdate? = null,
    val mandatory: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null
) {
    val progress: Float?
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val downloader: ApkDownloadManager,
    private val verifier: ApkVerifier,
    private val installer: AppUpdateInstaller,
    private val preferences: UpdatePreferences,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private var monitoringJob: Job? = null

    init {
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (_state.value.stage == UpdateStage.CHECKING) return
        val hasPendingDownload = preferences.pendingDownloadId >= 0L
        if (!manual && !hasPendingDownload && clock() - preferences.lastCheckAt < AUTO_CHECK_INTERVAL_MS) return
        viewModelScope.launch {
            _state.value = UpdateUiState(stage = UpdateStage.CHECKING)
            runCatching { repository.check() }
                .onSuccess { availability ->
                    preferences.lastCheckAt = clock()
                    when (availability) {
                        UpdateAvailability.UpToDate -> {
                            val oldVersion = preferences.pendingVersionCode
                            if (oldVersion > 0L) downloader.fileFor(oldVersion).delete()
                            preferences.clearDownload()
                            _state.value = UpdateUiState(stage = UpdateStage.UP_TO_DATE)
                        }
                        is UpdateAvailability.UnsupportedDevice -> _state.value = UpdateUiState(
                            stage = UpdateStage.UNSUPPORTED,
                            message = "Esta atualização exige Android ${availability.minimumAndroidSdk} ou superior"
                        )
                        is UpdateAvailability.Available -> {
                            val ignored = preferences.ignoredVersionCode == availability.update.versionCode
                            if (ignored && !availability.mandatory && !manual) {
                                _state.value = UpdateUiState()
                            } else {
                                _state.value = UpdateUiState(
                                    stage = UpdateStage.AVAILABLE,
                                    update = availability.update,
                                    mandatory = availability.mandatory
                                )
                                if (preferences.pendingDownloadId >= 0L &&
                                    preferences.pendingVersionCode == availability.update.versionCode
                                ) monitorDownload(availability.update)
                            }
                        }
                    }
                }
                .onFailure { error ->
                    _state.value = UpdateUiState(
                        stage = UpdateStage.ERROR,
                        message = error.message ?: "Não foi possível verificar atualizações"
                    )
                }
        }
    }

    fun startDownload() {
        val update = _state.value.update ?: return
        viewModelScope.launch {
            runCatching { downloader.start(update) }
                .onSuccess { id ->
                    preferences.pendingDownloadId = id
                    preferences.pendingVersionCode = update.versionCode
                    monitorDownload(update)
                }
                .onFailure { error -> fail(error.message ?: "Não foi possível iniciar o download") }
        }
    }

    fun cancelDownload() {
        if (_state.value.mandatory) return
        monitoringJob?.cancel()
        downloader.cancel(preferences.pendingDownloadId)
        preferences.clearDownload()
        _state.update { it.copy(stage = UpdateStage.AVAILABLE, downloadedBytes = 0L, totalBytes = 0L) }
    }

    fun ignoreCurrentVersion() {
        val current = _state.value
        if (current.mandatory) return
        current.update?.let { preferences.ignoredVersionCode = it.versionCode }
        _state.value = UpdateUiState()
    }

    fun install() {
        val update = _state.value.update ?: return
        viewModelScope.launch {
            runCatching { installer.launch(downloader.fileFor(update.versionCode)) }
                .onSuccess { result ->
                    if (result == InstallLaunchResult.PERMISSION_REQUIRED) {
                        _state.update {
                            it.copy(
                                stage = UpdateStage.PERMISSION_REQUIRED,
                                message = "Ative “Permitir desta fonte”, volte ao NTV e selecione Instalar novamente."
                            )
                        }
                    }
                }
                .onFailure { error -> fail(error.message ?: "Não foi possível abrir o instalador") }
        }
    }

    fun clearTransientMessage() {
        if (_state.value.stage == UpdateStage.UP_TO_DATE || _state.value.stage == UpdateStage.ERROR) {
            _state.value = UpdateUiState()
        }
    }

    private fun monitorDownload(update: AppUpdate) {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (true) {
                when (val download = downloader.status(preferences.pendingDownloadId, update.versionCode)) {
                    ApkDownloadState.Idle -> return@launch
                    ApkDownloadState.Waiting -> _state.update {
                        it.copy(stage = UpdateStage.WAITING_FOR_DOWNLOAD, update = update)
                    }
                    is ApkDownloadState.Downloading -> _state.update {
                        it.copy(
                            stage = UpdateStage.DOWNLOADING,
                            update = update,
                            downloadedBytes = download.downloadedBytes,
                            totalBytes = download.totalBytes.takeIf { total -> total > 0L } ?: update.sizeBytes
                        )
                    }
                    is ApkDownloadState.Complete -> {
                        _state.update { it.copy(stage = UpdateStage.VERIFYING, update = update) }
                        verifier.verify(download.file, update)
                            .onSuccess {
                                _state.update { it.copy(stage = UpdateStage.READY_TO_INSTALL, update = update) }
                            }
                            .onFailure { error ->
                                download.file.delete()
                                preferences.clearDownload()
                                fail(error.message ?: "A atualização baixada é inválida")
                            }
                        return@launch
                    }
                    is ApkDownloadState.Failed -> {
                        downloader.cancel(preferences.pendingDownloadId)
                        preferences.clearDownload()
                        fail(download.reason)
                        return@launch
                    }
                }
                delay(DOWNLOAD_POLL_MS)
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(stage = UpdateStage.ERROR, message = message) }
    }

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L
        private const val DOWNLOAD_POLL_MS = 1_000L
    }
}

class UpdateViewModelFactory(
    private val repository: UpdateRepository,
    private val downloader: ApkDownloadManager,
    private val verifier: ApkVerifier,
    private val installer: AppUpdateInstaller,
    private val preferences: UpdatePreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        UpdateViewModel(repository, downloader, verifier, installer, preferences) as T
}
