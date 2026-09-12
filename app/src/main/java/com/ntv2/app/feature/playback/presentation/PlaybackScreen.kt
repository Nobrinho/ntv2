package com.ntv2.app.feature.playback.presentation

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sd
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import com.ntv2.app.core.player.PlaybackState
import kotlinx.coroutines.delay

private const val DPAD_SEEK_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 6_000L

private enum class TrackPicker { Audio, Subtitle }

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
    val scrubberFocus = remember { FocusRequester() }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    // Mantém a tela ligada durante a reprodução (evita o protetor de tela do Fire TV).
    val view = LocalView.current
    val keepScreenOn = !state.isPlaceholderMode && state.snapshot.isPlaying
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

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

    // Barra de controles estilo TV (abas + linha de tempo + ícones). Aparece ao interagir e some
    // sozinha após alguns segundos durante a reprodução; permanece visível quando pausado.
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsNonce by remember { mutableStateOf(0) }
    fun reveal() {
        if (state.isPlaceholderMode) return
        controlsVisible = true
        controlsNonce++
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { scrubberFocus.requestFocus() }
        else runCatching { videoFocusRequester.requestFocus() }
    }
    LaunchedEffect(controlsNonce, controlsVisible, state.snapshot.isPlaying) {
        if (controlsVisible && state.snapshot.isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    // Foco inicial no vídeo.
    LaunchedEffect(Unit) { runCatching { videoFocusRequester.requestFocus() } }

    // Feedback central de seek (segundos acumulados na "rajada" de ← / →); some após ~1s.
    var seekFeedbackMs by remember { mutableStateOf(0L) }
    var seekNonce by remember { mutableStateOf(0) }
    LaunchedEffect(seekNonce) {
        if (seekNonce > 0) {
            delay(900)
            seekFeedbackMs = 0L
        }
    }

    // Seletor de faixa (áudio/legenda) aberto sobre o player.
    var trackPicker by remember { mutableStateOf<TrackPicker?>(null) }

    fun seekBy(delta: Long) {
        if (state.isPlaceholderMode) return
        viewModel.onAction(PlayerScreenAction.SeekBy(delta))
        seekFeedbackMs += delta
        seekNonce++
        controlsNonce++
    }

    fun togglePlay() {
        if (state.isPlaceholderMode) return
        viewModel.onAction(
            if (state.snapshot.isPlaying) PlayerScreenAction.Pause else PlayerScreenAction.Play
        )
    }

    // Back fecha a barra de controles em vez de sair, quando ela está visível.
    BackHandler(enabled = controlsVisible) { controlsVisible = false }

    Box(modifier = Modifier.fillMaxSize()) {
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
                controlsVisible = controlsVisible,
                seekFeedbackMs = seekFeedbackMs,
                onReveal = { reveal() },
                onSeek = { delta -> seekBy(delta); reveal() },
                onToggle = { togglePlay(); reveal() }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.snapshot.state is PlaybackState.Error) {
                    Button(onClick = { viewModel.onAction(PlayerScreenAction.Retry) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tentar novamente")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Linha de metadados: Ano • Duração • Gêneros • Áudio.
                    val d = state.details
                    val metaLine = listOfNotNull(
                        d?.year?.toString(),
                        "${state.durationSeconds / 60} min",
                        d?.genres,
                        d?.audio
                    ).joinToString("  •  ")
                    if (metaLine.isNotBlank()) {
                        Text(metaLine, color = Color(0xFFB0B0B0), style = MaterialTheme.typography.bodyMedium)
                    }
                    d?.director?.let { Text("Diretor: $it", color = Color(0xFFB0B0B0), style = MaterialTheme.typography.bodyMedium) }
                    d?.synopsis?.let {
                        Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Canal: ${state.channelName}", color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Voltar")
                }
            }
        }

        // Título do filme no topo, junto com os controles (estilo player de TV).
        if (controlsVisible && state.title.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Text(
                    text = state.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Barra de controles inferior (abas / linha de tempo / ícones).
        if (controlsVisible) {
            PlayerControlBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                tracks = state.snapshot.tracks,
                isPlaying = state.snapshot.isPlaying,
                positionMs = positionMs,
                bufferedMs = state.snapshot.bufferedPositionMs,
                durationMs = durationMs,
                scrubberFocus = scrubberFocus,
                onInteract = { controlsNonce++ },
                onSeek = { delta -> seekBy(delta) },
                onRestart = { seekBy(-positionMs) },
                onToggle = { togglePlay(); controlsNonce++ },
                onOpenAudio = { trackPicker = TrackPicker.Audio },
                onOpenSubtitle = { trackPicker = TrackPicker.Subtitle }
            )
        }

        trackPicker?.let { picker ->
            val tracks = state.snapshot.tracks
            TrackPickerOverlay(
                title = if (picker == TrackPicker.Audio) "Áudio" else "Legenda",
                options = if (picker == TrackPicker.Audio) tracks.audios else tracks.subtitles,
                allowOff = picker == TrackPicker.Subtitle,
                onSelect = { id ->
                    when (picker) {
                        TrackPicker.Audio -> id?.let { viewModel.onAction(PlayerScreenAction.SelectAudio(it)) }
                        TrackPicker.Subtitle -> viewModel.onAction(PlayerScreenAction.SelectSubtitle(id))
                    }
                    trackPicker = null
                    controlsNonce++
                },
                onDismiss = { trackPicker = null; controlsNonce++ }
            )
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
    controlsVisible: Boolean,
    seekFeedbackMs: Long,
    onReveal: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onReveal(); true }
                    Key.DirectionLeft -> { onSeek(-DPAD_SEEK_MS); true }
                    Key.DirectionRight -> { onSeek(DPAD_SEEK_MS); true }
                    Key.DirectionCenter, Key.Enter -> { onToggle(); true }
                    // ↓ deixa o foco descer para a seção de informações (rolagem).
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

/**
 * Barra de controles no estilo player de TV: linha de "abas" (resolução/áudio/legenda), linha de
 * tempo com knob e os tempos nas pontas, e uma linha de ícones (reiniciar, play/pausa, áudio).
 */
@Composable
private fun PlayerControlBar(
    modifier: Modifier,
    tracks: MediaTracksInfo,
    isPlaying: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    scrubberFocus: FocusRequester,
    onInteract: () -> Unit,
    onSeek: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE6000000))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Linha superior: abas (resolução exibida; áudio/legenda acionáveis).
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            resolutionLabel(tracks.videoHeight)?.let { label ->
                TabItem(
                    icon = if (tracks.videoHeight >= 700) Icons.Filled.Hd else Icons.Filled.Sd,
                    label = label,
                    focusable = false,
                    onClick = {}
                )
            }
            if (tracks.audios.size > 1) {
                TabItem(
                    icon = Icons.Filled.Audiotrack,
                    label = "Áudio",
                    onClick = { onOpenAudio(); onInteract() }
                )
            }
            if (tracks.subtitles.isNotEmpty()) {
                val sel = tracks.subtitles.firstOrNull { it.isSelected }
                TabItem(
                    icon = Icons.Filled.ClosedCaption,
                    label = sel?.label ?: "Legenda",
                    onClick = { onOpenSubtitle(); onInteract() }
                )
            }
        }

        // Linha do tempo: decorrido — barra com knob — total.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Scrubber(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(scrubberFocus),
                positionMs = positionMs,
                bufferedMs = bufferedMs,
                durationMs = durationMs,
                onSeek = { delta -> onSeek(delta); onInteract() }
            )
            Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }

        // Linha inferior: ícones de ação.
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconControlButton(Icons.Filled.Replay, "Reiniciar") { onRestart() }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = 1.dp, height = 20.dp)
                    .background(Color(0x33FFFFFF))
            )
            IconControlButton(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (isPlaying) "Pausar" else "Reproduzir"
            ) { onToggle() }
            if (tracks.audios.size > 1) {
                IconControlButton(Icons.Filled.Audiotrack, "Áudio") { onOpenAudio(); onInteract() }
            }
        }
    }
}

@Composable
private fun TabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    focusable: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val base = Modifier
        .clip(RoundedCornerShape(8.dp))
        .then(if (focused) Modifier.background(Color(0x33FFFFFF)) else Modifier)
        .padding(horizontal = 10.dp, vertical = 6.dp)
    val interactive = if (focusable) {
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = interactive.then(base),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun IconControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0x22FFFFFF)),
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

/** Linha do tempo com buffer, progresso, marcadores de segmento e knob. ← / → dão seek. */
@Composable
private fun Scrubber(
    modifier: Modifier,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val played = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier
            .height(28.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-DPAD_SEEK_MS); true }
                    Key.DirectionRight -> { onSeek(DPAD_SEEK_MS); true }
                    else -> false
                }
            }
            .focusable()
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val trackH = 6.dp.toPx()
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            // Trilho
            drawRoundRect(
                color = Color(0x40FFFFFF),
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w, trackH),
                cornerRadius = radius
            )
            // Buffer
            if (buffered > 0f) {
                drawRoundRect(
                    color = Color(0x66FFFFFF),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(w * buffered, trackH),
                    cornerRadius = radius
                )
            }
            // Reproduzido
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w * played, trackH),
                cornerRadius = radius
            )
            // Marcadores de segmento (visual, estilo capítulos).
            val segments = 5
            val gap = 2.dp.toPx()
            for (i in 1 until segments) {
                val x = w * i / segments
                drawRect(
                    color = Color(0xFF000000),
                    topLeft = Offset(x - gap / 2f, cy - trackH / 2f),
                    size = Size(gap, trackH)
                )
            }
            // Knob
            val knobR = if (focused) 10.dp.toPx() else 7.dp.toPx()
            drawCircle(color = Color.White, radius = knobR, center = Offset(w * played, cy))
        }
    }
}

@Composable
private fun TrackPickerOverlay(
    title: String,
    options: List<MediaTrackOption>,
    allowOff: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Column(
                modifier = Modifier.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allowOff) {
                    val noneSelected = options.none { it.isSelected }
                    TrackRow(
                        label = "Desligar",
                        selected = noneSelected,
                        modifier = Modifier.focusRequester(firstFocus),
                        onClick = { onSelect(null) }
                    )
                }
                options.forEachIndexed { index, option ->
                    val mod = if (!allowOff && index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    TrackRow(
                        label = option.label,
                        selected = option.isSelected,
                        modifier = mod,
                        onClick = { onSelect(option.id) }
                    )
                }
            }
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (selected) "● $label" else label,
            color = if (selected) Color.White else Color(0xFFCFCFCF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Converte a altura do vídeo em rótulo comercial de resolução. */
private fun resolutionLabel(height: Int): String? = when {
    height <= 0 -> null
    height >= 2000 -> "4K"
    height >= 1000 -> "1080p"
    height >= 700 -> "720p"
    height >= 460 -> "480p"
    else -> "SD"
}

/** Rótulo do estado exibido junto do loading durante a reprodução (null = não mostrar). */
private fun loadingLabel(playbackState: PlaybackState): String? = when (playbackState) {
    PlaybackState.Preparing -> "Preparando…"
    PlaybackState.Buffering -> "Armazenando em buffer…"
    else -> null
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
