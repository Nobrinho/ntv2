package com.ntv2.app.feature.channels.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.auth.domain.AuthRepository
import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelSummary
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionEmptyState
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionUiState
import com.ntv2.app.feature.channels.presentation.state.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

sealed interface ChannelSelectionAction {
    data object Load : ChannelSelectionAction
    data class ToggleChannel(val channelId: Long) : ChannelSelectionAction
    data object SelectAll : ChannelSelectionAction
    data object ClearSelection : ChannelSelectionAction
    data object Retry : ChannelSelectionAction
    data object Continue : ChannelSelectionAction
    data object NavigationConsumed : ChannelSelectionAction
    data object Logout : ChannelSelectionAction
    data object LogoutNavigationConsumed : ChannelSelectionAction
}

class ChannelSelectionViewModel(
    private val channelRepository: ChannelRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelSelectionUiState(isLoading = true))
    val uiState: StateFlow<ChannelSelectionUiState> = _uiState.asStateFlow()

    private var allChannels: List<ChannelSummary> = emptyList()

    init {
        observePersistedSelection()
        onAction(ChannelSelectionAction.Load)
    }

    fun onAction(action: ChannelSelectionAction): Boolean {
        return when (action) {
            ChannelSelectionAction.Load,
            ChannelSelectionAction.Retry -> {
                loadChannels()
                false
            }

            is ChannelSelectionAction.ToggleChannel -> {
                toggleChannel(action.channelId)
                false
            }

            ChannelSelectionAction.SelectAll -> {
                selectAll()
                false
            }

            ChannelSelectionAction.ClearSelection -> {
                clearSelectionLocal()
                false
            }

            ChannelSelectionAction.Continue -> persistAndContinue()
            ChannelSelectionAction.NavigationConsumed -> {
                _uiState.update { it.copy(navigateToLibrary = false) }
                false
            }

            ChannelSelectionAction.Logout -> {
                logout()
                false
            }

            ChannelSelectionAction.LogoutNavigationConsumed -> {
                _uiState.update { it.copy(navigateToLogin = false) }
                false
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { authRepository.logout() }
            _uiState.update { it.copy(isLoading = false, navigateToLogin = true) }
        }
    }

    private fun observePersistedSelection() {
        viewModelScope.launch {
            channelRepository.observeSelectedChannelIds().collect { selectedIds ->
                _uiState.update { current ->
                    val merged = if (current.selectedChannelIds.isEmpty()) {
                        selectedIds
                    } else {
                        current.selectedChannelIds
                    }
                    current.copy(
                        selectedChannelIds = merged,
                        channels = allChannels.map { it.toUi(merged.contains(it.id)) },
                        canContinue = merged.isNotEmpty()
                    )
                }
            }
        }
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, emptyState = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    channelRepository.fetchEligibleChannels()
                }
            }.onSuccess { channels ->
                allChannels = channels
                val selected = _uiState.value.selectedChannelIds
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        channels = channels.map { c -> c.toUi(selected.contains(c.id)) },
                        emptyState = if (channels.isEmpty()) ChannelSelectionEmptyState.NoEligibleChannels else null,
                        canContinue = selected.isNotEmpty()
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Falha ao carregar canais"
                    )
                }
            }
        }
    }

    private fun toggleChannel(channelId: Long) {
        _uiState.update { current ->
            val updated = current.selectedChannelIds.toMutableSet().apply {
                if (contains(channelId)) remove(channelId) else add(channelId)
            }
            current.copy(
                selectedChannelIds = updated,
                channels = allChannels.map { it.toUi(updated.contains(it.id)) },
                canContinue = updated.isNotEmpty()
            )
        }
    }

    private fun selectAll() {
        val allIds = allChannels.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                selectedChannelIds = allIds,
                channels = allChannels.map { channel -> channel.toUi(true) },
                canContinue = allIds.isNotEmpty()
            )
        }
    }

    private fun clearSelectionLocal() {
        _uiState.update {
            it.copy(
                selectedChannelIds = emptySet(),
                channels = allChannels.map { channel -> channel.toUi(false) },
                canContinue = false
            )
        }
    }

    private fun persistAndContinue(): Boolean {
        val selectedIds = _uiState.value.selectedChannelIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Selecione ao menos um canal") }
            return false
        }

        viewModelScope.launch {
            val selectedChannels = allChannels.filter { selectedIds.contains(it.id) }
            runCatching {
                channelRepository.persistSelectedChannels(selectedChannels)
            }.onSuccess {
                _uiState.update { it.copy(navigateToLibrary = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Falha ao salvar seleção")
                }
            }
        }
        return false
    }
}

class ChannelSelectionViewModelFactory(
    private val channelRepository: ChannelRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelSelectionViewModel::class.java)) {
            return ChannelSelectionViewModel(channelRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
