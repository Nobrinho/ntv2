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
    val fileId: Int
)

data class ChannelMediaSectionUi(
    val channelId: Long,
    val channelName: String,
    val items: List<MediaCardUi>
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

data class MediaLibraryUiState(
    val searchQuery: String = "",
    val minDurationMinutes: Int = 15,
    val sections: List<ChannelMediaSectionUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emptyState: MediaLibraryEmptyState? = null,
    val lastFocusedMediaId: String? = null,
    val focusRestoreNonce: Long = 0,
    val pendingNavigation: MediaNavigationPayload? = null
)
