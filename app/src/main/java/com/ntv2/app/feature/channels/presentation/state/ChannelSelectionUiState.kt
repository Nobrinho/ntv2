package com.ntv2.app.feature.channels.presentation.state

import com.ntv2.app.feature.channels.domain.ChannelSummary

data class ChannelItemUi(
    val id: Long,
    val title: String,
    val avatarPath: String?,
    val isSelected: Boolean
)

sealed interface ChannelSelectionEmptyState {
    data object NoEligibleChannels : ChannelSelectionEmptyState
}

data class ChannelSelectionUiState(
    val channels: List<ChannelItemUi> = emptyList(),
    val selectedChannelIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emptyState: ChannelSelectionEmptyState? = null,
    val canContinue: Boolean = false,
    val navigateToLibrary: Boolean = false,
    val navigateToLogin: Boolean = false
)

fun ChannelSummary.toUi(isSelected: Boolean): ChannelItemUi {
    return ChannelItemUi(
        id = id,
        title = title,
        avatarPath = avatarPath,
        isSelected = isSelected
    )
}
