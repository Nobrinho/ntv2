package com.ntv2.app.feature.media.presentation

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
import androidx.compose.ui.layout.ContentScale
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
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.MediaLibraryEmptyState
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryAction
import com.ntv2.app.feature.media.presentation.viewmodel.MediaLibraryViewModel

@Composable
fun MediaLibraryScreen(
    viewModel: MediaLibraryViewModel,
    onOpenSettings: () -> Unit,
    onOpenChannels: () -> Unit,
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
    // Busca no estilo TV do YouTube: a barra é apenas um botão; ao apertar OK abre um teclado
    // próprio (grade de teclas navegável por D-pad). Sem teclado do sistema, sem disputa de foco.
    var searching by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Biblioteca", style = MaterialTheme.typography.headlineMedium, color = Color.White)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SearchBar(
                    query = state.searchQuery,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(initialActionsFocus),
                    onClick = { searching = true }
                )
                // Só aparece com busca ativa: limpa o filtro sem reabrir o teclado e devolve o
                // foco à barra (o botão some ao limpar, então precisa realocar o foco).
                if (state.searchQuery.isNotBlank()) {
                    Button(onClick = {
                        viewModel.onAction(MediaLibraryAction.SearchChanged(""))
                        initialActionsFocus.requestFocus()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Limpar")
                    }
                }
                Button(onClick = { viewModel.onAction(MediaLibraryAction.Refresh) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Atualizar")
                }
                Button(onClick = onOpenChannels) {
                    Icon(Icons.Filled.Subscriptions, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Canais")
                }
                Button(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configurações")
                }
            }

            Text("Filtro mínimo: ${state.minDurationMinutes} min", color = Color.White)

            when {
                state.isLoading -> {
                    Text("Carregando biblioteca...", color = Color.White)
                }

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
                    val message = when (val emptyState = state.emptyState) {
                        MediaLibraryEmptyState.NoChannelsSelected -> "Nenhum canal selecionado"
                        MediaLibraryEmptyState.NoVideosFound -> "Nenhum vídeo encontrado para o filtro atual"
                        MediaLibraryEmptyState.NoSearchResults -> "Nenhum resultado para a busca"
                        null -> ""
                    }
                    Text(message, color = Color.White)
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.sections, key = { it.channelId }) { section ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(section.channelName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(section.items, key = { it.mediaId }) { media ->
                                        val requester = cardFocusRequesters.getOrPut(media.mediaId) { FocusRequester() }
                                        MediaCard(
                                            media = media,
                                            modifier = Modifier
                                                .focusRequester(requester)
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        viewModel.onAction(MediaLibraryAction.VideoFocused(media.mediaId))
                                                    }
                                                },
                                            onClick = {
                                                viewModel.onAction(MediaLibraryAction.OpenVideo(media))
                                            }
                                        )
                                    }
                                    if (section.hasMore) {
                                        item(key = "load_more_${section.channelId}") {
                                            LoadMoreCard(
                                                onClick = {
                                                    viewModel.onAction(
                                                        MediaLibraryAction.LoadMoreChannel(section.channelId)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (searching) {
            val resultCount = state.sections.sumOf { it.items.size }
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
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x11FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x44FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .height(220.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Carregar mais", color = Color.White)
        }
    }
}

@Composable
private fun MediaCard(
    media: MediaCardUi,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Sem escala de foco (que estourava/cortava o card na lista): destaque por borda + fundo.
    Box(
        modifier = modifier
            .width(300.dp)
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
        Column {
            // Miniatura 16:9 com badge de duração e barra de progresso sobrepostos.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(Color(0xFF1C1C1C))
            ) {
                if (media.thumbnailPath != null) {
                    AsyncImage(
                        model = media.thumbnailPath,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Sem miniatura ainda: placeholder discreto.
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

                // Selo de resolução no canto superior esquerdo (4K/1080p/...).
                resolutionLabel(media.videoHeight)?.let { label ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Badge de duração no canto inferior direito.
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

                // Barra de progresso sobre a base da miniatura.
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
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(48.dp)
            )
        }
    }
}
