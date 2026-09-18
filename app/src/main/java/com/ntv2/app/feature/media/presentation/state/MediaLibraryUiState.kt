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
    val progress: Float = 0f
)

data class ChannelMediaSectionUi(
    val channelId: Long,
    val channelName: String,
    val items: List<MediaCardUi>,
    val hasMore: Boolean = false
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

data class MediaLibraryUiState(
    val searchQuery: String = "",
    val minDurationMinutes: Int = 15,
    val sections: List<ChannelMediaSectionUi> = emptyList(),
    /** Grade plana do canal ativo (novo layout). */
    val items: List<MediaCardUi> = emptyList(),
    /** Resultados próprios da busca; não substituem a grade principal. */
    val searchResults: List<MediaCardUi> = emptyList(),
    val hasMore: Boolean = false,
    /** Incrementa a cada conclusão de "carregar mais" (sucesso ou falha) — sinal p/ a UI reagir
     *  mesmo quando o tamanho da lista não muda (teto de itens atingido). */
    val loadMoreNonce: Int = 0,
    val activeChannelId: Long? = null,
    val activeChannelName: String = "",
    val enabledChannels: List<ChannelChipUi> = emptyList(),
    /** Exibir capas (pôsteres/thumbs) — toggle das Configurações. */
    val showCovers: Boolean = true,
    /** Exibir fotos do elenco na tela de detalhes — toggle das Configurações. */
    val castPhotos: Boolean = true,
    val isLoading: Boolean = false,
    /** Resolvendo o vídeo (card do índice) antes de abrir o player — feedback no botão Assistir. */
    val isOpeningVideo: Boolean = false,
    /** Falha ao resolver/abrir o vídeo — feedback de erro no botão Assistir. */
    val openVideoFailed: Boolean = false,
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
    val focusRestoreNonce: Long = 0,
    val pendingNavigation: MediaNavigationPayload? = null
)
