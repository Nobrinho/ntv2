package com.ntv2.app.feature.media.presentation.state

data class MediaCardUi(
    val mediaId: String,
    val channelId: Long,
    val channelName: String,
    val title: String,
    val caption: String?,
    val fileName: String?,
    val durationSeconds: Int,
    val thumbnailPath: String?,
    /** Pôster do post (retrato). Capa preferida sobre [thumbnailPath] (frame do vídeo). */
    val posterPath: String? = null,
    /** Proporção real (largura/altura) da capa; 0 = desconhecida (usa fallback no layout). */
    val coverAspectRatio: Float = 0f,
    val fileId: Int,
    /** Altura do vídeo (px) para exibir a resolução (4K/1080p/...); 0 se desconhecida. */
    val videoHeight: Int = 0,
    /** Fração assistida (0f..1f) para o indicador de progresso; 0 se não houver. */
    val progress: Float = 0f,
    /** Filme dividido em partes no Telegram: total (>= 2). 1 = arquivo único. */
    val partCount: Int = 1,
    /** Banner (horizontal) do filme, quando conhecido; preferido nos cards 16:9 de "Continuar assistindo". */
    val backdropPath: String? = null
)

/** Uma linha do Histórico: card + se foi concluído + quando foi atualizado (para agrupar por dia). */
data class HistoryEntryUi(
    val card: MediaCardUi,
    val completed: Boolean,
    val updatedAt: Long
)

data class ChannelMediaSectionUi(
    val channelId: Long,
    val channelName: String,
    val items: List<MediaCardUi>,
    val hasMore: Boolean = false,
    /** O topo foi descartado (teto de itens): há páginas mais novas a buscar ao subir. */
    val hasPrevious: Boolean = false
)

/** Aba de tipo de conteúdo da biblioteca (canal ativo). */
enum class LibraryTab { MOVIES, SERIES }

/** Por que a biblioteca do canal ativo está vazia (contagens do que foi carregado e do que foi filtrado). */
data class EmptyDiagnostics(
    val channelName: String,
    /** Mídias que o canal devolveu (antes de qualquer filtro). */
    val loaded: Int,
    /** Escondidas pelo filtro de duração mínima. */
    val hiddenByDuration: Int,
    /** Episódios de série (ficam na aba Séries, não na grade de Filmes). */
    val episodes: Int,
    val minDurationMinutes: Int,
    /** Ainda há páginas a carregar no canal. */
    val hasMore: Boolean,
    val seriesCount: Int
)

sealed interface MediaLibraryEmptyState {
    data object NoChannelsSelected : MediaLibraryEmptyState
    data object NoVideosFound : MediaLibraryEmptyState
    data object NoSearchResults : MediaLibraryEmptyState
}

data class MediaNavigationPayload(
    val mediaId: String,
    val fileId: Int,
    val title: String,
    val channelName: String,
    val durationSeconds: Int,
    val fileName: String?,
    val thumbnailPath: String?
)

/** Canal disponível para escolher como ativo (picker do botão Canais). */
data class ChannelChipUi(
    val id: Long,
    val title: String,
    val avatarPath: String? = null
)

enum class SearchFilterKind { GENRE, YEAR, ACTOR }

enum class SearchTypeFilter(val label: String) { ALL("Todos"), MOVIES("Filmes"), SERIES("Séries") }

/** Filtros combináveis da busca. Gêneros: a mídia precisa ter TODOS os marcados; anos: qualquer um deles. [type] sozinho não lista nada: precisa de texto, gênero ou ano. */
data class SearchFilters(
    val type: SearchTypeFilter = SearchTypeFilter.ALL,
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
    /** Ator (nome do elenco do índice); séries não têm elenco no índice, então só filmes casam. */
    val actor: String? = null
) {
    /** Gênero ou ano ativos: listam mídias mesmo sem texto digitado. */
    val hasAttribute: Boolean get() = genres.isNotEmpty() || years.isNotEmpty() || actor != null
    val isActive: Boolean get() = hasAttribute || type != SearchTypeFilter.ALL
    val label: String get() = (listOfNotNull(actor) + genres + years.map { it.toString() }).joinToString(" • ")
}

data class SearchFilterOptions(val genres: List<String> = emptyList(), val years: List<Int> = emptyList()) {
    val isAvailable: Boolean get() = genres.isNotEmpty() || years.isNotEmpty()
}

/** Busca por filtro (chip dos Detalhes): lista as mídias do canal com esse gênero/ano. */
data class SearchFilter(val kind: SearchFilterKind, val value: String)

data class MediaLibraryUiState(
    /** Filtros da busca (botão de filtro / chips dos Detalhes); combinam com o texto digitado. */
    val searchFilters: SearchFilters = SearchFilters(),
    /** Gêneros e anos disponíveis no índice do canal ativo (vazio = sem índice → sem filtro). */
    val searchFilterOptions: SearchFilterOptions = SearchFilterOptions(),
    /** Quantos filmes do canal têm o ator filtrado (chip "Ator • N filmes"). */
    val searchActorCount: Int = 0,
    val searchQuery: String = "",
    val minDurationMinutes: Int = 15,
    val sections: List<ChannelMediaSectionUi> = emptyList(),
    /** Grade plana do canal ativo (novo layout). */
    val items: List<MediaCardUi> = emptyList(),
    /** Aba de conteúdo ativa (Filmes/Séries). */
    val libraryTab: LibraryTab = LibraryTab.MOVIES,
    /** Séries do canal ativo (índice v2); vazio = sem séries → esconde a aba Séries. */
    val series: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    /** Trilha "Continuar assistindo" (histórico não concluído, mais recente primeiro). */
    val continueWatching: List<MediaCardUi> = emptyList(),
    /** Trilha "Minha lista" (favoritos, mais recente primeiro). */
    val myList: List<MediaCardUi> = emptyList(),
    /** Ids favoritados — para marcar o coração nos cards/detalhes. */
    val favoriteIds: Set<String> = emptySet(),
    /** Histórico completo (assistidos + em andamento), mais recente primeiro. */
    val history: List<HistoryEntryUi> = emptyList(),
    /** Trilha "Recomendados para você" (por gênero, do histórico + favoritos). */
    val recommendations: List<MediaCardUi> = emptyList(),
    /** Resultados próprios da busca; não substituem a grade principal. */
    val searchResults: List<MediaCardUi> = emptyList(),
    /** Séries encontradas na busca (índice v2); abrem o overlay de temporadas/episódios. */
    val searchSeries: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    /** Progresso (0..1) por mediaId de episódio das séries da busca, para os indicadores das linhas. */
    val episodeProgress: Map<String, Float> = emptyMap(),
    val hasMore: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Incrementa a cada conclusão de "carregar mais" (sucesso ou falha) — sinal p/ a UI reagir
     *  mesmo quando o tamanho da lista não muda (teto de itens atingido). */
    val loadMoreNonce: Int = 0,
    val activeChannelId: Long? = null,
    val activeChannelName: String = "",
    val enabledChannels: List<ChannelChipUi> = emptyList(),
    /** Exibir capas (pôsteres/thumbs) — toggle das Configurações. */
    val showCovers: Boolean = true,
    /** Animação do card enquanto a capa carrega (escolhida nas Configurações). */
    val cardLoadingStyle: com.ntv2.app.core.ui.CardLoadingStyle = com.ntv2.app.core.ui.CardLoadingStyle.DEFAULT,
    /** Animações ligadas nas Configurações (também vale para a animação do card). */
    val animationsEnabled: Boolean = true,
    /** Exibir fotos do elenco na tela de detalhes — toggle das Configurações. */
    val castPhotos: Boolean = true,
    val isLoading: Boolean = false,
    /** Resolvendo o vídeo (card do índice) antes de abrir o player — feedback no botão Assistir. */
    val isOpeningVideo: Boolean = false,
    /** Falha ao resolver/abrir o vídeo — feedback de erro no botão Assistir. */
    val openVideoFailed: Boolean = false,
    /** Por que falhou (ex.: faltam partes do filme); null = mensagem genérica no botão. */
    val openVideoFailureText: String? = null,
    val isSearchPending: Boolean = false,
    val isSearchLoading: Boolean = false,
    /** Há mais páginas de busca para carregar (paginação infinita). */
    val searchHasMore: Boolean = false,
    /** Carregando a próxima página de resultados da busca. */
    val isSearchLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val emptyState: MediaLibraryEmptyState? = null,
    val lastFocusedMediaId: String? = null,
    val returnToDetailsMediaId: String? = null,
    // Card completo dos Detalhes que abriram o player (resultados da busca não estão em items).
    val returnToDetailsMedia: MediaCardUi? = null,
    /** Progresso atualizado (0..1) por mediaId, gravado depois que o card foi montado (ex.: ao sair
     *  do player). Os Detalhes usam isto para mostrar Continuar/Recomeçar sem reabrir. */
    val progressOverrides: Map<String, Float> = emptyMap(),
    val focusRestoreNonce: Long = 0,
    val pendingNavigation: MediaNavigationPayload? = null
)
