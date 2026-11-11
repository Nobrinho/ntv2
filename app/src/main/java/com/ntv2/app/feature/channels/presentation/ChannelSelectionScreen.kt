package com.ntv2.app.feature.channels.presentation

import com.ntv2.app.core.ui.focusEnterTo
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandBackdropKind
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.brandBackdrop
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionEmptyState
import com.ntv2.app.feature.channels.presentation.state.ChannelSelectionUiState
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionAction
import com.ntv2.app.feature.channels.presentation.viewmodel.ChannelSelectionViewModel

/*
 * Seleção de canais do protótipo: 09 do celular (CVoga) e TV 06 (Rzr7o).
 * Celular: título + atualizar no topo, lista de canais em linha, rodapé com contagem · Limpar e "Continuar".
 * TV: título + "Atualizar" no topo, grade de 4 cartões, ações "Continuar" / "Limpar" embaixo.
 * "Voltar" e "Sair", que o protótipo não desenha, continuam: no topo (celular) e junto do "Atualizar" (TV).
 */

private const val SUBTITLE = "Escolha os canais que formam o seu catálogo."

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
            .background(BrandColors.Background)
            .brandBackdrop(BrandBackdropKind.Gradient)
    ) {
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
        val onToggle: (Long) -> Unit = { id -> viewModel.onAction(ChannelSelectionAction.ToggleChannel(id)) }
        val onRetry: () -> Unit = { viewModel.onAction(ChannelSelectionAction.Retry) }
        if (useTvLayout) {
            ChannelSelectionTv(
                state = state,
                showBack = showBack,
                hasSelection = hasSelection,
                refreshFocus = firstActionFocusRequester,
                continueFocus = continueFocus,
                logoutFocus = logoutFocus,
                firstCardFocus = firstCardFocus,
                onRefresh = onRetry,
                onToggle = onToggle,
                onClear = {
                    // "Limpar" some ao limpar a seleção: move o foco antes para não perdê-lo.
                    runCatching { firstActionFocusRequester.requestFocus() }
                    viewModel.onAction(ChannelSelectionAction.ClearSelection)
                },
                onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
                onBack = onBack,
                onLogout = { logoutAsked = true; confirmLogout = true }
            )
        } else {
            ChannelSelectionPhone(
                state = state,
                showBack = showBack,
                hasSelection = hasSelection,
                logoutFocus = firstActionFocusRequester,
                onRefresh = onRetry,
                onToggle = onToggle,
                onClear = { viewModel.onAction(ChannelSelectionAction.ClearSelection) },
                onContinue = { viewModel.onAction(ChannelSelectionAction.Continue) },
                onBack = onBack,
                onLogout = { confirmLogout = true }
            )
        }

        if (confirmLogout) {
            ConfirmDialog(
                title = "Sair da conta?",
                message = "Você precisará entrar novamente para usar o app.",
                icon = NbrIcons.LogOut,
                confirmLabel = "Sair",
                confirmIcon = NbrIcons.LogOut,
                cancelLabel = "Cancelar",
                cancelIcon = NbrIcons.X,
                destructive = true,
                onConfirm = { confirmLogout = false; onLogout() },
                onDismiss = { confirmLogout = false }
            )
        }
    }
    }
}

/** 09 do celular: conteúdo com padding 0/24/24/24 e gap 16; atualizar também por puxar a lista. */
@Composable
private fun ChannelSelectionPhone(
    state: ChannelSelectionUiState,
    showBack: Boolean,
    hasSelection: Boolean,
    logoutFocus: FocusRequester,
    onRefresh: () -> Unit,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Topo (34 dp): [voltar] título 28/700 · atualizar 22 accent · sair.
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                PhoneTopIcon(NbrIcons.ChevronLeft, "Voltar", ChannelText, onClick = onBack)
            }
            Text(
                "Seleção de Canais",
                style = NbrType.ScreenTitle,
                color = ChannelText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            PhoneTopIcon(NbrIcons.RefreshCw, "Atualizar", BrandColors.Accent, onClick = onRefresh)
            PhoneTopIcon(
                NbrIcons.LogOut, "Sair", BrandColors.TextMuted,
                modifier = Modifier.focusRequester(logoutFocus), onClick = onLogout
            )
        }
        Text(SUBTITLE, style = NbrType.BodyLarge, color = BrandColors.TextSecondary)

        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                state.isLoading -> ChannelSkeleton(tv = false)
                state.errorMessage != null -> ChannelMessage("Erro: ${state.errorMessage}", tv = false, onRetry = onRefresh)
                state.emptyState == ChannelSelectionEmptyState.NoEligibleChannels ->
                    ChannelMessage("Nenhum canal elegível encontrado", tv = false)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.channels, key = { it.id }) { item ->
                        ChannelRowCard(
                            title = item.title,
                            avatarPath = item.avatarPath,
                            selected = item.isSelected,
                            onClick = { onToggle(item.id) }
                        )
                    }
                }
            }
        }

        // Rodapé (gap 12): "2 de 4 canais selecionados · Limpar" 13/400 muted + "Continuar".
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.channels.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countStyle = NbrType.Secondary
                    Text(
                        selectedCountLabel(state.selectedChannelIds.size, state.channels.size),
                        style = countStyle,
                        color = BrandColors.TextMuted
                    )
                    if (hasSelection) {
                        Text(" · ", style = countStyle, color = BrandColors.TextMuted)
                        Text(
                            "Limpar",
                            style = countStyle,
                            color = BrandColors.TextMuted,
                            modifier = Modifier.clickable(onClick = onClear)
                        )
                    }
                }
            }
            BrandButton(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth()
            ) { BrandButtonLabel("Continuar") }
        }
    }
}

/** Ícone de 22 do topo do celular, com área de toque circular de 32. */
@Composable
private fun PhoneTopIcon(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** TV 06: padding 36/48, gap 16; grade de 4 com gap 14; ações embaixo com gap 10. */
@Composable
private fun ChannelSelectionTv(
    state: ChannelSelectionUiState,
    showBack: Boolean,
    hasSelection: Boolean,
    refreshFocus: FocusRequester,
    continueFocus: FocusRequester,
    logoutFocus: FocusRequester,
    firstCardFocus: FocusRequester,
    onRefresh: () -> Unit,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Topo: título 32/800 + secundários (Voltar, Sair, Atualizar 120).
        // Entrando na linha pelo D-pad (↑ da grade), o foco vai para o "Voltar" — nunca direto no
        // "Sair", que fica acima da 1ª coluna e desconectaria a conta num OK sem olhar.
        val backFocus = remember { FocusRequester() }
        Row(
            Modifier
                .fillMaxWidth()
                .focusEnterTo { if (showBack) backFocus else refreshFocus }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Seleção de Canais",
                style = NbrType.Hero.copy(fontSize = 32.sp, lineHeight = 38.7.sp, fontWeight = FontWeight.ExtraBold),
                color = ChannelText,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showBack) {
                TvSecondaryButton("Voltar", modifier = Modifier.focusRequester(backFocus), onClick = onBack)
            }
            TvSecondaryButton("Sair", modifier = Modifier.focusRequester(logoutFocus), onClick = onLogout)
            TvSecondaryButton(
                "Atualizar",
                modifier = Modifier.width(120.dp).focusRequester(refreshFocus),
                onClick = onRefresh
            )
        }
        Text(SUBTITLE, style = NbrType.Body, color = BrandColors.TextSecondary)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> ChannelSkeleton(tv = true)
                state.errorMessage != null -> ChannelMessage("Erro: ${state.errorMessage}", tv = true, onRetry = onRefresh)
                state.emptyState == ChannelSelectionEmptyState.NoEligibleChannels ->
                    ChannelMessage("Nenhum canal elegível encontrado", tv = true)
                else -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    // Folga para o anel de foco não ser cortado na borda da grade.
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(state.channels, key = { it.id }) { item ->
                        val first = item.id == state.channels.first().id
                        ChannelGridCard(
                            title = item.title,
                            avatarPath = item.avatarPath,
                            selected = item.isSelected,
                            modifier = if (first) Modifier.focusRequester(firstCardFocus) else Modifier,
                            onClick = { onToggle(item.id) }
                        )
                    }
                }
            }
        }

        // Ações: "Continuar" 180 + "Limpar" 110 + contagem 12/400 muted.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandButton(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.width(180.dp).focusRequester(continueFocus)
            ) { BrandButtonLabel("Continuar") }
            if (hasSelection) {
                TvSecondaryButton("Limpar", modifier = Modifier.width(110.dp), onClick = onClear)
            }
            if (state.channels.isNotEmpty()) {
                Text(
                    selectedCountLabel(state.selectedChannelIds.size, state.channels.size),
                    style = NbrType.Caption,
                    color = BrandColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun TvSecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BrandButton(onClick = onClick, style = BrandButtonStyle.Secondary, modifier = modifier) {
        BrandButtonLabel(label)
    }
}

/** Erro ou lista vazia: texto text-2 centralizado e, no erro, "Tentar novamente". */
@Composable
private fun ChannelMessage(text: String, tv: Boolean, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(if (tv) 360.dp else 280.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text, style = NbrType.Body, color = BrandColors.TextSecondary, textAlign = TextAlign.Center)
            if (onRetry != null) {
                BrandButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    BrandButtonLabel("Tentar novamente")
                }
            }
        }
    }
}
