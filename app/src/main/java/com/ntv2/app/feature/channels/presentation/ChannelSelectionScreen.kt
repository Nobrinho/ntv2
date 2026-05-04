package com.ntv2.app.feature.channels.presentation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionEmptyState
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionUiState
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionAction
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionViewModel

@Composable
fun ChannelSelectionScreen(
    viewModel: ChannelSelectionViewModel,
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val firstActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstActionFocusRequester.requestFocus()
    }
    LaunchedEffect(state.navigateToLibrary) {
        if (state.navigateToLibrary) {
            onOpenLibrary()
            viewModel.onAction(ChannelSelectionAction.NavigationConsumed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Seleção de Canais", style = MaterialTheme.typography.headlineMedium, color = Color.White)

        ActionsRow(
            state = state,
            firstActionFocusRequester = firstActionFocusRequester,
            onSelectAll = { viewModel.onAction(ChannelSelectionAction.SelectAll) },
            onClearSelection = { viewModel.onAction(ChannelSelectionAction.ClearSelection) },
            onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
            onBack = onBack
        )

        when {
            state.isLoading -> {
                Text("Carregando canais...", color = Color.White)
            }

            state.errorMessage != null -> {
                Text("Erro: ${state.errorMessage}", color = Color.White)
                Button(onClick = { viewModel.onAction(ChannelSelectionAction.Retry) }) {
                    Text("Tentar novamente")
                }
            }

            state.emptyState == ChannelSelectionEmptyState.NoEligibleChannels -> {
                Text("Nenhum canal elegível encontrado", color = Color.White)
            }

            else -> {
                ChannelsGrid(
                    state = state,
                    onToggle = { id -> viewModel.onAction(ChannelSelectionAction.ToggleChannel(id)) }
                )
            }
        }
    }
}

@Composable
private fun ActionsRow(
    state: ChannelSelectionUiState,
    firstActionFocusRequester: FocusRequester,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.focusRequester(firstActionFocusRequester),
            onClick = onSelectAll
        ) {
            Text("Selecionar todos")
        }

        Button(onClick = onClearSelection) {
            Text("Limpar seleção")
        }

        Button(onClick = onContinue, enabled = state.canContinue) {
            Text("Continuar")
        }

        Button(onClick = onBack) {
            Text("Voltar")
        }
    }
}

@Composable
private fun ChannelsGrid(
    state: ChannelSelectionUiState,
    onToggle: (Long) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp),
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.channels, key = { it.id }) { item ->
            Card(onClick = { onToggle(item.id) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.title, maxLines = 2)
                    Text(if (item.isSelected) "Selecionado" else "Não selecionado")
                }
            }
        }
    }
}
