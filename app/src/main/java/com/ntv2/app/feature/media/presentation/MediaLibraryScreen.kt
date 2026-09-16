package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.runtime.DisposableEffect
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.MainBottomNav
import com.ntv2.app.core.ui.MainTab
import com.ntv2.app.core.ui.NavRail
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.media.presentation.state.ChannelChipUi
import com.ntv2.app.feature.media.domain.MovieDetails
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryAction
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaLibraryScreen(
    viewModel: MediaLibraryViewModel,
    openChannelPickerRequest: Int,
    lowRamPlaybackWarnings: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPlaybackPlaceholder: (
        mediaId: String,
        fileId: Int,
        title: String,
        channelName: String,
        durationSeconds: Int,
        fileName: String?,
        thumbnailPath: String?
    ) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialActionsFocus = remember { FocusRequester() }
    val cardFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    // Teclado de busca próprio (D-pad) e picker de canal ativo — overlays na tela.
    var searching by remember { mutableStateOf(false) }
    var channelPicker by remember { mutableStateOf(false) }
    // Card selecionado para a tela de Detalhes (overlay estilo Netflix/Prime).
    var detailsMedia by remember { mutableStateOf<MediaCardUi?>(null) }
    // Controle de foco do "Carregar mais": ao clicar, o card sai da árvore quando os itens chegam
    // e o foco se perde (direcional depois "pula" pro último). Movemos o foco de forma explícita.
    val loadMoreFocus = remember { FocusRequester() }
    var loadMoreRequested by remember { mutableStateOf(false) }
    // Fronteira antes do load: guardamos o último item conhecido para achar o 1º novo por
    // identidade (robusto ao corte do topo pelo teto de itens).
    var lastIdBeforeLoad by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyStaggeredGridState()
    val adaptive = rememberAdaptiveLayoutInfo()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(MediaLibraryAction.ScreenResumed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Foca a barra de busca ao entrar e ao fechar o teclado (não abre teclado sozinho).
    LaunchedEffect(searching) {
        if (!searching) initialActionsFocus.requestFocus()
    }

    LaunchedEffect(openChannelPickerRequest) {
        if (openChannelPickerRequest > 0) {
            channelPicker = true
        }
    }

    LaunchedEffect(state.pendingNavigation) {
        val payload = state.pendingNavigation ?: return@LaunchedEffect
        onOpenPlaybackPlaceholder(
            payload.mediaId,
            payload.fileId,
            payload.title,
            payload.channelName,
            payload.durationSeconds,
            payload.fileName,
            payload.thumbnailPath
        )
        viewModel.onAction(MediaLibraryAction.ConsumeNavigation)
    }

    LaunchedEffect(state.focusRestoreNonce, state.lastFocusedMediaId) {
        val mediaId = state.lastFocusedMediaId ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.mediaId == mediaId }
        if (index >= 0) {
            gridState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
        }
        cardFocusRequesters[mediaId]?.requestFocus()
    }

    LaunchedEffect(state.returnToDetailsMediaId, state.items) {
        val mediaId = state.returnToDetailsMediaId ?: return@LaunchedEffect
        val media = state.items.firstOrNull { it.mediaId == mediaId } ?: return@LaunchedEffect
        detailsMedia = media
        viewModel.onAction(MediaLibraryAction.ConsumeReturnToDetails)
    }

    // Após "Carregar mais": leva a fronteira (1º item novo, achado por identidade) à vista e foca.
    // No grid lazy o item novo pode ainda não estar composto — por isso o scrollToItem antes do
    // requestFocus (ele também reancora a viewport na fronteira, mostrando o conteúdo novo).
    LaunchedEffect(state.loadMoreNonce) {
        if (!loadMoreRequested) return@LaunchedEffect
        val lastOldIndex = state.items.indexOfFirst { it.mediaId == lastIdBeforeLoad }
        val firstNew = if (lastOldIndex >= 0) state.items.getOrNull(lastOldIndex + 1) else null
        when {
            firstNew != null -> {
                gridState.scrollToItem(lastOldIndex + 1)
                withFrameNanos { }
                withFrameNanos { }
                runCatching { cardFocusRequesters[firstNew.mediaId]?.requestFocus() }
                loadMoreRequested = false
            }
            // Sem itens novos e sem mais páginas: foca o último item existente.
            !state.hasMore -> {
                runCatching { state.items.lastOrNull()?.mediaId?.let { cardFocusRequesters[it]?.requestFocus() } }
                loadMoreRequested = false
            }
            // Sem novos mas ainda há botão: mantém o foco no próprio "Carregar mais".
            else -> {
                runCatching { loadMoreFocus.requestFocus() }
                loadMoreRequested = false
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTvLayout = adaptive.useTvLayout && maxWidth >= 720.dp
        Row(modifier = Modifier.fillMaxSize()) {
            // Rail lateral de navegação (logo + ações), estilo TV.
            if (useTvLayout) {
                NavRail(
                    firstItemFocus = initialActionsFocus,
                    searchActive = false,
                    showClearFilter = false,
                    onSearch = { searching = true },
                    onClearFilter = { viewModel.onAction(MediaLibraryAction.SearchChanged("")) },
                    onChannels = { channelPicker = true },
                    onRefresh = { viewModel.onAction(MediaLibraryAction.Refresh) },
                    onSettings = onOpenSettings
                )
            }

            // Conteúdo do canal ativo (grade plana de aspecto misto).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (useTvLayout) 24.dp else 14.dp,
                        vertical = if (useTvLayout) 20.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!useTvLayout) {
                    CompactLibraryActions(
                        firstItemFocus = initialActionsFocus,
                        onSearch = { searching = true },
                        onRefresh = { viewModel.onAction(MediaLibraryAction.Refresh) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        state.activeChannelName.ifBlank { "Biblioteca" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                when {
                    state.isLoading -> MediaGridSkeleton(
                        showCovers = state.showCovers,
                        modifier = Modifier.fillMaxSize()
                    )

                    state.errorMessage != null -> {
                        Text("Erro: ${state.errorMessage}", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.onAction(MediaLibraryAction.Refresh) }) {
                                Text("Tentar novamente")
                            }
                            Button(onClick = { viewModel.onAction(MediaLibraryAction.ClearError) }) {
                                Text("Fechar")
                            }
                        }
                    }

                    state.emptyState != null -> {
                        val message = when (state.emptyState) {
                            MediaLibraryEmptyState.NoChannelsSelected -> "Nenhum canal selecionado (use Configurações)"
                            MediaLibraryEmptyState.NoVideosFound -> "Nenhum vídeo neste canal para o filtro atual"
                            MediaLibraryEmptyState.NoSearchResults -> "Nenhum resultado para a busca"
                            null -> ""
                        }
                        Text(message, color = Color.White)
                    }

                    else -> {
                        LazyMediaGrid(
                            items = state.items,
                            showCovers = state.showCovers,
                            lowRamPlaybackWarnings = lowRamPlaybackWarnings,
                            hasMore = state.hasMore,
                            loadingMore = loadMoreRequested,
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (useTvLayout) Modifier else Modifier.padding(bottom = 76.dp)),
                            focusRequesterFor = { id ->
                                cardFocusRequesters.getOrPut(id) { FocusRequester() }
                            },
                            onCardFocused = { id ->
                                viewModel.onAction(MediaLibraryAction.VideoFocused(id))
                            },
                            onCardClick = { media -> detailsMedia = media },
                            loadMoreFocus = loadMoreFocus,
                            onLoadMore = {
                                lastIdBeforeLoad = state.items.lastOrNull()?.mediaId
                                loadMoreRequested = true
                                viewModel.onAction(MediaLibraryAction.LoadMore)
                            }
                        )
                    }
                }
            }
        }

        if (!useTvLayout) {
            MainBottomNav(
                selected = MainTab.Library,
                onLibrary = {},
                onChannels = { channelPicker = true },
                onSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        detailsMedia?.let { media ->
            MovieDetailsOverlay(
                media = media,
                details = viewModel.detailsFor(media.mediaId),
                showCastPhotos = state.castPhotos,
                lowRamPlaybackWarnings = lowRamPlaybackWarnings,
                onPlay = {
                    viewModel.onAction(MediaLibraryAction.OpenVideo(media))
                    detailsMedia = null
                },
                onDismiss = { detailsMedia = null }
            )
        }

        if (channelPicker) {
            ChannelPickerOverlay(
                channels = state.enabledChannels,
                activeId = state.activeChannelId,
                onSelect = { id ->
                    viewModel.onAction(MediaLibraryAction.SelectActiveChannel(id))
                    channelPicker = false
                },
                onDismiss = { channelPicker = false }
            )
        }

        if (searching) {
            val searchInProgress = state.isSearchPending || state.isSearchLoading
            val resultCount = if (state.searchQuery.isBlank() || searchInProgress) 0 else state.searchResults.size
            if (useTvLayout) {
                TvSearchOverlay(
                    query = state.searchQuery,
                    resultCount = resultCount,
                    searchInProgress = searchInProgress,
                    onKey = { c ->
                        viewModel.onAction(MediaLibraryAction.SearchChanged(state.searchQuery + c))
                    },
                    onBackspace = {
                        val q = state.searchQuery
                        if (q.isNotEmpty()) {
                            viewModel.onAction(MediaLibraryAction.SearchChanged(q.dropLast(1)))
                        }
                    },
                    onClear = { viewModel.onAction(MediaLibraryAction.SearchChanged("")) },
                    onClose = {
                        searching = false
                        viewModel.onAction(MediaLibraryAction.SearchChanged(""))
                    }
                )
            } else {
                TouchSearchOverlay(
                    query = state.searchQuery,
                    resultCount = resultCount,
                    searchInProgress = searchInProgress,
                    suggestions = if (state.searchQuery.isBlank() || searchInProgress) emptyList() else state.searchResults,
                    onQueryChange = { viewModel.onAction(MediaLibraryAction.SearchChanged(it)) },
                    onClear = { viewModel.onAction(MediaLibraryAction.SearchChanged("")) },
                    onClose = {
                        searching = false
                        viewModel.onAction(MediaLibraryAction.SearchChanged(""))
                    },
                    onSelect = { media ->
                        detailsMedia = media
                        searching = false
                        viewModel.onAction(MediaLibraryAction.SearchChanged(""))
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactLibraryActions(
    firstItemFocus: FocusRequester,
    onSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactLibraryChip(modifier = Modifier.focusRequester(firstItemFocus), onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Busca")
        }
        CompactLibraryChip(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Atualizar")
        }
    }
}

@Composable
private fun CompactLibraryChip(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(color = Color.White)) {
            content()
        }
    }
}

/** Formata a duração em horas e minutos (ex.: 71 min → "1h11min"; 45 min → "45min"). */
private fun durationLabel(durationSeconds: Int): String {
    val totalMinutes = durationSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h${minutes}min"
        hours > 0 -> "${hours}h"
        else -> "${minutes}min"
    }
}

private fun MediaCardUi.needsLowRamPlaybackWarning(): Boolean = videoHeight >= 1000

private val KEYBOARD_ROWS = listOf(
    "1234567890",
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm"
)

@Composable
private fun SearchStatusText(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchInProgress && query.isNotBlank()) {
            CircularProgressIndicator(
                color = BRAND_GREEN,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = when {
                query.isBlank() -> "Digite algo para buscar"
                searchInProgress -> "Pesquisando…"
                else -> "$resultCount resultado(s)"
            },
            color = Color(0xFFB0B0B0),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Teclado de busca no estilo TV do YouTube: overlay com a consulta atual e uma grade de teclas
 * navegável por D-pad. Cada tecla é um alvo de foco comum do Compose; OK digita. Sem IME do
 * sistema — funciona igual em qualquer Fire TV e não perde o foco.
 */
@Composable
private fun TvSearchOverlay(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstKeyFocus.requestFocus() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .onPreviewKeyEvent { e ->
                // BACK fecha o teclado e volta para a biblioteca.
                if (e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape)) {
                    onClose(); true
                } else false
            }
    ) {
        val compact = maxWidth < 600.dp
        val keySize = if (compact) 42.dp else 52.dp
        val gap = if (compact) 6.dp else 8.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 16.dp else 48.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
        ) {
            Text("Buscar", style = MaterialTheme.typography.headlineMedium, color = Color.White)

            // Campo mostrando o que já foi digitado.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                    .background(Color(0x11FFFFFF))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = if (query.isEmpty()) "Digite para buscar por título, canal ou arquivo" else "$query|",
                    color = if (query.isEmpty()) Color(0xFF9A9A9A) else Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SearchStatusText(
                query = query,
                resultCount = resultCount,
                searchInProgress = searchInProgress
            )

            // Grade de letras/números.
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        row.forEachIndexed { colIndex, c ->
                            val keyModifier = if (rowIndex == 0 && colIndex == 0) {
                                Modifier.focusRequester(firstKeyFocus)
                            } else {
                                Modifier
                            }
                            KeyButton(
                                label = c.toString(),
                                modifier = keyModifier.size(keySize),
                                onClick = { onKey(c) }
                            )
                        }
                    }
                }

                // Linha de ações: espaço, apagar, limpar, fechar.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    KeyButton(
                        label = "Espaço",
                        modifier = Modifier.height(keySize).width(if (compact) 124.dp else 160.dp),
                        onClick = { onKey(' ') }
                    )
                    KeyButton(
                        label = "⌫ Apagar",
                        modifier = Modifier.height(keySize).width(if (compact) 112.dp else 130.dp),
                        onClick = onBackspace
                    )
                    KeyButton(
                        label = "Limpar",
                        modifier = Modifier.height(keySize).width(if (compact) 92.dp else 110.dp),
                        onClick = onClear
                    )
                    KeyButton(
                        label = "Fechar",
                        modifier = Modifier.height(keySize).width(if (compact) 92.dp else 110.dp),
                        onClick = onClose
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchSearchOverlay(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    suggestions: List<MediaCardUi>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onSelect: (MediaCardUi) -> Unit
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val visibleSuggestions = remember(query, searchInProgress, suggestions) {
        val normalized = query.trim()
        if (normalized.isBlank() || searchInProgress) emptyList()
        else suggestions.take(12)
    }

    LaunchedEffect(Unit) {
        fieldFocus.requestFocus()
        keyboard?.show()
    }
    BackHandler(enabled = true) { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color(0xFF252525))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(fieldFocus),
                    singleLine = true,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                androidx.compose.material3.Text(
                                    "Pesquisar",
                                    color = Color(0xFF9A9A9A),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Close,
                            contentDescription = "Limpar",
                            tint = Color(0xFFD8D8D8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF252525)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        SearchStatusText(
            query = query,
            resultCount = resultCount,
            searchInProgress = searchInProgress,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            visibleSuggestions.forEach { media ->
                TouchSearchRow(
                    media = media,
                    onClick = { onSelect(media) }
                )
            }
            if (searchInProgress && query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = BRAND_GREEN,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        androidx.compose.material3.Text(
                            "Pesquisando…",
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else if (visibleSuggestions.isEmpty() && query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "Nenhum resultado encontrado",
                        color = Color(0xFFB0B0B0),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchSearchRow(
    media: MediaCardUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            media.title,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val thumb = media.posterPath ?: media.thumbnailPath
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 72.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222222))
            )
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0x22FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x44FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x11FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x66FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFFB0B0B0))
            Text(
                text = query.ifBlank { "Buscar por título, canal ou arquivo" },
                color = if (query.isBlank()) Color(0xFFB0B0B0) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Botão único, largo e centralizado no fim da grade. Feedback de foco (verde) e de carregamento
// (spinner). A visibilidade/fade é controlada por quem o exibe (AnimatedVisibility).
@Composable
private fun LoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val accent = Color(0xFF2BEE34)
    val content = if (focused) Color(0xFF0E0E0E) else Color.White
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .widthIn(min = 320.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !loading, onClick = onClick)
            .background(if (focused) accent else Color(0x1FFFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accent else Color(0x44FFFFFF),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
            Text("Carregando…", color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Text("Carregar mais", color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Layout masonry (empacotamento por altura) otimizado para TV.
// 5 colunas virtuais: card retrato (9:16) ocupa 1 coluna; card horizontal (16:9) ocupa 2
// colunas adjacentes. A proporção vem do post (pôster → retrato; só frame → horizontal).
// 6 colunas: card horizontal (2 col) cabe 3 por linha; retrato (1 col) até 6 por linha.
// Colunas pela largura DISPONÍVEL da grade (dp): ~130dp por card. Cards menores no celular
// (2 no retrato, ~5 na paisagem) e ~6 na TV (teto). Evita card gigante em telas largas.
private const val GRID_TARGET_COL_DP = 130f
private fun columnsForWidthDp(widthDp: Float): Int =
    (widthDp / GRID_TARGET_COL_DP).toInt().coerceIn(2, 6)
private val GRID_GAP = 12.dp
private val CARD_TITLE_H = 56.dp

@Composable
private fun LazyMediaGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    lowRamPlaybackWarnings: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    state: LazyStaggeredGridState,
    focusRequesterFor: (String) -> FocusRequester,
    onCardFocused: (String) -> Unit,
    onCardClick: (MediaCardUi) -> Unit,
    loadMoreFocus: FocusRequester,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = columnsForWidthDp(maxWidth.value)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalItemSpacing = GRID_GAP
        ) {
            items(
                items = items,
                key = { it.mediaId }
            ) { media ->
                val requester = focusRequesterFor(media.mediaId)
                MediaCard(
                    media = media,
                    showCover = showCovers,
                    showPlaybackWarning = lowRamPlaybackWarnings && media.needsLowRamPlaybackWarning(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) onCardFocused(media.mediaId) },
                    onClick = { onCardClick(media) }
                )
            }

            if (hasMore) {
                item(
                    key = "load-more",
                    span = StaggeredGridItemSpan.FullLine
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadMoreButton(
                            loading = loadingMore,
                            focusRequester = loadMoreFocus,
                            onClick = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

private fun MediaCardUi.gridAspectRatio(showCovers: Boolean): Float {
    val hasPoster = posterPath != null
    val aspect = when {
        !showCovers -> 2f / 3f
        coverAspectRatio > 0f -> coverAspectRatio
        hasPoster -> 2f / 3f
        else -> 16f / 9f
    }
    return aspect.coerceIn(0.45f, 2.2f)
}

// Skeleton do grid: um mosaico de placeholders com pulse suave, usando o MESMO empacotamento
// do masonry — dá a sensação de que a grade está preenchendo (melhor que um "Carregando…").
@Composable
private fun MediaGridSkeleton(
    showCovers: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )
    val shimmer = Color.White.copy(alpha = alpha)
    // Mosaico determinístico: mistura paisagem (16:9) e retrato (2:3); capas OFF → tudo retrato.
    val aspects = remember(showCovers) {
        if (!showCovers) List(24) { 2f / 3f }
        else listOf(
            16f / 9f, 2f / 3f, 2f / 3f, 16f / 9f, 2f / 3f, 16f / 9f,
            2f / 3f, 16f / 9f, 2f / 3f, 2f / 3f, 16f / 9f, 2f / 3f,
            16f / 9f, 2f / 3f, 2f / 3f, 16f / 9f, 2f / 3f, 2f / 3f
        )
    }

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            aspects.forEach { _ ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(shimmer)
                    )
                    if (!showCovers) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmer)
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val gapPx = GRID_GAP.roundToPx()
        val titlePx = if (showCovers) 0 else CARD_TITLE_H.roundToPx()
        val totalW = constraints.maxWidth
        val cols = columnsForWidthDp(totalW / density)
        val colW = ((totalW - gapPx * (cols - 1)) / cols).coerceAtLeast(1)
        val colHeights = IntArray(cols)
        val placed = ArrayList<Triple<Placeable, Int, Int>>(measurables.size)

        measurables.forEachIndexed { i, measurable ->
            val aspect = aspects[i].coerceIn(0.45f, 2.2f)
            val landscape = showCovers && aspect > 1.15f && cols >= 2
            val span = if (landscape) 2 else 1
            val wPx = if (span == 2) colW * 2 + gapPx else colW
            val hPx = (wPx / aspect).roundToInt() + titlePx

            val startCol = if (span == 1) {
                (0 until cols).minByOrNull { colHeights[it] } ?: 0
            } else {
                (0 until cols - 1).minByOrNull { maxOf(colHeights[it], colHeights[it + 1]) } ?: 0
            }
            val y = if (span == 1) colHeights[startCol]
                    else maxOf(colHeights[startCol], colHeights[startCol + 1])
            val x = startCol * (colW + gapPx)

            placed.add(Triple(measurable.measure(Constraints.fixed(wPx, hPx)), x, y))

            val bottom = y + hPx + gapPx
            if (span == 1) {
                colHeights[startCol] = bottom
            } else {
                colHeights[startCol] = bottom
                colHeights[startCol + 1] = bottom
            }
        }

        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else (colHeights.maxOrNull() ?: 0)
        val totalH = (colHeights.maxOrNull() ?: 0).coerceAtMost(maxH)
        layout(totalW, totalH) {
            placed.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}

@Composable
private fun MediaCard(
    media: MediaCardUi,
    showCover: Boolean,
    showPlaybackWarning: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Com pôster → capa retrato (9:16). Sem pôster (capas ON) → frame 16:9. Capas OFF → placeholder.
    val cover = if (showCover) (media.posterPath ?: media.thumbnailPath) else null
    val showTitle = !showCover || media.posterPath == null
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x0FFFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Capa (proporção da imagem) com barra de progresso e alerta opcional de reprodução.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(media.gridAspectRatio(showCover))
                    .background(Color(0xFF1C1C1C))
            ) {
                if (cover != null) {
                    // Loader por card, mas SÓ se demorar (>180ms): cache hit (voltar à grade) e
                    // cargas rápidas mostram a capa direto, sem spinner nem "blink".
                    var loaded by remember(cover) { mutableStateOf(false) }
                    var showSpinner by remember(cover) { mutableStateOf(false) }
                    AsyncImage(
                        model = cover,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        onState = { if (it.isDone()) loaded = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    LaunchedEffect(cover) {
                        kotlinx.coroutines.delay(180)
                        if (!loaded) showSpinner = true
                    }
                    if (showSpinner && !loaded) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = BRAND_GREEN, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Selos de resolução/duração removidos para não cobrir a arte das capas.

                if (media.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(media.progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                if (showPlaybackWarning) {
                    // Ícone neutro de alerta (aparelho pode não decodificar vídeo em alta resolução).
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC000000))
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Pode não reproduzir vídeo neste aparelho",
                            tint = Color(0xFFFFC857),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (showTitle) {
                Text(
                    media.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_TITLE_H)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private val BRAND_GREEN = Color(0xFF2BEE34)

// Tela de Detalhes (estilo Netflix/Prime): responsiva (TV/paisagem lado a lado, celular/retrato
// empilhado). O conteúdo aparece imediatamente; imagens secundárias carregam sem bloquear a tela.
@Composable
private fun MovieDetailsOverlay(
    media: MediaCardUi,
    details: MovieDetails?,
    showCastPhotos: Boolean,
    lowRamPlaybackWarnings: Boolean,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }
    val playFocus = remember { FocusRequester() }

    val backdrop = details?.backdropPath ?: media.posterPath ?: media.thumbnailPath
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        val portrait = maxHeight > maxWidth
        val compactLandscape = !portrait && maxHeight < 520.dp
        if (portrait) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    if (backdrop != null) {
                        AsyncImage(
                            model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0x00050505), 0.7f to Color(0x99050505), 1f to Color(0xFF050505))
                        )
                    )
                }
                DetailsInfo(
                    media, details, showCastPhotos, lowRamPlaybackWarnings, playFocus, onPlay, onDismiss,
                    actionsFirst = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        } else {
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0xF2050505), 0.45f to Color(0xB3050505), 0.8f to Color(0x00050505))
            ))
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x00050505), 0.55f to Color(0x66050505), 1f to Color(0xF2050505))
            ))
            DetailsInfo(
                media, details, showCastPhotos, lowRamPlaybackWarnings, playFocus, onPlay, onDismiss,
                actionsFirst = compactLandscape,
                modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterStart)
                    .then(if (compactLandscape) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(start = 48.dp, end = 24.dp, top = 40.dp, bottom = 40.dp)
            )
        }
    }
}

private fun coil.compose.AsyncImagePainter.State.isDone(): Boolean =
    this is coil.compose.AsyncImagePainter.State.Success || this is coil.compose.AsyncImagePainter.State.Error

@Composable
private fun DetailsInfo(
    media: MediaCardUi,
    details: MovieDetails?,
    showCastPhotos: Boolean,
    lowRamPlaybackWarnings: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    actionsFirst: Boolean,
    modifier: Modifier = Modifier
) {
    val title = details?.title ?: media.title
    val durationSecs = if ((details?.durationSeconds ?: 0) > 0) details!!.durationSeconds else media.durationSeconds
    val showPlaybackWarning = lowRamPlaybackWarnings && media.needsLowRamPlaybackWarning()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        details?.originalTitle?.takeIf { it.isNotBlank() && it != title }?.let {
            Text(it, color = Color(0xFFC9C9C9), style = MaterialTheme.typography.titleMedium)
        }
        val meta = buildList {
            details?.year?.let { add(it.toString()) }
            if (durationSecs > 0) add(durationLabel(durationSecs))
            details?.rating?.let { add("★ ${"%.1f".format(it)}") }
            details?.ageRating?.takeIf { it.isNotBlank() }?.let { add(it) }
            details?.quality?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (meta.isNotEmpty()) {
            Text(meta.joinToString("   •   "), color = Color(0xFFE6E6E6), style = MaterialTheme.typography.titleSmall)
        }
        if (showPlaybackWarning) {
            Text(
                text = "Este aparelho pode tocar apenas o som em vídeos 1080p. Prefira versão 720p quando disponível.",
                color = Color(0xFFFFD37A),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FFC857))
                    .border(1.dp, Color(0x66FFC857), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        if (actionsFirst) {
            DetailsActionRow(media, showPlaybackWarning, playFocus, onPlay, onDismiss)
        }
        details?.genres?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFFBDBDBD), style = MaterialTheme.typography.bodyMedium)
        }
        details?.synopsis?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFFDCDCDC), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        details?.director?.takeIf { it.isNotBlank() }?.let {
            Text("Diretor: $it", color = Color(0xFFB6B6B6), style = MaterialTheme.typography.bodySmall)
        }

        val cast = details?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            if (showCastPhotos) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    cast.take(8).forEach { c ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                            if (c.photoUrl != null) {
                                AsyncImage(
                                    model = c.photoUrl, contentDescription = c.name, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colorForTitle(c.name))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colorForTitle(c.name)),
                                    contentAlignment = Alignment.Center
                                ) { Text(initialFor(c.name), color = Color.White, style = MaterialTheme.typography.titleMedium) }
                            }
                            Text(c.name, color = Color(0xFFDCDCDC), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                Text("Elenco: ${cast.joinToString(", ") { it.name }}", color = Color(0xFFB6B6B6), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!actionsFirst) {
            DetailsActionRow(media, showPlaybackWarning, playFocus, onPlay, onDismiss)
        }
    }
}

@Composable
private fun DetailsActionRow(
    media: MediaCardUi,
    showPlaybackWarning: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailButton(
            icon = Icons.Filled.PlayArrow,
            label = when {
                showPlaybackWarning -> "Tentar assistir"
                media.progress > 0f -> "Continuar"
                else -> "Assistir"
            },
            primary = true,
            modifier = Modifier.focusRequester(playFocus),
            onClick = onPlay
        )
        DetailButton(icon = Icons.Filled.Close, label = "Voltar", primary = false, onClick = onDismiss)
    }
}

@Composable
private fun DetailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        primary -> BRAND_GREEN
        focused -> Color(0x33FFFFFF)
        else -> Color(0x1FFFFFFF)
    }
    val content = if (primary) Color(0xFF0B0B0B) else Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, color = content, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChannelPickerOverlay(
    channels: List<ChannelChipUi>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .onPreviewKeyEvent { e ->
                when {
                    // Back/Esc fecha.
                    e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape) -> {
                        onDismiss(); true
                    }
                    // Prende o foco no modal: ← / → não têm alvo aqui e escapavam para a grade
                    // ao fundo — consome para o foco ficar na lista (navega só com ↑ / ↓).
                    e.type == KeyEventType.KeyDown &&
                        (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) -> true
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 680.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Escolher canal", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                text = "Selecione o canal usado para listar os vídeos na biblioteca.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xCCFFFFFF)
            )
            Column(
                modifier = Modifier.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                channels.forEachIndexed { index, ch ->
                    val selected = ch.id == activeId
                    var focused by remember { mutableStateOf(false) }
                    val mod = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    val accent = Color(0xFF2BEE34)
                    Row(
                        modifier = mod
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onSelect(ch.id) }
                            .background(
                                when {
                                    focused -> Color(0x33FFFFFF)
                                    selected -> Color(0x1F2BEE34)
                                    else -> Color(0x14FFFFFF)
                                }
                            )
                            .border(
                                width = if (focused || selected) 2.dp else 1.dp,
                                color = when {
                                    focused -> Color.White
                                    selected -> accent
                                    else -> Color(0x33FFFFFF)
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar real do canal quando disponível; fallback: inicial colorida por título.
                        if (ch.avatarPath != null) {
                            AsyncImage(
                                model = ch.avatarPath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorForTitle(ch.title))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorForTitle(ch.title)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initialFor(ch.title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Text(
                            text = ch.title,
                            color = if (selected) accent else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            var closeFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(10.dp))
                    .onFocusChanged { closeFocused = it.isFocused }
                    .clickable(onClick = onDismiss)
                    .background(if (closeFocused) Color(0x33FFFFFF) else Color(0x1FFFFFFF))
                    .border(
                        width = if (closeFocused) 2.dp else 1.dp,
                        color = if (closeFocused) Color.White else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Fechar", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private val CHANNEL_AVATAR_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFF42A5F5), Color(0xFFFFA726), Color(0xFF66BB6A), Color(0xFFEC407A)
)

private fun colorForTitle(title: String): Color =
    CHANNEL_AVATAR_COLORS[kotlin.math.abs(title.hashCode()) % CHANNEL_AVATAR_COLORS.size]

private fun initialFor(title: String): String =
    title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
