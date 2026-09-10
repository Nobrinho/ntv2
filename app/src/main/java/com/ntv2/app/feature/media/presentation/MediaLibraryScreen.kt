package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.runtime.DisposableEffect
import androidx.tv.material3.Button
import androidx.tv.material3.Card
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(MediaLibraryAction.ScreenResumed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        initialActionsFocus.requestFocus()
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
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(initialActionsFocus),
                value = state.searchQuery,
                onValueChange = { viewModel.onAction(MediaLibraryAction.SearchChanged(it)) },
                label = { Text("Buscar por título, caption ou arquivo", color = Color.White) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true
            )
            Button(onClick = { viewModel.onAction(MediaLibraryAction.Refresh) }) {
                Text("Atualizar")
            }
            Button(onClick = onOpenSettings) {
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
}

@Composable
private fun LoadMoreCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, Color(0x44FFFFFF)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .height(220.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Carregar mais")
        }
    }
}

@Composable
private fun MediaCard(
    media: MediaCardUi,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .border(1.dp, Color(0x44FFFFFF)),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                model = media.thumbnailPath,
                contentDescription = media.title
            )
            if (media.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(media.progress)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Text(media.title, maxLines = 2)
            Text("${media.durationSeconds / 60} min")
        }
    }
}
