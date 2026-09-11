package com.ntv2.app.feature.playback.presentation

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.player.PlaybackState
import kotlinx.coroutines.delay

private const val DPAD_SEEK_MS = 10_000L

@OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(
    mediaId: String,
    fileId: Int,
    title: String,
    channelName: String,
    durationSeconds: Int,
    fileName: String?,
    thumbnailPath: String?,
    viewModel: PlayerScreenViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoFocusRequester = remember { FocusRequester() }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(mediaId, fileId) {
        viewModel.onAction(
            PlayerScreenAction.Prepare(
                mediaId = mediaId,
                fileId = fileId,
                title = title,
                channelName = channelName,
                durationSeconds = durationSeconds,
                fileName = fileName,
                thumbnailPath = thumbnailPath
            )
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAction(PlayerScreenAction.OnAppStop)
                Lifecycle.Event.ON_RESUME -> viewModel.onAction(PlayerScreenAction.OnAppResume)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAction(PlayerScreenAction.Release)
        }
    }

    // Ticker de posição (o ExoPlayer só pode ser lido na Main; o snapshot não atualiza a cada segundo).
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember(durationSeconds) { mutableStateOf(durationSeconds * 1000L) }
    LaunchedEffect(state.isPlaceholderMode) {
        while (true) {
            viewModel.player?.let { p ->
                positionMs = p.currentPosition.coerceAtLeast(0L)
                if (p.duration > 0L) durationMs = p.duration
            }
            delay(500)
        }
    }

    // Foco inicial no vídeo (tela cheia). Apertar para baixo move o foco aos controles e rola a tela.
    LaunchedEffect(Unit) {
        runCatching { videoFocusRequester.requestFocus() }
    }

    // Overlay de timeline sobre o vídeo: aparece ao apertar ↑ e some sozinho após alguns segundos.
    var showTimeline by remember { mutableStateOf(false) }
    var timelineNonce by remember { mutableStateOf(0) }
    LaunchedEffect(timelineNonce) {
        if (timelineNonce > 0) {
            showTimeline = true
            delay(5000)
            showTimeline = false
        }
    }

    // Feedback central de seek (segundos acumulados na "rajada" de ← / →); some após ~1s.
    var seekFeedbackMs by remember { mutableStateOf(0L) }
    var seekNonce by remember { mutableStateOf(0) }
    LaunchedEffect(seekNonce) {
        if (seekNonce > 0) {
            delay(900)
            seekFeedbackMs = 0L
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        VideoSurface(
            heightDp = screenHeightDp,
            focusRequester = videoFocusRequester,
            isPlaceholderMode = state.isPlaceholderMode,
            thumbnailPath = state.thumbnailPath,
            title = state.title,
            playbackState = state.snapshot.state,
            statusMessage = state.statusMessage,
            player = { viewModel.player },
            showTimeline = showTimeline,
            positionMs = positionMs,
            bufferedMs = state.snapshot.bufferedPositionMs,
            durationMs = durationMs,
            seekFeedbackMs = seekFeedbackMs,
            onShowTimeline = { timelineNonce++ },
            onSeek = { delta ->
                if (!state.isPlaceholderMode) {
                    viewModel.onAction(PlayerScreenAction.SeekBy(delta))
                    seekFeedbackMs += delta
                    seekNonce++
                }
            },
            onToggle = {
                if (!state.isPlaceholderMode) {
                    viewModel.onAction(
                        if (state.snapshot.isPlaying) PlayerScreenAction.Pause else PlayerScreenAction.Play
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.isPlaceholderMode) {
                PlaybackProgress(
                    positionMs = positionMs,
                    bufferedMs = state.snapshot.bufferedPositionMs,
                    durationMs = durationMs
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isPlaying = state.snapshot.isPlaying
                    Button(
                        onClick = {
                            viewModel.onAction(
                                if (isPlaying) PlayerScreenAction.Pause else PlayerScreenAction.Play
                            )
                        }
                    ) {
                        Text(if (isPlaying) "Pausar" else "Reproduzir")
                    }
                    Button(onClick = { viewModel.onAction(PlayerScreenAction.SeekBy(-5 * 60_000L)) }) {
                        Text("-5 min")
                    }
                    Button(onClick = { viewModel.onAction(PlayerScreenAction.SeekBy(5 * 60_000L)) }) {
                        Text("+5 min")
                    }
                }
            }

            if (state.snapshot.state is PlaybackState.Error) {
                Button(onClick = { viewModel.onAction(PlayerScreenAction.Retry) }) {
                    Text("Tentar novamente")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Canal: ${state.channelName}", color = Color.White)
                Text("Título: ${state.title}", color = Color.White)
                Text("Duração: ${state.durationSeconds / 60} min", color = Color.White)
                Text("Arquivo: ${state.fileName ?: "-"}", color = Color.White)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Estado: ${state.statusMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0)
                )
                Text(
                    "Estado técnico: ${state.snapshot.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0)
                )
            }

            Button(onClick = onBack) {
                Text("Voltar")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    heightDp: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester,
    isPlaceholderMode: Boolean,
    thumbnailPath: String?,
    title: String,
    playbackState: PlaybackState,
    statusMessage: String,
    player: () -> Player?,
    showTimeline: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    seekFeedbackMs: Long,
    onShowTimeline: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                // Com o vídeo em foco (tela cheia): ↑ mostra a timeline; ← / → dão seek de 10s
                // (e também revelam a timeline). ↓ deixa o foco descer para os controles.
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        onShowTimeline()
                        true
                    }
                    Key.DirectionLeft -> {
                        onSeek(-DPAD_SEEK_MS)
                        onShowTimeline()
                        true
                    }
                    Key.DirectionRight -> {
                        onSeek(DPAD_SEEK_MS)
                        onShowTimeline()
                        true
                    }
                    else -> false
                }
            }
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (!isPlaceholderMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                },
                update = { view -> view.player = player() },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize()
            )
            // Spinner + rótulo do estado enquanto prepara/armazena em buffer.
            loadingLabel(playbackState)?.let { label ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Título do filme no canto superior esquerdo (com a timeline), estilo YouTube.
            if (showTimeline && title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            // Overlay da timeline (aparece ao apertar ↑).
            if (showTimeline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    PlaybackProgress(
                        positionMs = positionMs,
                        bufferedMs = bufferedMs,
                        durationMs = durationMs
                    )
                }
            }
            // Feedback central de seek: seta + segundos acumulados.
            if (seekFeedbackMs != 0L) {
                val seconds = kotlin.math.abs(seekFeedbackMs) / 1000
                val label = if (seekFeedbackMs < 0L) "◀◀  ${seconds}s" else "${seconds}s  ▶▶"
                Box(
                    modifier = Modifier
                        .background(Color(0xB3000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            if (!thumbnailPath.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailPath,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(statusMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Rótulo do estado exibido junto do loading durante a reprodução (null = não mostrar). */
private fun loadingLabel(playbackState: PlaybackState): String? = when (playbackState) {
    PlaybackState.Preparing -> "Preparando…"
    PlaybackState.Buffering -> "Armazenando em buffer…"
    else -> null
}

@Composable
private fun PlaybackProgress(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long
) {
    val played = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered)
                    .height(6.dp)
                    .background(Color(0x55FFFFFF))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(played)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
