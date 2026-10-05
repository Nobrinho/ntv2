package com.ntv2.app.feature.channels.presentation

import com.ntv2.app.core.ui.brandBackdrop
import androidx.compose.runtime.withFrameNanos

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstActionFocusRequester = remember { FocusRequester() }
    val firstCardFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    val logoutFocus = remember { FocusRequester() }
    var confirmLogout by remember { mutableStateOf(false) }
    var logoutAsked by remember { mutableStateOf(false) }
    val hasSelection = state.selectedChannelIds.isNotEmpty()

    // Foco inicial no 1º canal (não em "Atualizar", onde um OK por engano recarregava a lista);
    // sem canais (carregando/erro), fica em Atualizar.
    LaunchedEffect(state.channels.isNotEmpty()) {
        withFrameNanos { }
        if (state.channels.isNotEmpty() && runCatching { firstCardFocus.requestFocus() }.isSuccess) {
            return@LaunchedEffect
        }
        runCatching { firstActionFocusRequester.requestFocus() }
    }
    // Cancelou o "Fechar o aplicativo?": volta ao 1º canal (ou Atualizar, sem canais).
    val restoreSignal = com.ntv2.app.core.ui.LocalFocusRestoreSignal.current
    LaunchedEffect(restoreSignal) {
        if (restoreSignal == 0 || confirmLogout) return@LaunchedEffect
        withFrameNanos { }
        if (state.channels.isEmpty() || runCatching { firstCardFocus.requestFocus() }.isFailure) {
            runCatching { firstActionFocusRequester.requestFocus() }
        }
    }
    // Cancelou "Sair da conta?": devolve o foco ao botão Sair.
    LaunchedEffect(confirmLogout) {
        if (!confirmLogout && logoutAsked) {
            withFrameNanos { }
            runCatching { logoutFocus.requestFocus() }
        }
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
    FocusGlideScope(Modifier.fillMaxSize()) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(com.ntv2.app.core.ui.BrandColors.Background)
            .brandBackdrop(com.ntv2.app.core.ui.BrandBackdropKind.Gradient)
    ) {
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
        if (useTvLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                ChannelActionsRail(
                    state = state,
                    firstActionFocusRequester = firstActionFocusRequester,
                    continueFocus = continueFocus,
                    logoutFocus = logoutFocus,
                    showBack = showBack,
                    hasSelection = hasSelection,
                    onClear = {
                        // "Limpar" some ao limpar a seleção: move o foco antes para não perdê-lo.
                        runCatching { firstActionFocusRequester.requestFocus() }
                        viewModel.onAction(ChannelSelectionAction.ClearSelection)
                    },
                    onRefresh = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
                    onBack = onBack,
                    onLogout = { logoutAsked = true; confirmLogout = true }
                )
                ChannelContent(
                    state = state,
                    compact = false,
                    firstCardFocus = firstCardFocus,
                    onRetry = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onToggle = { id -> viewModel.onAction(ChannelSelectionAction.ToggleChannel(id)) }
                )
            }
        } else {
            // Celular: ações na barra inferior (navbottom); atualizar = puxar a lista para baixo.
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.onAction(ChannelSelectionAction.Retry) },
                modifier = Modifier.fillMaxSize().statusBarsPadding()
            ) {
                ChannelContent(
                    state = state,
                    compact = true,
                    onRetry = { viewModel.onAction(ChannelSelectionAction.Retry) },
                    onToggle = { id -> viewModel.onAction(ChannelSelectionAction.ToggleChannel(id)) }
                )
            }
            ChannelBottomBar(
                state = state,
                logoutFocusRequester = firstActionFocusRequester,
                showBack = showBack,
                hasSelection = hasSelection,
                onClear = { viewModel.onAction(ChannelSelectionAction.ClearSelection) },
                onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
                onBack = onBack,
                onLogout = { confirmLogout = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
}

@Composable
private fun ChannelActionsRail(
    state: ChannelSelectionUiState,
    firstActionFocusRequester: FocusRequester,
    continueFocus: FocusRequester,
    logoutFocus: FocusRequester,
    showBack: Boolean,
    hasSelection: Boolean,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    // Vindo da grade (←) com canais escolhidos, o foco entra direto em "Continuar".
    RailColumn(enterFocus = { if (state.canContinue) continueFocus else null }) {
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
            modifier = Modifier.focusRequester(continueFocus),
            onClick = onContinue
        )
        if (showBack) {
            RailButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onClick = onBack)
        }
        RailButton(
            Icons.AutoMirrored.Filled.Logout,
            "Sair",
            modifier = Modifier.focusRequester(logoutFocus),
            onClick = onLogout
        )
    }
}

@Composable
private fun ChannelBottomBar(
    state: ChannelSelectionUiState,
    logoutFocusRequester: FocusRequester,
    showBack: Boolean,
    hasSelection: Boolean,
    onClear: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(com.ntv2.app.core.ui.BrandColors.Background.copy(alpha = 0.95f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            ChannelNavItem(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", Modifier.weight(1f), onClick = onBack)
        }
        if (hasSelection) {
            ChannelNavItem(Icons.Filled.Deselect, "Limpar", Modifier.weight(1f), onClick = onClear)
        }
        ChannelNavItem(
            Icons.AutoMirrored.Filled.Logout, "Sair",
            Modifier.weight(1f).focusRequester(logoutFocusRequester), onClick = onLogout
        )
        ChannelNavItem(
            Icons.AutoMirrored.Filled.ArrowForward, "Continuar",
            Modifier.weight(if (showBack || hasSelection) 2f else 3f),
            enabled = state.canContinue, primary = state.canContinue, showLabel = true, onClick = onContinue
        )
    }
}

// Item da barra inferior: ícone + rótulo, raio 4dp; "Continuar" em azul NBR quando habilitado.
@Composable
private fun ChannelNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    showLabel: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        primary -> com.ntv2.app.core.ui.BrandColors.Accent
        focused -> Color(0x33FFFFFF)
        else -> Color(0x14FFFFFF)
    }
    val fg = when {
        !enabled -> Color(0x66FFFFFF)
        primary -> com.ntv2.app.core.ui.BrandColors.OnCta
        else -> Color.White
    }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) com.ntv2.app.core.ui.BrandColors.Accent else Color(0x1FFFFFFF),
                RoundedCornerShape(4.dp)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        if (showLabel) Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
            .then(if (enabled) Modifier.glideTarget(4.dp) else Modifier)
            .clip(RoundedCornerShape(4.dp))
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
    firstCardFocus: FocusRequester? = null,
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
                    color = com.ntv2.app.core.ui.BrandColors.TextSecondary
                )
            }
        }

        when {
            state.isLoading -> ChannelsGridSkeleton(compact = compact)
            state.errorMessage != null -> {
                Text("Erro: ${state.errorMessage}", color = Color.White)
                BrandButton(onClick = onRetry) { BrandButtonLabel("Tentar novamente") }
            }
            state.emptyState == ChannelSelectionEmptyState.NoEligibleChannels -> {
                Text("Nenhum canal elegível encontrado", color = Color.White)
            }
            else -> ChannelsGrid(state = state, compact = compact, firstCardFocus = firstCardFocus, onToggle = onToggle)
        }
    }
}

@Composable
private fun ChannelsGrid(
    state: ChannelSelectionUiState,
    compact: Boolean,
    firstCardFocus: FocusRequester? = null,
    onToggle: (Long) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = if (compact) GridCells.Adaptive(180.dp) else GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.channels, key = { it.id }) { item ->
            val first = item.id == state.channels.first().id
            ChannelCard(
                item = item,
                modifier = if (first && firstCardFocus != null) Modifier.focusRequester(firstCardFocus) else Modifier,
                onToggle = { onToggle(item.id) }
            )
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
                            .clip(RoundedCornerShape(6.dp))
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
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val selected = item.isSelected
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glideTarget(6.dp)
            .clip(RoundedCornerShape(6.dp))
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
                width = if ((focused && !gliding) || selected) 2.dp else 1.dp,
                color = when {
                    focused && !gliding -> Color.White
                    selected -> accent
                    else -> Color(0x44FFFFFF)
                },
                shape = RoundedCornerShape(6.dp)
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
                        color = if (selected) accent else Color(0xFF8E98A8),
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
