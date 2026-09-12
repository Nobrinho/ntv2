package com.ntv2.app.feature.channels.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.RailButton
import com.ntv2.app.core.ui.RailColumn
import com.ntv2.app.feature.channels.presentation.state.ChannelItemUi
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionEmptyState
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionUiState
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionAction
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionViewModel
import kotlin.math.abs

@Composable
fun ChannelSelectionScreen(
    viewModel: ChannelSelectionViewModel,
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
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
    LaunchedEffect(state.navigateToLogin) {
        if (state.navigateToLogin) {
            onLogout()
            viewModel.onAction(ChannelSelectionAction.LogoutNavigationConsumed)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Ações agora no rail lateral (igual à Biblioteca), mantendo todos os botões.
        RailColumn {
            RailButton(
                icon = Icons.Filled.DoneAll,
                label = "Todos",
                modifier = Modifier.focusRequester(firstActionFocusRequester),
                onClick = { viewModel.onAction(ChannelSelectionAction.SelectAll) }
            )
            RailButton(
                icon = Icons.Filled.Clear,
                label = "Limpar",
                onClick = { viewModel.onAction(ChannelSelectionAction.ClearSelection) }
            )
            RailButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "Continuar",
                enabled = state.canContinue,
                onClick = { viewModel.onAction(ChannelSelectionAction.Continue) }
            )
            RailButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Voltar",
                onClick = onBack
            )
            RailButton(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Sair",
                onClick = { viewModel.onAction(ChannelSelectionAction.Logout) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Seleção de Canais", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                if (state.channels.isNotEmpty()) {
                    Text(
                        "${state.selectedChannelIds.size} de ${state.channels.size} selecionados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

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
            ChannelCard(item = item, onToggle = { onToggle(item.id) })
        }
    }
}

@Composable
private fun ChannelCard(
    item: ChannelItemUi,
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val selected = item.isSelected
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .background(
                when {
                    focused -> Color(0x33FFFFFF)
                    selected -> Color(0x22000000)
                    else -> Color(0x11FFFFFF)
                }
            )
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    selected -> accent
                    else -> Color(0x44FFFFFF)
                },
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelAvatar(avatarPath = item.avatarPath, title = item.title)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (selected) "Selecionado" else "Selecionar",
                        color = if (selected) accent else Color(0xFF9A9A9A),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    avatarPath: String?,
    title: String
) {
    val avatarSize = 48.dp
    if (avatarPath != null) {
        AsyncImage(
            model = avatarPath,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
        )
    } else {
        // Sem foto: círculo colorido (estável por título) com a inicial.
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(colorForTitle(title)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialFor(title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFF42A5F5), Color(0xFFFFA726), Color(0xFF66BB6A), Color(0xFFEC407A)
)

private fun colorForTitle(title: String): Color =
    AVATAR_COLORS[abs(title.hashCode()) % AVATAR_COLORS.size]

private fun initialFor(title: String): String {
    val firstLetter = title.trim().firstOrNull { it.isLetterOrDigit() }
    return firstLetter?.uppercaseChar()?.toString() ?: "#"
}
