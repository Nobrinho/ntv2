package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.ntv2.app.core.ui.NavRail
import com.ntv2.app.feature.media.presentation.state.ChannelChipUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryAction
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryViewModel
import kotlin.math.roundToInt

@Composable
fun MediaLibraryScreen(
    viewModel: MediaLibraryViewModel,
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
        cardFocusRequesters[mediaId]?.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Rail lateral de navegação (logo + ações), estilo TV.
            NavRail(
                firstItemFocus = initialActionsFocus,
                searchActive = state.searchQuery.isNotBlank(),
                onSearch = { searching = true },
                onChannels = { channelPicker = true },
                onRefresh = { viewModel.onAction(MediaLibraryAction.Refresh) },
                onSettings = onOpenSettings
            )

            // Conteúdo do canal ativo (grade plana de aspecto misto).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.searchQuery.isNotBlank()) {
                        Text(
                            "• busca: ${state.searchQuery}",
                            color = Color(0xFFB0B0B0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                when {
                    state.isLoading -> Text("Carregando…", color = Color.White)

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
                        MasonryMediaGrid(
                            items = state.items,
                            showCovers = state.showCovers,
                            hasMore = state.hasMore,
                            focusRequesterFor = { id ->
                                cardFocusRequesters.getOrPut(id) { FocusRequester() }
                            },
                            onCardFocused = { id ->
                                viewModel.onAction(MediaLibraryAction.VideoFocused(id))
                            },
                            onCardClick = { media ->
                                viewModel.onAction(MediaLibraryAction.OpenVideo(media))
                            },
                            onLoadMore = { viewModel.onAction(MediaLibraryAction.LoadMore) },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
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
            val resultCount = state.items.size
            SearchOverlay(
                query = state.searchQuery,
                resultCount = resultCount,
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
                onClose = { searching = false }
            )
        }
    }
}

/** Converte a altura do vídeo (px) em rótulo comercial de resolução (null se desconhecida). */
private fun resolutionLabel(height: Int): String? = when {
    height <= 0 -> null
    height >= 2000 -> "4K"
    height >= 1000 -> "1080p"
    height >= 700 -> "720p"
    height >= 460 -> "480p"
    else -> "SD"
}

private val KEYBOARD_ROWS = listOf(
    "1234567890",
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm"
)

/**
 * Teclado de busca no estilo TV do YouTube: overlay com a consulta atual e uma grade de teclas
 * navegável por D-pad. Cada tecla é um alvo de foco comum do Compose; OK digita. Sem IME do
 * sistema — funciona igual em qualquer Fire TV e não perde o foco.
 */
@Composable
private fun SearchOverlay(
    query: String,
    resultCount: Int,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstKeyFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .onPreviewKeyEvent { e ->
                // BACK fecha o teclado e volta para a biblioteca.
                if (e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape)) {
                    onClose(); true
                } else false
            }
            .padding(48.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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

            Text(
                text = if (query.isBlank()) "Digite algo para buscar" else "$resultCount resultado(s)",
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.bodyMedium
            )

            // Grade de letras/números.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KEYBOARD_ROWS.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIndex, c ->
                            val keyModifier = if (rowIndex == 0 && colIndex == 0) {
                                Modifier.focusRequester(firstKeyFocus)
                            } else {
                                Modifier
                            }
                            KeyButton(
                                label = c.toString(),
                                modifier = keyModifier.size(52.dp),
                                onClick = { onKey(c) }
                            )
                        }
                    }
                }

                // Linha de ações: espaço, apagar, limpar, fechar.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton(
                        label = "Espaço",
                        modifier = Modifier.height(52.dp).width(160.dp),
                        onClick = { onKey(' ') }
                    )
                    KeyButton(
                        label = "⌫ Apagar",
                        modifier = Modifier.height(52.dp).width(130.dp),
                        onClick = onBackspace
                    )
                    KeyButton(
                        label = "Limpar",
                        modifier = Modifier.height(52.dp).width(110.dp),
                        onClick = onClear
                    )
                    KeyButton(
                        label = "Fechar",
                        modifier = Modifier.height(52.dp).width(110.dp),
                        onClick = onClose
                    )
                }
            }
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

@Composable
private fun LoadMoreCard(
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x11FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x44FFFFFF),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("Carregar mais", color = Color.White, textAlign = TextAlign.Center)
    }
}

// Layout masonry (empacotamento por altura) otimizado para TV.
// 5 colunas virtuais: card retrato (9:16) ocupa 1 coluna; card horizontal (16:9) ocupa 2
// colunas adjacentes. A proporção vem do post (pôster → retrato; só frame → horizontal).
private const val GRID_COLUMNS = 5
private val GRID_GAP = 12.dp
private val CARD_TITLE_H = 56.dp

@Composable
private fun MasonryMediaGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    hasMore: Boolean,
    focusRequesterFor: (String) -> FocusRequester,
    onCardFocused: (String) -> Unit,
    onCardClick: (MediaCardUi) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            items.forEach { media ->
                val requester = focusRequesterFor(media.mediaId)
                MediaCard(
                    media = media,
                    showCover = showCovers,
                    modifier = Modifier
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) onCardFocused(media.mediaId) },
                    onClick = { onCardClick(media) }
                )
            }
            if (hasMore) LoadMoreCard(onClick = onLoadMore)
        }
    ) { measurables, constraints ->
        val gapPx = GRID_GAP.roundToPx()
        val titlePx = CARD_TITLE_H.roundToPx()
        val totalW = constraints.maxWidth
        val colW = ((totalW - gapPx * (GRID_COLUMNS - 1)) / GRID_COLUMNS).coerceAtLeast(1)
        val colHeights = IntArray(GRID_COLUMNS)
        val placed = ArrayList<Triple<Placeable, Int, Int>>(measurables.size)

        measurables.forEachIndexed { i, measurable ->
            val media = items.getOrNull(i)
            val isLoadMore = media == null // último filho quando hasMore
            // Retrato quando: capas OFF, ou há pôster. Horizontal (2 col) só com capa e sem pôster.
            val landscape = !isLoadMore && showCovers && media?.posterPath == null
            val span = if (landscape) 2 else 1
            val wPx = if (span == 2) colW * 2 + gapPx else colW
            val hPx = when {
                isLoadMore -> colW // bloco compacto para preencher o menor vão do fim
                landscape -> (wPx * 9f / 16f).roundToInt() + titlePx
                else -> (wPx * 16f / 9f).roundToInt() + titlePx
            }

            // Escolhe a posição de menor altura (empata → mais à esquerda), preenchendo vãos.
            val startCol = if (span == 1) {
                (0 until GRID_COLUMNS).minByOrNull { colHeights[it] } ?: 0
            } else {
                (0 until GRID_COLUMNS - 1).minByOrNull { maxOf(colHeights[it], colHeights[it + 1]) } ?: 0
            }
            val y = if (span == 1) colHeights[startCol]
                    else maxOf(colHeights[startCol], colHeights[startCol + 1])
            val x = startCol * (colW + gapPx)

            val placeable = measurable.measure(Constraints.fixed(wPx, hPx))
            placed.add(Triple(placeable, x, y))

            val bottom = y + hPx + gapPx
            if (span == 1) {
                colHeights[startCol] = bottom
            } else {
                colHeights[startCol] = bottom
                colHeights[startCol + 1] = bottom
            }
        }

        val totalH = colHeights.maxOrNull() ?: 0
        layout(totalW, totalH) {
            placed.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}

@Composable
private fun MediaCard(
    media: MediaCardUi,
    showCover: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Com pôster → capa retrato (9:16). Sem pôster (capas ON) → frame 16:9. Capas OFF → placeholder.
    val cover = if (showCover) (media.posterPath ?: media.thumbnailPath) else null
    Box(
        modifier = modifier
            .fillMaxSize()
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Capa com selos de resolução/duração e progresso (preenche o espaço acima do título).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1C1C1C))
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
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

                resolutionLabel(media.videoHeight)?.let { label ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${media.durationSeconds / 60} min",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

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
            }

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
        Column(
            modifier = Modifier
                .width(680.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Escolher canal", style = MaterialTheme.typography.titleLarge, color = Color.White)
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
            Button(onClick = onDismiss) { Text("Fechar") }
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
