package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.ntv2.app.core.ui.LocalFocusRestoreSignal
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.MainBottomNav
import com.ntv2.app.core.ui.MainTab
import com.ntv2.app.core.ui.NavRail
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryAction
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryViewModel

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
    // Canal cuja 1ª página já foi posicionada no topo. A grade guarda a rolagem entre canais; sem
    // isso, trocar de canal abria o novo na altura em que o anterior estava (e já disparava o
    // "carregar mais" do novo canal, parecendo continuar a página anterior).
    var topAlignedChannel by remember { mutableStateOf<Long?>(null) }
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

    // Pré-carrega no disco as capas remotas da página recém-carregada (as linhas de baixo ficam
    // prontas antes de rolar). Só dispara quando o conjunto de capas muda.
    val coverContext = LocalContext.current
    val coverUrls = remember(state.items, state.showCovers) {
        if (state.showCovers) state.items.mapNotNull { it.posterPath ?: it.thumbnailPath } else emptyList()
    }
    LaunchedEffect(coverUrls) {
        if (coverUrls.isNotEmpty()) com.ntv2.app.core.ui.prefetchCovers(coverContext, coverUrls)
    }

    // Com os Detalhes abertos, a arte de fundo e o elenco é que importam: solta a fila do prefetch
    // para eles não entrarem atrás dela.
    LaunchedEffect(detailsMedia?.mediaId) {
        if (detailsMedia != null) com.ntv2.app.core.ui.cancelCoverPrefetch()
    }

    // Paginação por ROLAGEM (vale para dedo e controle; na TV o foco também dispara). Sem isto, no
    // celular a próxima página só vinha pelo card "Carregar mais" e a página de cima nunca vinha.
    LaunchedEffect(gridState, state.items.size, state.hasMore, state.hasPrevious) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val visible = info.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .collect { (firstVisible, lastVisible) ->
                val total = state.items.size
                if (total == 0) return@collect
                if (state.hasMore && lastVisible >= total - AUTO_LOAD_THRESHOLD) {
                    viewModel.onAction(MediaLibraryAction.LoadMore)
                }
                if (state.hasPrevious && firstVisible < AUTO_LOAD_THRESHOLD) {
                    viewModel.onAction(MediaLibraryAction.LoadPrevious)
                }
            }
    }

    LaunchedEffect(state.activeChannelId, state.isLoading) {
        val channelId = state.activeChannelId ?: return@LaunchedEffect
        if (state.isLoading || channelId == topAlignedChannel) return@LaunchedEffect
        val switched = topAlignedChannel != null
        topAlignedChannel = channelId
        if (!switched) return@LaunchedEffect // 1ª carga da tela: mantém o foco inicial padrão.
        runCatching { gridState.scrollToItem(0) }
        withFrameNanos { }
        state.items.firstOrNull()?.mediaId?.let { first ->
            cardFocusRequesters[first]?.let { runCatching { it.requestFocus() } }
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
                            cardLoadingStyle = state.cardLoadingStyle,
                            animationsEnabled = state.animationsEnabled,
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
                                val focusedIndex = items.indexOfFirst { it.mediaId == id }
                                if (state.hasMore && !loadMoreRequested &&
                                    focusedIndex >= items.size - AUTO_LOAD_THRESHOLD
                                ) {
                                    viewModel.onAction(MediaLibraryAction.LoadMore)
                                }
                                // Subindo perto do início com o topo descartado (teto de cards): busca a
                                // página de cima. A grade mantém a posição pelo key do card focado.
                                if (state.hasPrevious && focusedIndex in 0 until AUTO_LOAD_THRESHOLD) {
                                    viewModel.onAction(MediaLibraryAction.LoadPrevious)
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
                animationsEnabled = state.animationsEnabled,
                onPlay = {
                    // Não fecha os detalhes aqui: fechar antes da navegação (assíncrona) fazia a grid
                    // "piscar" no intervalo. O player cobre o overlay; ao voltar, os detalhes reabrem
                    // via returnToDetailsMediaId.
                    viewModel.onAction(MediaLibraryAction.OpenVideo(media))
                },
                onRestart = { viewModel.onAction(MediaLibraryAction.RestartVideo(media)) },
                isTv = adaptive.isTv,
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
internal fun CompactLibraryActions(
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
internal fun CompactLibraryChip(
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
internal fun durationLabel(durationSeconds: Int): String {
    val totalMinutes = durationSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h${minutes}min"
        hours > 0 -> "${hours}h"
        else -> "${minutes}min"
    }
}

internal fun MediaCardUi.needsLowRamPlaybackWarning(): Boolean = videoHeight >= 1000
