package com.ntv2.app.feature.media.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelSummary
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.MediaRepository
import com.ntv2.app.feature.media.presentation.state.ChannelMediaSectionUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.state.MediaLibraryUiState
import com.ntv2.app.feature.media.presentation.state.MediaNavigationPayload
import com.ntv2.app.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MediaLibraryAction {
    data object Load : MediaLibraryAction
    data object Refresh : MediaLibraryAction
    data class SearchChanged(val query: String) : MediaLibraryAction
    data class VideoFocused(val mediaId: String) : MediaLibraryAction
    data class OpenVideo(val media: MediaCardUi) : MediaLibraryAction
    data object ConsumeNavigation : MediaLibraryAction
    data object ScreenResumed : MediaLibraryAction
    data object ClearError : MediaLibraryAction
}

class MediaLibraryViewModel(
    private val mediaRepository: MediaRepository,
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaLibraryUiState(isLoading = true))
    val uiState: StateFlow<MediaLibraryUiState> = _uiState.asStateFlow()

    private var rawItems: List<MediaItemSummary> = emptyList()
    private var currentChannelsCount: Int = 0
    private var reloadJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeSelectionAndFilter()
    }

    fun onAction(action: MediaLibraryAction) {
        when (action) {
            MediaLibraryAction.Load,
            MediaLibraryAction.Refresh -> reload()

            is MediaLibraryAction.SearchChanged -> {
                _uiState.update { it.copy(searchQuery = action.query) }
                scheduleProjection()
            }

            is MediaLibraryAction.VideoFocused -> {
                _uiState.update { it.copy(lastFocusedMediaId = action.mediaId) }
            }

            is MediaLibraryAction.OpenVideo -> {
                _uiState.update {
                    it.copy(
                        pendingNavigation = MediaNavigationPayload(
                            mediaId = action.media.mediaId,
                            fileId = action.media.fileId,
                            title = action.media.title,
                            channelName = action.media.channelName,
                            durationSeconds = action.media.durationSeconds,
                            fileName = action.media.fileName,
                            thumbnailPath = action.media.thumbnailPath
                        ),
                        lastFocusedMediaId = action.media.mediaId
                    )
                }
            }

            MediaLibraryAction.ConsumeNavigation -> {
                _uiState.update { it.copy(pendingNavigation = null) }
            }

            MediaLibraryAction.ScreenResumed -> {
                if (_uiState.value.lastFocusedMediaId != null) {
                    _uiState.update { it.copy(focusRestoreNonce = it.focusRestoreNonce + 1) }
                }
            }

            MediaLibraryAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun observeSelectionAndFilter() {
        viewModelScope.launch {
            combine(
                channelRepository.observeSelectedChannels(),
                settingsRepository.minDurationMinutes
            ) { channels, minDuration ->
                channels to minDuration
            }.collect { (channels, minDuration) ->
                currentChannelsCount = channels.size
                _uiState.update { it.copy(minDurationMinutes = minDuration) }

                if (channels.isEmpty()) {
                    rawItems = emptyList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sections = emptyList(),
                            emptyState = MediaLibraryEmptyState.NoChannelsSelected,
                            errorMessage = null
                        )
                    }
                } else {
                    reload(channelsOverride = channels)
                }
            }
        }
    }

    private fun reload(channelsOverride: List<ChannelSummary>? = null) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val channels = channelsOverride ?: channelRepository.observeSelectedChannels().first()
            if (channels.isEmpty()) {
                rawItems = emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sections = emptyList(),
                        emptyState = MediaLibraryEmptyState.NoChannelsSelected,
                        errorMessage = null
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null, emptyState = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val merged = mutableListOf<MediaItemSummary>()
                    channels.forEach { channel ->
                        merged += mediaRepository.fetchChannelVideos(channel.id, channel.title)
                    }
                    merged
                }
            }.onSuccess { result ->
                rawItems = result
                _uiState.update { it.copy(isLoading = false) }
                scheduleProjection(immediate = true)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Falha ao carregar biblioteca"
                    )
                }
            }
        }
    }

    private fun scheduleProjection(immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) {
                delay(250L)
            }
            applyUiProjection()
        }
    }

    private fun applyUiProjection() {
        viewModelScope.launch {
            val state = _uiState.value
            val minSeconds = state.minDurationMinutes * 60
            val query = state.searchQuery.trim().lowercase()

            val sections = withContext(Dispatchers.Default) {
                val filteredByDuration = rawItems.asSequence()
                    .filter { item -> item.durationSeconds >= minSeconds }

                val filtered = if (query.isBlank()) {
                    filteredByDuration.toList()
                } else {
                    filteredByDuration.filter { item ->
                        item.title.lowercase().contains(query) ||
                            (item.caption?.lowercase()?.contains(query) == true) ||
                            (item.fileName?.lowercase()?.contains(query) == true)
                    }.toList()
                }

                filtered
                    .groupBy { it.channelId to it.channelTitle }
                    .map { (channel, items) ->
                        ChannelMediaSectionUi(
                            channelId = channel.first,
                            channelName = channel.second,
                            items = items.map { summary ->
                                MediaCardUi(
                                    mediaId = summary.mediaId,
                                    channelId = summary.channelId,
                                    channelName = summary.channelTitle,
                                    title = summary.title,
                                    caption = summary.caption,
                                    fileName = summary.fileName,
                                    durationSeconds = summary.durationSeconds,
                                    thumbnailPath = summary.thumbnailPath,
                                    fileId = summary.fileId
                                )
                            }
                        )
                    }
                    .sortedBy { it.channelName }
            }

            val emptyState = when {
                currentChannelsCount == 0 -> MediaLibraryEmptyState.NoChannelsSelected
                sections.isEmpty() && query.isNotBlank() -> MediaLibraryEmptyState.NoSearchResults
                sections.isEmpty() -> MediaLibraryEmptyState.NoVideosFound
                else -> null
            }

            _uiState.update {
                it.copy(
                    sections = sections,
                    emptyState = emptyState
                )
            }
        }
    }
}

class MediaLibraryViewModelFactory(
    private val mediaRepository: MediaRepository,
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaLibraryViewModel::class.java)) {
            return MediaLibraryViewModel(
                mediaRepository = mediaRepository,
                channelRepository = channelRepository,
                settingsRepository = settingsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
