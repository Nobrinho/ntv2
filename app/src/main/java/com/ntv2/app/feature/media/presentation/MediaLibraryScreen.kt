package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.core.ui.LocalFocusRestoreSignal
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.key.onKeyEvent
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
    onChannelPickerConsumed: () -> Unit = {},
    openSearchRequest: Int = 0,
    onSearchRequestConsumed: () -> Unit = {},
    refreshRequest: Int = 0,
    onRefreshRequestConsumed: () -> Unit = {},
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
    // TV: resultado da busca que abriu os Detalhes. Ao fechar os Detalhes, a busca reabre com a
    // mesma consulta e o foco volta nesse resultado (redigitar pelo D-pad é caro).
    // Saveable: sobrevive à ida ao player (a Biblioteca sai da composição durante a reprodução).
    var searchReturnMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    // Controle de foco do "Carregar mais": ao clicar, o card sai da árvore quando os itens chegam
    // e o foco se perde (direcional depois "pula" pro último). Movemos o foco de forma explícita.
    val loadMoreFocus = remember { FocusRequester() }
    var loadMoreRequested by remember { mutableStateOf(false) }
    // Fronteira antes do load: guardamos o último item conhecido para achar o 1º novo por
    // identidade (robusto ao corte do topo pelo teto de itens).
    var lastIdBeforeLoad by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyStaggeredGridState()
    val adaptive = rememberAdaptiveLayoutInfo()
    val scope = rememberCoroutineScope()
    // Último card focado. NÃO é estado observável: guardar no ViewModel a cada movimento do D-pad
    // recompunha a tela inteira (travava em TVs fracas). Só vai ao ViewModel ao abrir um card.
    val lastFocusedCard = remember { arrayOfNulls<String>(1) }
    val firstEntry = remember { booleanArrayOf(true) }
    // Foco estava no rail (e não num card)? Usado ao cancelar o "Fechar o aplicativo?".
    val focusInRail = remember { booleanArrayOf(false) }
    val railChannelsFocus = remember { FocusRequester() }
    val railSettingsFocus = remember { FocusRequester() }
    // Saiu pelo botão "Config" do rail: ao voltar, o foco retorna a ele (e não ao último card).
    var returnToSettingsButton by rememberSaveable { mutableStateOf(false) }
    // "Escolher canal" aberto pelo rail: ao fechar com Voltar, o foco volta ao botão Canais.
    var pickerFromRail by remember { mutableStateOf(false) }
    val restoreSignal = LocalFocusRestoreSignal.current

    // Devolve o foco a um card (rola até ele se preciso); sem cards, vai para a barra de ações.
    fun focusCard(mediaId: String?) {
        scope.launch {
            val items = state.items
            val id = mediaId?.takeIf { wanted -> items.any { it.mediaId == wanted } }
                ?: items.firstOrNull()?.mediaId
            val index = items.indexOfFirst { it.mediaId == id }
            if (id != null && index >= 0 &&
                gridState.layoutInfo.visibleItemsInfo.none { it.index == index }
            ) {
                gridState.scrollToItem(index)
            }
            // Espera o overlay (que prende o foco) sair da árvore antes de pedir o foco.
            withFrameNanos { }
            withFrameNanos { }
            val ok = id?.let { cardFocusRequesters[it] }
                ?.let { r -> runCatching { r.requestFocus() }.isSuccess } ?: false
            if (!ok) runCatching { initialActionsFocus.requestFocus() }
        }
    }

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
    // Ao voltar de outra tela com um card já aberto antes, quem restaura o foco é o
    // focusRestoreNonce (no card); aqui não disputa com ele.
    LaunchedEffect(searching) {
        if (searching) return@LaunchedEffect
        val restoring = firstEntry[0] && (state.lastFocusedMediaId != null || returnToSettingsButton)
        firstEntry[0] = false
        if (!restoring) runCatching { initialActionsFocus.requestFocus() }
    }
    // Voltou das Configurações abertas pelo rail: foco no botão Config.
    LaunchedEffect(Unit) {
        if (returnToSettingsButton) {
            withFrameNanos { }
            withFrameNanos { }
            returnToSettingsButton = false
            if (runCatching { railSettingsFocus.requestFocus() }.isFailure) {
                runCatching { initialActionsFocus.requestFocus() }
            }
        }
    }
    // Cancelou o "Fechar o aplicativo?": volta ao card (ou ao rail) onde o foco estava.
    LaunchedEffect(restoreSignal) {
        if (restoreSignal == 0 || searching || detailsMedia != null || channelPicker) return@LaunchedEffect
        if (focusInRail[0]) {
            withFrameNanos { }
            runCatching { initialActionsFocus.requestFocus() }
        } else {
            focusCard(lastFocusedCard[0])
        }
    }

    // Só abre o modal de canais por um PEDIDO novo (botão). Antes, ao voltar de outra tela a
    // recomposição via este efeito com o contador ainda > 0 reabria o modal sozinho. Agora o
    // pedido é consumido (zerado) assim que atendido.
    LaunchedEffect(openChannelPickerRequest) {
        if (openChannelPickerRequest > 0) {
            channelPicker = true
            onChannelPickerConsumed()
        }
    }

    LaunchedEffect(openSearchRequest) {
        if (openSearchRequest > 0) {
            searching = true
            onSearchRequestConsumed()
        }
    }
    LaunchedEffect(refreshRequest) {
        if (refreshRequest > 0) {
            viewModel.onAction(MediaLibraryAction.Refresh)
            onRefreshRequestConsumed()
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
        // Voltando do player os Detalhes reabrem (e focam o Assistir): não disputa o foco.
        if (returnToSettingsButton || detailsMedia != null || state.returnToDetailsMediaId != null) {
            return@LaunchedEffect
        }
        val mediaId = state.lastFocusedMediaId ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.mediaId == mediaId }
        if (index >= 0) {
            gridState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
        }
        cardFocusRequesters[mediaId]?.requestFocus()
    }

    // Saiu do player: reabre os Detalhes de onde ele foi aberto (o player só é acessível por eles).
    // Com os Detalhes ainda na tela (acabou de apertar Assistir) não faz nada; o pedido só é
    // consumido quando os Detalhes são fechados — assim ele sobrevive à ida ao player, quando a
    // Biblioteca sai da composição e o estado local (detailsMedia) é perdido.
    LaunchedEffect(state.returnToDetailsMediaId) {
        val mediaId = state.returnToDetailsMediaId ?: return@LaunchedEffect
        if (detailsMedia != null) return@LaunchedEffect
        val media = state.returnToDetailsMedia?.takeIf { it.mediaId == mediaId }
            ?: state.items.firstOrNull { it.mediaId == mediaId }
            ?: return@LaunchedEffect
        detailsMedia = media
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
                Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) focusInRail[0] = true }) {
                    NavRail(
                        firstItemFocus = initialActionsFocus,
                        channelsFocus = railChannelsFocus,
                        settingsFocus = railSettingsFocus,
                        searchActive = false,
                        showClearFilter = false,
                        onSearch = { searching = true },
                        onClearFilter = { viewModel.onAction(MediaLibraryAction.SearchChanged("")) },
                        onChannels = { pickerFromRail = true; channelPicker = true },
                        onRefresh = { viewModel.onAction(MediaLibraryAction.Refresh) },
                        onSettings = { returnToSettingsButton = true; onOpenSettings() }
                    )
                }
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
                                lastFocusedCard[0] = id
                                focusInRail[0] = false
                                // Paginação pelo foco (como na busca): perto do fim já pede a próxima página.
                                val items = state.items
                                if (state.hasMore && !loadMoreRequested &&
                                    items.indexOfFirst { it.mediaId == id } >= items.size - AUTO_LOAD_THRESHOLD
                                ) {
                                    viewModel.onAction(MediaLibraryAction.LoadMore)
                                }
                            },
                            onCardClick = { media ->
                                viewModel.onAction(MediaLibraryAction.VideoFocused(media.mediaId))
                                viewModel.onAction(MediaLibraryAction.ClearOpenVideoState)
                                viewModel.onAction(MediaLibraryAction.DetailsOpened(media))
                                detailsMedia = media
                            },
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
                    // Não fecha os detalhes aqui: fechar antes da navegação (assíncrona) fazia a grid
                    // "piscar" no intervalo. O player cobre o overlay; ao voltar, os detalhes reabrem
                    // via returnToDetailsMediaId.
                    viewModel.onAction(MediaLibraryAction.OpenVideo(media))
                },
                onDismiss = {
                    detailsMedia = null
                    viewModel.onAction(MediaLibraryAction.ConsumeReturnToDetails)
                    viewModel.onAction(MediaLibraryAction.DetailsClosed(media.mediaId))
                    if (searchReturnMediaId != null) searching = true else focusCard(media.mediaId)
                },
                playLoading = state.isOpeningVideo,
                playFailed = state.openVideoFailed
            )
        }

        if (channelPicker) {
            ChannelPickerOverlay(
                channels = state.enabledChannels,
                activeId = state.activeChannelId,
                onSelect = { id ->
                    viewModel.onAction(MediaLibraryAction.SelectActiveChannel(id))
                    channelPicker = false
                    pickerFromRail = false
                    // A grade vai recarregar: foco estável na barra de ações.
                    scope.launch {
                        withFrameNanos { }
                        withFrameNanos { }
                        runCatching { initialActionsFocus.requestFocus() }
                    }
                },
                onDismiss = {
                    channelPicker = false
                    if (pickerFromRail) {
                        pickerFromRail = false
                        scope.launch {
                            withFrameNanos { }
                            withFrameNanos { }
                            if (runCatching { railChannelsFocus.requestFocus() }.isFailure) {
                                focusCard(lastFocusedCard[0])
                            }
                        }
                    } else {
                        focusCard(lastFocusedCard[0])
                    }
                }
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
                    results = if (state.searchQuery.isBlank() || searchInProgress) emptyList() else state.searchResults,
                    hasMore = state.searchHasMore,
                    loadingMore = state.isSearchLoadingMore,
                    restoreFocusMediaId = searchReturnMediaId,
                    onRestoreConsumed = { searchReturnMediaId = null },
                    onLoadMore = { viewModel.onAction(MediaLibraryAction.LoadMoreSearch) },
                    onSubmit = { viewModel.onAction(MediaLibraryAction.SubmitSearch) },
                    onSelect = { media ->
                        // Mantém a consulta: ao fechar os Detalhes a busca reabre neste resultado.
                        viewModel.onAction(MediaLibraryAction.ClearOpenVideoState)
                        viewModel.onAction(MediaLibraryAction.DetailsOpened(media))
                        searchReturnMediaId = media.mediaId
                        detailsMedia = media
                        searching = false
                    },
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
                        searchReturnMediaId = null
                        viewModel.onAction(MediaLibraryAction.SearchChanged(""))
                    }
                )
            } else {
                TouchSearchOverlay(
                    query = state.searchQuery,
                    searchInProgress = searchInProgress,
                    suggestions = if (state.searchQuery.isBlank() || searchInProgress) emptyList() else state.searchResults,
                    hasMore = state.searchHasMore,
                    loadingMore = state.isSearchLoadingMore,
                    onQueryChange = { viewModel.onAction(MediaLibraryAction.SearchChanged(it)) },
                    onClear = { viewModel.onAction(MediaLibraryAction.SearchChanged("")) },
                    onClose = {
                        searching = false
                        viewModel.onAction(MediaLibraryAction.SearchChanged(""))
                    },
                    onLoadMore = { viewModel.onAction(MediaLibraryAction.LoadMoreSearch) },
                    onSubmit = { viewModel.onAction(MediaLibraryAction.SubmitSearch) },
                    onSelect = { media ->
                        viewModel.onAction(MediaLibraryAction.ClearOpenVideoState)
                        viewModel.onAction(MediaLibraryAction.DetailsOpened(media))
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactLibraryChip(
            icon = Icons.Filled.Search,
            contentDescription = "Busca",
            modifier = Modifier.focusRequester(firstItemFocus),
            onClick = onSearch
        )
        CompactLibraryChip(
            icon = Icons.Filled.Refresh,
            contentDescription = "Atualizar",
            onClick = onRefresh
        )
    }
}

// Botão de ação da biblioteca no celular: circular, só ícone, com contraste (círculo escuro +
// borda visível) e destaque de foco (fundo branco / ícone escuro).
@Composable
private fun CompactLibraryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(46.dp)
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
            modifier = Modifier.size(24.dp)
        )
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
 * Busca da TV (estilo YouTube/Google TV): teclado próprio à esquerda, resultados ao vivo à
 * direita. Sem IME do sistema — funciona igual em qualquer Fire TV e não perde o foco.
 *
 * Regras de D-pad:
 * - → na última tecla de cada linha entra nos resultados (último resultado focado, senão o 1º);
 * - ← (ou BACK) num resultado volta para a última tecla usada;
 * - ↑ no 1º / ↓ no último resultado não saem da lista (↓ perto do fim pagina);
 * - resultados trocados (nova consulta) com foco na lista → foco volta ao teclado;
 * - BACK no teclado fecha a busca.
 */
@Composable
private fun TvSearchOverlay(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    results: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    restoreFocusMediaId: String?,
    onRestoreConsumed: () -> Unit,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val keyRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val resultRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun keyRequester(id: String) = keyRequesters.getOrPut(id) { FocusRequester() }
    fun resultRequester(id: String) = resultRequesters.getOrPut(id) { FocusRequester() }

    val firstKeyId = KEYBOARD_ROWS.first().first().toString()
    var lastKeyId by remember { mutableStateOf(firstKeyId) }
    var lastResultIndex by remember { mutableStateOf(0) }
    // Tecla do teclado virtual: lembra a última focada (voltar dos resultados cai nela).
    fun keyMod(id: String) = Modifier
        .focusRequester(keyRequester(id))
        .onFocusChanged { if (it.isFocused) lastKeyId = id }
    var focusInResults by remember { mutableStateOf(false) }
    // "Buscar" pressionado: leva o foco ao 1º resultado assim que a busca terminar.
    var focusResultsWhenReady by remember { mutableStateOf(false) }
    val currentResults by androidx.compose.runtime.rememberUpdatedState(results)

    fun focusKeyboard() {
        runCatching { keyRequester(lastKeyId).requestFocus() }
            .onFailure { runCatching { keyRequester(firstKeyId).requestFocus() } }
    }

    fun focusResult(index: Int) {
        val list = currentResults
        if (list.isEmpty()) return
        val i = index.coerceIn(0, list.lastIndex)
        scope.launch {
            // O item pode não estar composto (lazy): rola até ele antes de pedir foco.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == i }) {
                listState.scrollToItem(i)
                withFrameNanos { }
            }
            runCatching { resultRequester(list[i].mediaId).requestFocus() }
        }
    }

    // Foco inicial: volta ao resultado que abriu os Detalhes; senão, 1ª tecla.
    LaunchedEffect(Unit) {
        val index = restoreFocusMediaId?.let { id -> results.indexOfFirst { it.mediaId == id } } ?: -1
        onRestoreConsumed()
        if (index >= 0) {
            lastResultIndex = index
            listState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
            runCatching { resultRequester(results[index].mediaId).requestFocus() }
                .onFailure { focusKeyboard() }
        } else {
            runCatching { keyRequester(firstKeyId).requestFocus() }
        }
    }

    // Nova consulta com o foco na lista: o item focado deixa de existir — devolve ao teclado.
    LaunchedEffect(query) {
        lastResultIndex = 0
        if (focusInResults) focusKeyboard()
    }

    LaunchedEffect(focusResultsWhenReady, searchInProgress, results) {
        if (!focusResultsWhenReady || searchInProgress) return@LaunchedEffect
        focusResultsWhenReady = false
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
            withFrameNanos { }
            focusResult(0)
        }
    }

    fun typeChar(c: Char) {
        onKey(c)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .onPreviewKeyEvent { e ->
                when {
                    // BACK em camadas: resultados → teclado; teclado → fecha a busca.
                    e.key == Key.Back || e.key == Key.Escape -> {
                        if (e.type == KeyEventType.KeyUp) {
                            if (focusInResults) focusKeyboard() else onClose()
                        }
                        true
                    }
                    e.type != KeyEventType.KeyDown -> false
                    // Teclado físico / teclas numéricas do controle digitam direto.
                    e.key == Key.Backspace -> { onBackspace(); true }
                    e.key == Key.Spacebar -> { typeChar(' '); true }
                    else -> {
                        val ch = e.nativeKeyEvent.unicodeChar.toChar()
                        if (ch.isLetterOrDigit()) { typeChar(ch.lowercaseChar()); true } else false
                    }
                }
            }
    ) {
        val compact = maxWidth < 900.dp
        val outerPadding = if (compact) 24.dp else 40.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 32.dp)
        ) {
            // ── Coluna esquerda: campo + teclado ──
            BoxWithConstraints(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            ) {
                val gap = 6.dp
                val columns = KEYBOARD_ROWS.maxOf { it.length }
                val keySize = ((maxWidth - gap * (columns - 1)) / columns).coerceIn(28.dp, 52.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
                ) {
                    Text("Buscar", style = MaterialTheme.typography.headlineMedium, color = Color.White)

                    // Campo mostrando o que já foi digitado.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (query.isEmpty()) "Digite para buscar por título, canal ou arquivo" else "$query|",
                            color = if (query.isEmpty()) Color(0xFF9A9A9A) else Color.White,
                            style = if (query.isEmpty()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SearchStatusText(
                        query = query,
                        resultCount = resultCount,
                        searchInProgress = searchInProgress
                    )

                    // Última tecla de cada linha: → entra nos resultados.
                    val enterResultsOnRight = Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                            if (currentResults.isNotEmpty()) focusResult(lastResultIndex)
                            true
                        } else false
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        KEYBOARD_ROWS.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEachIndexed { colIndex, c ->
                                    val id = c.toString()
                                    KeyButton(
                                        label = id,
                                        modifier = Modifier
                                            .then(if (colIndex == row.lastIndex) enterResultsOnRight else Modifier)
                                            .then(keyMod(id))
                                            .size(keySize),
                                        onClick = { lastKeyId = id; typeChar(c) }
                                    )
                                }
                            }
                        }

                        // Ações: espaço/apagar; buscar/limpar/fechar.
                        val actionHeight = keySize.coerceAtLeast(40.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            KeyButton(
                                label = "Espaço",
                                modifier = Modifier
                                    .then(keyMod("space"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = { lastKeyId = "space"; typeChar(' ') }
                            )
                            KeyButton(
                                label = "⌫ Apagar",
                                modifier = enterResultsOnRight
                                    // OK segurado: apaga em sequência se o controle repetir a tecla.
                                    .onPreviewKeyEvent { e ->
                                        val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter ||
                                            e.key == Key.NumPadEnter
                                        if (isOk && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0) {
                                            onBackspace(); true
                                        } else false
                                    }
                                    .then(keyMod("backspace"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = { lastKeyId = "backspace"; onBackspace() }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            KeyButton(
                                label = "Buscar",
                                modifier = Modifier
                                    .then(keyMod("submit"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = {
                                    lastKeyId = "submit"
                                    if (query.isNotBlank()) {
                                        focusResultsWhenReady = true
                                        onSubmit()
                                    }
                                }
                            )
                            KeyButton(
                                label = "Limpar",
                                modifier = Modifier
                                    .then(keyMod("clear"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = { lastKeyId = "clear"; onClear() }
                            )
                            KeyButton(
                                label = "Fechar",
                                modifier = enterResultsOnRight
                                    .then(keyMod("close"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = onClose
                            )
                        }
                    }
                }
            }

            // ── Coluna direita: resultados ao vivo ──
            SearchResultsList(
                query = query,
                searchInProgress = searchInProgress,
                results = results,
                hasMore = hasMore,
                loadingMore = loadingMore,
                listState = listState,
                onLoadMore = onLoadMore,
                onSelect = onSelect,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF))
                    .focusGroup()
                    .onFocusChanged { focusInResults = it.hasFocus },
                rowModifier = { index, media ->
                    Modifier
                        .focusRequester(resultRequester(media.mediaId))
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastResultIndex = index
                                // Paginação pelo foco: perto do fim, pede a próxima página.
                                if (index >= currentResults.size - 3 && hasMore && !loadingMore) onLoadMore()
                            }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionLeft -> { focusKeyboard(); true }
                                Key.DirectionUp -> index == 0
                                Key.DirectionDown -> index == currentResults.lastIndex
                                else -> false
                            }
                        }
                }
            )
        }
    }
}

@Composable
private fun TouchSearchOverlay(
    query: String,
    searchInProgress: Boolean,
    suggestions: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    onSubmit: () -> Unit,
    onSelect: (MediaCardUi) -> Unit
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

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
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSubmit() }),
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

        SearchResultsList(
            query = query,
            searchInProgress = searchInProgress,
            results = suggestions,
            hasMore = hasMore,
            loadingMore = loadingMore,
            listState = listState,
            onLoadMore = onLoadMore,
            onSelect = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Lista de resultados compartilhada por TV e mobile: mesmas regras de exibição (esconde durante a
 * busca, "Pesquisando…", "Nenhum resultado encontrado", "Fim da lista") e paginação infinita ao
 * aproximar do fim. [rowModifier] permite à TV anexar foco/D-pad a cada linha.
 */
@Composable
private fun SearchResultsList(
    query: String,
    searchInProgress: Boolean,
    results: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier,
    rowModifier: (index: Int, media: MediaCardUi) -> Modifier = { _, _ -> Modifier }
) {
    val visibleResults = remember(query, searchInProgress, results) {
        if (query.isBlank() || searchInProgress) emptyList() else results
    }
    // Paginação infinita: dispara ao aproximar do fim da lista.
    val shouldLoadMore by remember(visibleResults.size) {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            visibleResults.isNotEmpty() && last >= visibleResults.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) onLoadMore()
    }

    // Feedback de busca fica só no centro (spinner "Pesquisando…" / "Nenhum resultado"),
    // sem duplicar um status no topo. Lista com paginação infinita.
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        visibleResults.forEachIndexed { index, media ->
            item(key = media.mediaId) {
                SearchResultRow(
                    media = media,
                    modifier = rowModifier(index, media),
                    onClick = { onSelect(media) }
                )
            }
        }
        // Rodapé: spinner ao carregar mais; "Fim da lista" discreto quando acabou.
        if (visibleResults.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(
                            color = BRAND_GREEN,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (!hasMore) {
                        androidx.compose.material3.Text(
                            "Fim da lista",
                            color = Color(0xFF6E6E6E),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        if (searchInProgress && query.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
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
            }
        } else if (visibleResults.isEmpty() && query.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
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
private fun SearchResultRow(
    media: MediaCardUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Destaque de foco só aparece com D-pad (no toque o clickable não recebe foco).
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x26FFFFFF) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White) else Modifier)
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
            // Mostra o pôster na proporção original: paisagem (H) fica largo, retrato (V) fica em pé.
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(58.dp)
                    .aspectRatio(media.gridAspectRatio(true))
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
// Quantos cards antes do fim o foco dispara o carregamento da próxima página.
private const val AUTO_LOAD_THRESHOLD = 10
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
    val scope = rememberCoroutineScope()
    // Grade de alturas mistas: a busca espacial padrão do ↑/↓ às vezes pula de coluna.
    // Aqui ↑/↓ seguem a mesma coluna (lane); se o alvo não está visível, rola um card e tenta de novo.
    fun moveInLane(mediaId: String, down: Boolean): Boolean {
        val cur = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mediaId } ?: return false
        fun target() = state.layoutInfo.visibleItemsInfo
            .filter { it.lane == cur.lane && it.key != "load-more" }
            .filter { if (down) it.index > cur.index else it.index < cur.index }
            .let { l -> if (down) l.minByOrNull { it.index } else l.maxByOrNull { it.index } }
        fun focusLoadMore(): Boolean = down && hasMore &&
            runCatching { loadMoreFocus.requestFocus() }.isSuccess
        target()?.let { t ->
            runCatching { focusRequesterFor(t.key as String).requestFocus() }
            return true
        }
        if (state.layoutInfo.visibleItemsInfo.any { it.key == "load-more" } && focusLoadMore()) return true
        val canScroll = if (down) state.canScrollForward else state.canScrollBackward
        if (!canScroll) return false
        scope.launch {
            val step = (cur.size.height + GRID_GAP.value * 2).toFloat()
            state.scrollBy(if (down) step else -step)
            withFrameNanos { }
            val t = target()
            if (t != null) runCatching { focusRequesterFor(t.key as String).requestFocus() } else focusLoadMore()
        }
        return true
    }
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
                        .onFocusChanged { if (it.isFocused) onCardFocused(media.mediaId) }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> moveInLane(media.mediaId, down = true)
                                Key.DirectionUp -> moveInLane(media.mediaId, down = false)
                                else -> false
                            }
                        },
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
    onDismiss: () -> Unit,
    playLoading: Boolean = false,
    playFailed: Boolean = false
) {
    BackHandler(enabled = true) { onDismiss() }
    val playFocus = remember { FocusRequester() }

    val backdrop = details?.backdropPath ?: media.posterPath ?: media.thumbnailPath
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    // trapFocus: a grade continua composta por trás — o foco não pode escapar para ela.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF050505)).trapFocus()) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    playLoading = playLoading,
                    playFailed = playFailed
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
                    // Rolável sempre: ao focar os botões, a coluna rola até eles (nunca ficam cortados).
                    .verticalScroll(rememberScrollState())
                    .padding(start = 48.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
                playLoading = playLoading,
                playFailed = playFailed
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
    modifier: Modifier = Modifier,
    playLoading: Boolean = false,
    playFailed: Boolean = false
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
            DetailsActionRow(media, showPlaybackWarning, playFocus, onPlay, onDismiss, playLoading, playFailed)
        }
        details?.genres?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFFBDBDBD), style = MaterialTheme.typography.bodyMedium)
        }
        details?.synopsis?.takeIf { it.isNotBlank() }?.let { SynopsisText(it) }
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
            DetailsActionRow(media, showPlaybackWarning, playFocus, onPlay, onDismiss, playLoading, playFailed)
        }
    }
}

// Sinopse: 4 linhas; focável pelo D-pad e OK expande/recolhe o texto completo.
@Composable
private fun SynopsisText(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { expanded = !expanded }
            .background(if (focused) Color(0x1FFFFFFF) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text,
            color = Color(0xFFDCDCDC),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (focused) {
            Text(
                if (expanded) "OK para recolher" else "OK para ler tudo",
                color = Color(0xFF9A9A9A),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DetailsActionRow(
    media: MediaCardUi,
    showPlaybackWarning: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    playLoading: Boolean = false,
    playFailed: Boolean = false
) {
    Row(modifier = Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailButton(
            icon = Icons.Filled.PlayArrow,
            label = when {
                playLoading -> "Abrindo…"
                playFailed -> "Falhou — tentar de novo"
                showPlaybackWarning -> "Tentar assistir"
                media.progress > 0f -> "Continuar"
                else -> "Assistir"
            },
            primary = true,
            modifier = Modifier.focusRequester(playFocus),
            loading = playLoading,
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
    modifier: Modifier = Modifier,
    loading: Boolean = false
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
            // Enquanto carrega, ignora novos toques (evita disparos duplicados).
            .clickable(enabled = !loading, onClick = onClick)
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
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
                .padding(vertical = 24.dp)
                .trapFocus()
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
            // Lista rolável (muitos canais não cabem na tela); foco inicial no canal ativo.
            val focusIndex = channels.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                channels.forEachIndexed { index, ch ->
                    val selected = ch.id == activeId
                    var focused by remember { mutableStateOf(false) }
                    val mod = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier
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
