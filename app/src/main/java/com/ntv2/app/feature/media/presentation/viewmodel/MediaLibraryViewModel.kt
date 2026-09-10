package com.ntv2.app.feature.media.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelSummary
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.feature.media.domain.MediaRepository
import com.ntv2.app.feature.media.presentation.state.ChannelMediaSectionUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.state.MediaLibraryUiState
import com.ntv2.app.feature.media.presentation.state.MediaNavigationPayload
import com.ntv2.app.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    data class LoadMoreChannel(val channelId: Long) : MediaLibraryAction
    data object ConsumeNavigation : MediaLibraryAction
    data object ScreenResumed : MediaLibraryAction
    data object ClearError : MediaLibraryAction
}

class MediaLibraryViewModel(
    private val mediaRepository: MediaRepository,
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository,
    private val progressStore: PlaybackProgressStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaLibraryUiState(isLoading = true))
    val uiState: StateFlow<MediaLibraryUiState> = _uiState.asStateFlow()

    // Paginação por canal: cada canal acumula suas páginas e mantém seu cursor.
    private val channelOrder = mutableListOf<Long>()
    private val channelTitles = mutableMapOf<Long, String>()
    private val channelItems = mutableMapOf<Long, List<MediaItemSummary>>()
    private val channelCursors = mutableMapOf<Long, Long>() // nextFromMessageId; 0 => fim
    private val loadingMore = mutableSetOf<Long>()
    private var currentChannelsCount: Int = 0
    private var loadJob: Job? = null
    private var searchDebounceJob: Job? = null
    private val pageSize = 40

    init {
        observeSelectionAndFilter()
    }

    fun onAction(action: MediaLibraryAction) {
        when (action) {
            MediaLibraryAction.Load,
            MediaLibraryAction.Refresh -> loadFirstPages()

            is MediaLibraryAction.SearchChanged -> {
                _uiState.update { it.copy(searchQuery = action.query) }
                scheduleReload()
            }

            is MediaLibraryAction.LoadMoreChannel -> loadMore(action.channelId)

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
                // Atualiza o indicador de progresso após voltar da reprodução.
                projectSections()
            }

            MediaLibraryAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun observeSelectionAndFilter() {
        // Seleção de canais dispara (re)carga; mudança de duração mínima apenas re-projeta.
        viewModelScope.launch {
            channelRepository.observeSelectedChannels().collect { channels ->
                currentChannelsCount = channels.size
                if (channels.isEmpty()) {
                    clearChannelData()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sections = emptyList(),
                            emptyState = MediaLibraryEmptyState.NoChannelsSelected,
                            errorMessage = null
                        )
                    }
                } else {
                    loadFirstPages(channels)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.minDurationMinutes.collect { minDuration ->
                _uiState.update { it.copy(minDurationMinutes = minDuration) }
                projectSections()
            }
        }
    }

    private fun scheduleReload() {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300L)
            loadFirstPages()
        }
    }

    /** Carrega a primeira página de cada canal (modo lista ou busca, conforme a query atual). */
    private fun loadFirstPages(channelsOverride: List<ChannelSummary>? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val channels = channelsOverride ?: channelRepository.observeSelectedChannels().first()
            if (channels.isEmpty()) {
                clearChannelData()
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

            clearChannelData()
            channels.sortedBy { it.title }.forEach { channelOrder += it.id }
            channels.forEach { channelTitles[it.id] = it.title }

            _uiState.update { it.copy(isLoading = true, errorMessage = null, emptyState = null) }
            val query = currentQuery()
            runCatching {
                withContext(ioDispatcher) {
                    coroutineScope {
                        channels.map { channel ->
                            async { channel.id to fetchPage(channel.id, channel.title, query, fromMessageId = 0L) }
                        }.awaitAll()
                    }
                }
            }.onSuccess { results ->
                results.forEach { (id, page) ->
                    channelItems[id] = page.items
                    channelCursors[id] = page.nextCursor
                }
                _uiState.update { it.copy(isLoading = false) }
                projectSections()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Falha ao carregar biblioteca")
                }
            }
        }
    }

    /** Carrega a próxima página de um canal específico e a acrescenta. */
    private fun loadMore(channelId: Long) {
        val cursor = channelCursors[channelId] ?: 0L
        if (cursor == 0L || channelId in loadingMore) return
        val title = channelTitles[channelId] ?: return
        loadingMore += channelId
        viewModelScope.launch {
            val query = currentQuery()
            runCatching {
                withContext(ioDispatcher) {
                    fetchPage(channelId, title, query, fromMessageId = cursor)
                }
            }.onSuccess { page ->
                val existing = channelItems[channelId].orEmpty()
                val seen = existing.mapTo(HashSet()) { it.mediaId }
                channelItems[channelId] = existing + page.items.filter { seen.add(it.mediaId) }
                channelCursors[channelId] = page.nextCursor
                projectSections()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Falha ao carregar mais") }
            }
            loadingMore -= channelId
        }
    }

    private suspend fun fetchPage(channelId: Long, channelTitle: String, query: String, fromMessageId: Long) =
        if (query.isBlank()) {
            mediaRepository.fetchChannelVideos(channelId, channelTitle, fromMessageId, pageSize)
        } else {
            mediaRepository.searchChannelVideos(channelId, channelTitle, query, fromMessageId, pageSize)
        }

    private fun projectSections() {
        viewModelScope.launch {
            val minSeconds = _uiState.value.minDurationMinutes * 60
            val hasQuery = currentQuery().isNotBlank()

            val sections = withContext(ioDispatcher) {
                val visibleIds = channelOrder.flatMap { id -> channelItems[id].orEmpty().map { it.mediaId } }
                val savedPositions = progressStore.savedPositions(visibleIds)
                channelOrder.mapNotNull { id ->
                    val items = channelItems[id] ?: return@mapNotNull null
                    val filtered = items.filter { it.durationSeconds >= minSeconds }
                    if (filtered.isEmpty()) return@mapNotNull null
                    ChannelMediaSectionUi(
                        channelId = id,
                        channelName = channelTitles[id] ?: "",
                        items = filtered.map { it.toCard(savedPositions[it.mediaId] ?: 0L) },
                        hasMore = (channelCursors[id] ?: 0L) != 0L
                    )
                }
            }

            val emptyState = when {
                currentChannelsCount == 0 -> MediaLibraryEmptyState.NoChannelsSelected
                sections.isEmpty() && hasQuery -> MediaLibraryEmptyState.NoSearchResults
                sections.isEmpty() -> MediaLibraryEmptyState.NoVideosFound
                else -> null
            }

            _uiState.update { it.copy(sections = sections, emptyState = emptyState) }
        }
    }

    private fun currentQuery(): String = _uiState.value.searchQuery.trim()

    private fun clearChannelData() {
        channelOrder.clear()
        channelTitles.clear()
        channelItems.clear()
        channelCursors.clear()
        loadingMore.clear()
    }

    private fun MediaItemSummary.toCard(savedPositionMs: Long): MediaCardUi {
        val totalMs = durationSeconds * 1_000L
        val progress = if (totalMs > 0L && savedPositionMs > 0L) {
            (savedPositionMs.toFloat() / totalMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        return MediaCardUi(
            mediaId = mediaId,
            channelId = channelId,
            channelName = channelTitle,
            title = title,
            caption = caption,
            fileName = fileName,
            durationSeconds = durationSeconds,
            thumbnailPath = thumbnailPath,
            fileId = fileId,
            progress = progress
        )
    }
}

class MediaLibraryViewModelFactory(
    private val mediaRepository: MediaRepository,
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository,
    private val progressStore: PlaybackProgressStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaLibraryViewModel::class.java)) {
            return MediaLibraryViewModel(
                mediaRepository = mediaRepository,
                channelRepository = channelRepository,
                settingsRepository = settingsRepository,
                progressStore = progressStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
