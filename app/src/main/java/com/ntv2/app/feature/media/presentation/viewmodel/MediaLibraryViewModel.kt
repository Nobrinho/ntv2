package com.ntv2.app.feature.media.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelSummary
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.feature.media.domain.MediaDetailsCache
import com.ntv2.app.feature.media.domain.MediaRepository
import com.ntv2.app.feature.media.domain.MovieDetails
import com.ntv2.app.feature.media.presentation.state.ChannelChipUi
import com.ntv2.app.feature.media.presentation.state.ChannelMediaSectionUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.state.MediaLibraryUiState
import com.ntv2.app.feature.media.presentation.state.MediaNavigationPayload
import com.ntv2.app.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MediaLibraryAction {
    data object Load : MediaLibraryAction
    data object Refresh : MediaLibraryAction
    data class SearchChanged(val query: String) : MediaLibraryAction
    data object SubmitSearch : MediaLibraryAction
    data object LoadMoreSearch : MediaLibraryAction
    data object ClearOpenVideoState : MediaLibraryAction
    data class VideoFocused(val mediaId: String) : MediaLibraryAction
    data class OpenVideo(val media: MediaCardUi) : MediaLibraryAction
    data class LoadMoreChannel(val channelId: Long) : MediaLibraryAction
    data object LoadMore : MediaLibraryAction
    data class SelectActiveChannel(val channelId: Long) : MediaLibraryAction
    data object ConsumeNavigation : MediaLibraryAction
    data object ConsumeReturnToDetails : MediaLibraryAction
    data object ScreenResumed : MediaLibraryAction
    data object ClearError : MediaLibraryAction
}

class MediaLibraryViewModel(
    private val mediaRepository: MediaRepository,
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository,
    private val progressStore: PlaybackProgressStore,
    private val mediaDetailsCache: MediaDetailsCache,
    private val maxCardsLimit: Int? = null,
    // Passo da grade por dispositivo: TV = 5 colunas, celular = 2. A paginação carrega múltiplos
    // desse passo para as linhas fecharem completas (sem sobra de meia linha).
    private val gridStep: Int = 2,
    // Índice de busca (canal rico) para busca local instantânea; null = sem índice.
    private val searchIndexRepository: com.ntv2.app.feature.media.data.index.SearchIndexRepository? = null,
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
    // Paginação infinita da busca: itens acumulados + cursor + guarda de concorrência.
    private val searchItems = mutableListOf<MediaItemSummary>()
    private var searchCursor = 0L
    private var searchLoadingMore = false
    // Página enxuta, sempre múltipla do passo da grade (TV=5 → 30; celular=2 → 24).
    private val pageSize = if (gridStep >= 5) 30 else 24
    // Primeira leva da busca menor => primeiro resultado pinta mais rápido; o resto vem no scroll.
    private val searchPageSize = if (gridStep >= 5) 10 else 8
    // Teto de páginas que a busca avança sozinha quando o filtro de título esvazia as primeiras.
    private val MAX_SEARCH_AUTO_PAGES = 5
    // Teto de itens mantidos por canal (grade não-lazy). Configurável nas Configurações.
    private var maxRetainedItems = 150

    init {
        observeSelectionAndFilter()
        // Pré-carrega o índice de busca em background para a 1ª busca já vir instantânea.
        searchIndexRepository?.let { repo -> viewModelScope.launch { runCatching { repo.covers(0L) } } }
    }

    fun onAction(action: MediaLibraryAction) {
        when (action) {
            MediaLibraryAction.Load,
            MediaLibraryAction.Refresh -> loadFirstPages()

            is MediaLibraryAction.SearchChanged -> {
                searchDebounceJob?.cancel()
                val query = action.query
                _uiState.update {
                    if (query.trim().isEmpty()) {
                        it.copy(
                            searchQuery = query,
                            searchResults = emptyList(),
                            isSearchPending = false,
                            isSearchLoading = false,
                            isSearchLoadingMore = false,
                            searchHasMore = false
                        )
                    } else {
                        it.copy(
                            searchQuery = query,
                            isSearchPending = true,
                            isSearchLoading = false
                        )
                    }
                }
                if (query.trim().isNotEmpty()) {
                    scheduleSearch(query)
                }
            }

            MediaLibraryAction.SubmitSearch -> {
                // Enter/ação de busca: dispara já, sem esperar o debounce.
                searchDebounceJob?.cancel()
                val q = _uiState.value.searchQuery.trim()
                if (q.isNotEmpty()) searchCurrentChannel(q)
            }

            MediaLibraryAction.LoadMoreSearch -> loadMoreSearch()

            MediaLibraryAction.ClearOpenVideoState ->
                _uiState.update { it.copy(isOpeningVideo = false, openVideoFailed = false) }

            is MediaLibraryAction.LoadMoreChannel -> loadMore(action.channelId)

            MediaLibraryAction.LoadMore -> _uiState.value.activeChannelId?.let { loadMore(it) }

            is MediaLibraryAction.SelectActiveChannel -> {
                viewModelScope.launch { settingsRepository.updateActiveChannelId(action.channelId) }
            }

            is MediaLibraryAction.VideoFocused -> {
                _uiState.update { it.copy(lastFocusedMediaId = action.mediaId) }
            }

            is MediaLibraryAction.OpenVideo -> openVideo(action.media)

            MediaLibraryAction.ConsumeNavigation -> {
                _uiState.update { it.copy(pendingNavigation = null) }
            }

            MediaLibraryAction.ConsumeReturnToDetails -> {
                _uiState.update { it.copy(returnToDetailsMediaId = null, returnToDetailsMedia = null) }
            }

            MediaLibraryAction.ScreenResumed -> {
                if (_uiState.value.lastFocusedMediaId != null) {
                    _uiState.update { it.copy(focusRestoreNonce = it.focusRestoreNonce + 1) }
                }
                // Se o canal ativo ficou vazio (ex.: carregou antes do histórico do login), recarrega.
                val s = _uiState.value
                if (!s.isLoading && s.items.isEmpty() && s.searchQuery.isBlank() && s.activeChannelId != null) {
                    loadFirstPages()
                } else {
                    // Atualiza o indicador de progresso após voltar da reprodução.
                    projectSections()
                }
            }

            MediaLibraryAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private var loadedChannelId: Long? = null

    private fun observeSelectionAndFilter() {
        // Novo modelo: UM canal ativo por vez. Canais habilitados vêm da seleção (Configurações);
        // o canal ativo é uma preferência (botão Canais). Carrega só o canal ativo.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                channelRepository.observeSelectedChannels(),
                settingsRepository.activeChannelId
            ) { enabled, activePref -> enabled to activePref }
                .collect { (enabled, activePref) ->
                    currentChannelsCount = enabled.size
                    val chips = enabled.map { ChannelChipUi(it.id, it.title, it.avatarPath) }
                    val active = enabled.firstOrNull { it.id == activePref } ?: enabled.firstOrNull()
                    _uiState.update {
                        it.copy(
                            enabledChannels = chips,
                            activeChannelId = active?.id,
                            activeChannelName = active?.title ?: ""
                        )
                    }
                    if (enabled.isEmpty()) {
                        clearChannelData()
                        loadedChannelId = null
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSearchPending = false,
                                isSearchLoading = false,
                                sections = emptyList(),
                                items = emptyList(),
                                searchResults = emptyList(),
                                hasMore = false,
                                emptyState = MediaLibraryEmptyState.NoChannelsSelected,
                                errorMessage = null
                            )
                        }
                    } else if (active != null && active.id != loadedChannelId) {
                        loadedChannelId = active.id
                        loadActiveChannel(active)
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.minDurationMinutes.collect { minDuration ->
                _uiState.update { it.copy(minDurationMinutes = minDuration) }
                projectSections()
            }
        }
        viewModelScope.launch {
            settingsRepository.showCovers.collect { show ->
                _uiState.update { it.copy(showCovers = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.castPhotos.collect { on ->
                _uiState.update { it.copy(castPhotos = on) }
            }
        }
        viewModelScope.launch {
            settingsRepository.maxCards.collect { max ->
                val effectiveMax = maxCardsLimit?.let { max.coerceAtMost(it) } ?: max
                maxRetainedItems = effectiveMax
                // Aplica o novo teto imediatamente ao canal ativo (apara o excedente do topo).
                var changed = false
                channelItems.keys.toList().forEach { id ->
                    val items = channelItems[id] ?: return@forEach
                    if (items.size > effectiveMax) {
                        channelItems[id] = items.takeLast(effectiveMax)
                        changed = true
                    }
                }
                if (changed) projectSections()
            }
        }
    }

    /** Carrega a 1ª página do canal ativo (grade plana). */
    private fun loadActiveChannel(channel: ChannelSummary) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            clearChannelData()
            channelOrder += channel.id
            channelTitles[channel.id] = channel.title
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isSearchPending = false,
                    isSearchLoading = false,
                    errorMessage = null,
                    emptyState = null
                )
            }
            val query = ""
            // Logo após o login o histórico do canal ainda pode não ter carregado no TDLib e a 1ª
            // busca volta vazia — tenta de novo algumas vezes com um pequeno intervalo.
            var page: com.ntv2.app.feature.media.domain.MediaPage? = null
            var attempt = 0
            while (attempt < 4) {
                val result = runCatching {
                    withContext(ioDispatcher) { fetchPage(channel.id, channel.title, query, fromMessageId = 0L) }
                }.getOrElse { error ->
                    if (attempt == 3) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSearchPending = false,
                                isSearchLoading = false,
                                errorMessage = error.message ?: "Falha ao carregar biblioteca"
                            )
                        }
                        return@launch
                    }
                    null
                }
                if (result != null) {
                    page = result
                    if (result.items.isNotEmpty() || query.isNotBlank()) break
                }
                attempt++
                if (attempt < 4) delay(1_200L)
            }
            channelItems[channel.id] = dedupByTmdb(page?.items.orEmpty())
            channelCursors[channel.id] = page?.nextCursor ?: 0L
            // Transição atômica: desliga o skeleton JUNTO com os itens/emptyState já calculados,
            // evitando um frame intermediário com "nenhum vídeo" antes das mídias aparecerem.
            val sections = computeSections()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isSearchPending = false,
                    isSearchLoading = false,
                    sections = sections,
                    items = sections.flatMap { s -> s.items },
                    hasMore = sections.firstOrNull()?.hasMore ?: false,
                    emptyState = emptyStateFor(sections)
                )
            }
        }
    }

    private fun openVideo(media: MediaCardUi) {
        // Card normal (já tem fileId do TDLib): navega direto.
        if (media.fileId != 0) {
            _uiState.update {
                it.copy(
                    pendingNavigation = MediaNavigationPayload(
                        mediaId = media.mediaId,
                        fileId = media.fileId,
                        title = media.title,
                        channelName = media.channelName,
                        durationSeconds = media.durationSeconds,
                        fileName = media.fileName,
                        thumbnailPath = media.thumbnailPath
                    ),
                    lastFocusedMediaId = media.mediaId,
                    returnToDetailsMediaId = media.mediaId,
                    returnToDetailsMedia = media
                )
            }
            return
        }
        // Card do índice (sem fileId): resolve a mensagem no TDLib para obter o fileId e reproduzir.
        val messageId = media.mediaId.substringAfterLast('_').toLongOrNull() ?: return
        _uiState.update {
            it.copy(
                isOpeningVideo = true,
                openVideoFailed = false,
                lastFocusedMediaId = media.mediaId,
                returnToDetailsMediaId = media.mediaId,
                returnToDetailsMedia = media
            )
        }
        viewModelScope.launch {
            val resolved = withContext(ioDispatcher) {
                runCatching { mediaRepository.getVideoByMessage(media.channelId, media.channelName, messageId) }.getOrNull()
            }
            if (resolved == null || resolved.fileId == 0) {
                _uiState.update { it.copy(isOpeningVideo = false, openVideoFailed = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isOpeningVideo = false,
                    openVideoFailed = false,
                    pendingNavigation = MediaNavigationPayload(
                        mediaId = media.mediaId,
                        fileId = resolved.fileId,
                        title = media.title,
                        channelName = media.channelName,
                        durationSeconds = resolved.durationSeconds,
                        fileName = resolved.fileName,
                        thumbnailPath = media.posterPath ?: media.thumbnailPath
                    )
                )
            }
        }
    }

    private fun com.ntv2.app.feature.media.data.index.IndexMovie.toSummary(
        channelId: Long,
        channelTitle: String
    ): MediaItemSummary = MediaItemSummary(
        mediaId = "${channelId}_$videoMessageId",
        channelId = channelId,
        channelTitle = channelTitle,
        title = title,
        caption = null,
        fileName = null,
        durationSeconds = 0,
        thumbnailPath = null,
        fileId = 0,
        coverAspectRatio = 0f,
        posterPath = posterUrl,
        synopsis = overview,
        year = year?.take(4)?.toIntOrNull(),
        genres = genres.joinToString(", ").ifBlank { null },
        originalTitle = originalTitle,
        backdropPath = backdropUrl,
        cast = cast,
        tmdbId = tmdbId.toString()
    )

    private fun scheduleSearch(query: String) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300L)
            searchCurrentChannel(query.trim())
        }
    }

    private fun searchCurrentChannel(query: String) {
        if (query.isBlank()) return
        val activeId = _uiState.value.activeChannelId ?: return
        val title = channelTitles[activeId] ?: _uiState.value.activeChannelName
        viewModelScope.launch {
            searchItems.clear()
            searchCursor = 0L
            searchLoadingMore = false
            _uiState.update {
                it.copy(
                    isSearchPending = false,
                    isSearchLoading = true,
                    isSearchLoadingMore = false,
                    searchHasMore = false,
                    searchResults = emptyList()
                )
            }
            // Índice local (canal rico): busca instantânea por título, sem TDLib.
            val idx = searchIndexRepository
            if (idx != null && runCatching { idx.covers(activeId) }.getOrDefault(false)) {
                val movies = runCatching { idx.search(activeId, query) }.getOrDefault(emptyList())
                if (_uiState.value.searchQuery.trim() != query) return@launch
                val summaries = dedupByTmdb(movies.map { it.toSummary(activeId, title) })
                val saved = withContext(ioDispatcher) { progressStore.savedPositions(summaries.map { it.mediaId }) }
                _uiState.update { current ->
                    if (current.searchQuery.trim() != query) current
                    else current.copy(
                        isSearchLoading = false,
                        isSearchLoadingMore = false,
                        searchHasMore = false,
                        searchResults = summaries.map { it.toCard(saved[it.mediaId] ?: 0L) }
                    )
                }
                return@launch
            }
            try {
                // Palavras comuns (ex.: "segredo") retornam muitas mensagens que só casam na SINOPSE;
                // o filtro de título as descarta e a página fica vazia. Avança páginas até achar ao
                // menos um título relevante (ou acabar / atingir o teto), para não devolver vazio.
                var pages = 0
                do {
                    val page = withContext(ioDispatcher) {
                        fetchPage(activeId, title, query, fromMessageId = searchCursor, limit = searchPageSize)
                    }
                    if (_uiState.value.searchQuery.trim() != query) return@launch
                    searchItems += page.items
                    searchCursor = page.nextCursor
                    pages++
                } while (dedupByTmdb(searchItems).isEmpty() && searchCursor != 0L && pages < MAX_SEARCH_AUTO_PAGES)
                publishSearchResults(query, searchCursor != 0L)
            } catch (e: Throwable) {
                _uiState.update { current ->
                    if (current.searchQuery.trim() != query) current
                    else current.copy(isSearchLoading = false, searchResults = emptyList())
                }
            }
        }
    }

    private fun loadMoreSearch() {
        val query = _uiState.value.searchQuery.trim()
        val state = _uiState.value
        if (query.isEmpty() || searchLoadingMore || !state.searchHasMore ||
            state.isSearchLoading || searchCursor == 0L
        ) return
        val activeId = state.activeChannelId ?: return
        val title = channelTitles[activeId] ?: state.activeChannelName
        searchLoadingMore = true
        _uiState.update { it.copy(isSearchLoadingMore = true) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { fetchPage(activeId, title, query, fromMessageId = searchCursor, limit = searchPageSize) }
            }.onSuccess { page ->
                if (_uiState.value.searchQuery.trim() != query) { searchLoadingMore = false; return@launch }
                searchItems += page.items
                searchCursor = page.nextCursor
                searchLoadingMore = false
                publishSearchResults(query, page.nextCursor != 0L)
            }.onFailure {
                searchLoadingMore = false
                _uiState.update { it.copy(isSearchLoadingMore = false) }
            }
        }
    }

    /** Deduplica os itens acumulados da busca, resolve progresso e publica em searchResults. */
    private suspend fun publishSearchResults(query: String, hasMore: Boolean) {
        val items = dedupByTmdb(searchItems)
        val savedPositions = withContext(ioDispatcher) {
            progressStore.savedPositions(items.map { it.mediaId })
        }
        _uiState.update { current ->
            if (current.searchQuery.trim() != query) current
            else current.copy(
                isSearchLoading = false,
                isSearchLoadingMore = false,
                searchHasMore = hasMore,
                searchResults = items.map { it.toCard(savedPositions[it.mediaId] ?: 0L) }
            )
        }
    }

    /** Recarrega a 1ª página do canal ativo (Atualizar / busca). */
    private fun loadFirstPages() {
        val activeId = _uiState.value.activeChannelId ?: return
        val title = channelTitles[activeId] ?: _uiState.value.activeChannelName
        loadActiveChannel(ChannelSummary(id = activeId, title = title, avatarPath = null))
    }

    /** Carrega a próxima página de um canal específico e a acrescenta. */
    private fun loadMore(channelId: Long) {
        val cursor = channelCursors[channelId] ?: 0L
        if (cursor == 0L || channelId in loadingMore) return
        val title = channelTitles[channelId] ?: return
        loadingMore += channelId
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    fetchPage(channelId, title, query = "", fromMessageId = cursor)
                }
            }.onSuccess { page ->
                val existing = channelItems[channelId].orEmpty()
                val seen = existing.mapTo(HashSet()) { it.mediaId }
                val merged = dedupByTmdb(existing + page.items.filter { seen.add(it.mediaId) })
                // Teto de memória: grade é não-lazy, então limitamos os itens mantidos, descartando
                // os mais antigos (do topo) e preservando os recém-carregados (do fim).
                channelItems[channelId] =
                    if (merged.size > maxRetainedItems) merged.takeLast(maxRetainedItems) else merged
                channelCursors[channelId] = page.nextCursor
                // Atualização atômica com o nonce: a UI reage mesmo se o tamanho não mudar (teto).
                val sections = computeSections()
                _uiState.update {
                    it.copy(
                        sections = sections,
                        items = sections.flatMap { s -> s.items },
                        hasMore = sections.firstOrNull()?.hasMore ?: false,
                        emptyState = emptyStateFor(sections),
                        loadMoreNonce = it.loadMoreNonce + 1
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message ?: "Falha ao carregar mais",
                        loadMoreNonce = it.loadMoreNonce + 1
                    )
                }
            }
            loadingMore -= channelId
        }
    }

    private suspend fun fetchPage(
        channelId: Long,
        channelTitle: String,
        query: String,
        fromMessageId: Long,
        limit: Int = pageSize
    ) =
        if (query.isBlank()) {
            mediaRepository.fetchChannelVideos(channelId, channelTitle, fromMessageId, limit)
        } else {
            mediaRepository.searchChannelVideos(channelId, channelTitle, query, fromMessageId, limit)
        }

    private fun projectSections() {
        viewModelScope.launch {
            val sections = computeSections()
            _uiState.update {
                it.copy(
                    sections = sections,
                    items = sections.flatMap { s -> s.items },
                    hasMore = sections.firstOrNull()?.hasMore ?: false,
                    emptyState = emptyStateFor(sections)
                )
            }
        }
    }

    /** Monta as seções (canal ativo) aplicando o filtro de duração e o progresso salvo. */
    private suspend fun computeSections(): List<ChannelMediaSectionUi> {
        val minSeconds = _uiState.value.minDurationMinutes * 60
        return withContext(ioDispatcher) {
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
    }

    private fun emptyStateFor(sections: List<ChannelMediaSectionUi>): MediaLibraryEmptyState? = when {
        currentChannelsCount == 0 -> MediaLibraryEmptyState.NoChannelsSelected
        sections.isEmpty() -> MediaLibraryEmptyState.NoVideosFound
        else -> null
    }

    /** Detalhes ricos para a tela de Detalhes (lidos do cache por mediaId). */
    fun detailsFor(mediaId: String): MovieDetails? = mediaDetailsCache.get(mediaId)

    /** Remove filmes repetidos pelo mesmo TMDB id (canal rico), mantendo o primeiro. Itens sem id
     *  (ex.: canais Polemic) passam sem alteração. */
    private fun dedupByTmdb(items: List<MediaItemSummary>): List<MediaItemSummary> {
        val seen = HashSet<String>()
        return items.filter { it.tmdbId.isNullOrBlank() || seen.add(it.tmdbId!!) }
    }

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
        // Popula o cache de detalhes para a tela de reprodução ler por mediaId.
        mediaDetailsCache.put(
            mediaId,
            MovieDetails(
                title = title,
                posterPath = posterPath,
                synopsis = synopsis,
                year = year,
                director = director,
                audio = audio,
                genres = genres,
                originalTitle = originalTitle,
                backdropPath = backdropPath,
                durationSeconds = durationSeconds,
                rating = rating,
                ageRating = ageRating,
                country = country,
                quality = quality,
                studio = studio,
                cast = cast,
                trailerUrl = trailerUrl,
                category = category,
                collection = collection
            )
        )
        return MediaCardUi(
            mediaId = mediaId,
            channelId = channelId,
            channelName = channelTitle,
            title = title,
            caption = caption,
            fileName = fileName,
            durationSeconds = durationSeconds,
            thumbnailPath = thumbnailPath,
            posterPath = posterPath,
            coverAspectRatio = coverAspectRatio,
            fileId = fileId,
            videoHeight = height,
            progress = progress
        )
    }
}

class MediaLibraryViewModelFactory(
    private val mediaRepository: MediaRepository,
    private val channelRepository: ChannelRepository,
    private val settingsRepository: SettingsRepository,
    private val progressStore: PlaybackProgressStore,
    private val mediaDetailsCache: MediaDetailsCache,
    private val maxCardsLimit: Int? = null,
    private val gridStep: Int = 2,
    private val searchIndexRepository: com.ntv2.app.feature.media.data.index.SearchIndexRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaLibraryViewModel::class.java)) {
            return MediaLibraryViewModel(
                mediaRepository = mediaRepository,
                channelRepository = channelRepository,
                settingsRepository = settingsRepository,
                progressStore = progressStore,
                mediaDetailsCache = mediaDetailsCache,
                maxCardsLimit = maxCardsLimit,
                gridStep = gridStep,
                searchIndexRepository = searchIndexRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
