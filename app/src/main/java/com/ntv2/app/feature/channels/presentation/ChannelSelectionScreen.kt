package com.ntv2.app.feature.channels.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Refresh
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
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
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
    onLogout: () -> Unit,
    // "Voltar" só faz sentido quando esta tela foi aberta a partir das Configurações.
    // No primeiro login (raiz), não há para onde voltar, então o botão é ocultado.
    showBack: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val firstActionFocusRequester = remember { FocusRequester() }
    var confirmLogout by remember { mutableStateOf(false) }
    val hasSelection = state.selectedChannelIds.isNotEmpty()

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

    val adaptive = rememberAdaptiveLayoutInfo()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
        if (useTvLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                ChannelActionsRail(
                    state = state,
                    firstActionFocusRequester = firstActionFocusRequester,
                    showBack = showBack,
                    hasSelection = hasSelection,
                    onClear = { viewModel.onAction(ChannelSelectionAction.ClearSelection) },
                    onRefresh = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
                    onBack = onBack,
                    onLogout = { confirmLogout = true }
                )
                ChannelContent(
                    state = state,
                    compact = false,
                    onRetry = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onToggle = { id -> viewModel.onAction(ChannelSelectionAction.ToggleChannel(id)) }
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CompactChannelActions(
                    state = state,
                    firstActionFocusRequester = firstActionFocusRequester,
                    showBack = showBack,
                    hasSelection = hasSelection,
                    onClear = { viewModel.onAction(ChannelSelectionAction.ClearSelection) },
                    onRefresh = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
                    onBack = onBack,
                    onLogout = { confirmLogout = true }
                )
                ChannelContent(
                    state = state,
                    compact = true,
                    onRetry = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onToggle = { id -> viewModel.onAction(ChannelSelectionAction.ToggleChannel(id)) }
                )
            }
            // Sem bottom nav aqui: ainda não há biblioteca (nenhum canal escolhido); as ações do
            // topo (Continuar/Atualizar/Limpar/Sair e Voltar quando aplicável) já bastam.
        }

        if (confirmLogout) {
            ConfirmDialog(
                title = "Sair da conta?",
                message = "Você precisará entrar novamente para usar o app.",
                icon = Icons.AutoMirrored.Filled.Logout,
                confirmLabel = "Sair",
                confirmIcon = Icons.AutoMirrored.Filled.Logout,
                cancelLabel = "Cancelar",
                cancelIcon = Icons.Filled.Close,
                destructive = true,
                onConfirm = { confirmLogout = false; onLogout() },
                onDismiss = { confirmLogout = false }
            )
        }
    }
}

@Composable
private fun ChannelActionsRail(
    state: ChannelSelectionUiState,
    firstActionFocusRequester: FocusRequester,
    showBack: Boolean,
    hasSelection: Boolean,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    RailColumn {
        // Foco inicial na Atualizar (sempre presente); Limpar só aparece com seleção.
        RailButton(
            icon = Icons.Filled.Refresh,
            label = "Atualizar",
            modifier = Modifier.focusRequester(firstActionFocusRequester),
            onClick = onRefresh
        )
        if (hasSelection) {
            RailButton(Icons.Filled.Deselect, "Limpar", onClick = onClear)
        }
        RailButton(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = "Continuar",
            enabled = state.canContinue,
            primary = state.canContinue,
            onClick = onContinue
        )
        if (showBack) {
            RailButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onClick = onBack)
        }
        RailButton(Icons.AutoMirrored.Filled.Logout, "Sair", onClick = onLogout)
    }
}

@Composable
private fun CompactChannelActions(
    state: ChannelSelectionUiState,
    firstActionFocusRequester: FocusRequester,
    showBack: Boolean,
    hasSelection: Boolean,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .horizontalScroll(rememberScrollState())
            .background(Color(0xFF0C0C0C))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Atualizar e Limpar são só ícone. Limpar só aparece com pelo menos um canal selecionado.
        ChannelIconAction(
            icon = Icons.Filled.Refresh,
            contentDescription = "Atualizar",
            modifier = Modifier.focusRequester(firstActionFocusRequester),
            onClick = onRefresh
        )
        if (hasSelection) {
            ChannelIconAction(
                icon = Icons.Filled.Deselect,
                contentDescription = "Limpar seleção",
                onClick = onClear
            )
        }
        CompactActionChip(label = "Continuar", enabled = state.canContinue, primary = state.canContinue, onClick = onContinue)
        if (showBack) {
            CompactActionChip(label = "Voltar", onClick = onBack)
        }
        CompactActionChip(label = "Sair", onClick = onLogout)
    }
}

// Ação circular só-ícone (contraste + destaque de foco) para a barra de canais no celular.
@Composable
private fun ChannelIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0xFF2C2C2E))
            .border(1.dp, if (focused) Color.White else Color(0x66FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CompactActionChip(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        !enabled -> Color(0x14FFFFFF)
        primary -> MaterialTheme.colorScheme.primary
        else -> Color(0x22FFFFFF)
    }
    val content = if (primary && enabled) Color.Black else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) content else Color(0x66FFFFFF), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ChannelContent(
    state: ChannelSelectionUiState,
    compact: Boolean,
    onRetry: () -> Unit,
    onToggle: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (compact) Modifier.padding(bottom = 76.dp) else Modifier)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Seleção de Canais", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            if (state.channels.isNotEmpty()) {
                Text(
                    "${state.selectedChannelIds.size} de ${state.channels.size} selecionados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0)
                )
            }
        }

        when {
            state.isLoading -> ChannelsGridSkeleton(compact = compact)
            state.errorMessage != null -> {
                Text("Erro: ${state.errorMessage}", color = Color.White)
                Button(onClick = onRetry) { Text("Tentar novamente") }
            }
            state.emptyState == ChannelSelectionEmptyState.NoEligibleChannels -> {
                Text("Nenhum canal elegível encontrado", color = Color.White)
            }
            else -> ChannelsGrid(state = state, compact = compact, onToggle = onToggle)
        }
    }
}

@Composable
private fun ChannelsGrid(
    state: ChannelSelectionUiState,
    compact: Boolean,
    onToggle: (Long) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = if (compact) GridCells.Adaptive(180.dp) else GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.channels, key = { it.id }) { item ->
            ChannelCard(item = item, onToggle = { onToggle(item.id) })
        }
    }
}

// Skeleton da grade de canais: placeholders no mesmo formato do card (avatar + 2 linhas), com
// pulse suave — evita a legenda "Carregando canais...".
@Composable
private fun ChannelsGridSkeleton(compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "channels-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "channels-skeleton-alpha"
    )
    val shimmer = Color.White.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(if (compact) 8 else 6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(if (compact) 1 else 4) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(shimmer)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmer)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmer)
                            )
                        }
                    }
                }
            }
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
