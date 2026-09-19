package com.ntv2.app.feature.playback.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import com.ntv2.app.core.player.PlaybackState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val DPAD_SEEK_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 6_000L
private const val GESTURE_ADJUSTMENT_TIMEOUT_MS = 900L
// Seek do dpad é acumulado e só aplicado ao player após essa pausa sem teclas.
private const val SEEK_COMMIT_DELAY_MS = 500L
private const val BACK_EXIT_WINDOW_MS = 2_000L

/** Passo do seek do dpad com aceleração conforme a tecla é segurada (repetições). */
private fun dpadSeekStep(event: KeyEvent): Long {
    val repeat = event.nativeKeyEvent.repeatCount
    return when {
        repeat < 10 -> DPAD_SEEK_MS
        repeat < 30 -> 30_000L
        else -> 60_000L
    }
}

private enum class TrackPicker { Audio, Subtitle, Settings }
private enum class PlayerGestureTarget { Volume, Brightness }

private data class GestureAdjustment(
    val target: PlayerGestureTarget,
    val percent: Int
)

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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val adaptive = rememberAdaptiveLayoutInfo()
    val videoIsFullscreen = !(adaptive.usePhoneLayout && adaptive.isPortrait)
    val compactControls = adaptive.usePhoneLayout && adaptive.isPortrait
    val videoHeightDp = if (adaptive.usePhoneLayout && adaptive.isPortrait) {
        configuration.screenHeightDp.dp
    } else {
        configuration.screenHeightDp.dp
    }

    DisposableEffect(activity, view) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            if (window != null) {
                originalBrightness?.let { brightness ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = brightness
                    }
                }
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Mantém a tela ligada durante a reprodução (evita o protetor de tela do Fire TV).
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
    // Alvo do seek em andamento (rajada de ← / →); aplicado ao player após SEEK_COMMIT_DELAY_MS.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekNonce) {
        if (seekNonce > 0) {
            delay(SEEK_COMMIT_DELAY_MS)
            pendingSeekMs?.let { target ->
                viewModel.onAction(PlayerScreenAction.SeekTo(target))
                positionMs = target
            }
            pendingSeekMs = null
            delay(400)
            seekFeedbackMs = 0L
        }
    }
    val displayPositionMs = pendingSeekMs ?: positionMs

    // "Voltar" duas vezes para sair (TV), quando os controles estão ocultos.
    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(backArmed) {
        if (backArmed) {
            delay(BACK_EXIT_WINDOW_MS)
            backArmed = false
        }
    }

    // Seletor de faixa (legenda) aberto sobre o player.
    var trackPicker by remember { mutableStateOf<TrackPicker?>(null) }
    // Sub-seletor (áudio/legenda) aberto a partir de Opções: Voltar retorna para Opções.
    var pickerFromSettings by remember { mutableStateOf(false) }
    // Ao fechar o modal, devolve o foco ao botão Opções (ou à linha do tempo, se indisponível).
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(trackPicker) {
        if (trackPicker == null && controlsVisible) {
            if (runCatching { settingsFocus.requestFocus() }.isFailure) {
                runCatching { scrubberFocus.requestFocus() }
            }
        }
    }

    // Gestos verticais em tela cheia: esquerda controla volume, direita controla brilho.
    var gestureAdjustment by remember { mutableStateOf<GestureAdjustment?>(null) }
    var gestureNonce by remember { mutableStateOf(0) }
    var activeGestureTarget by remember { mutableStateOf<PlayerGestureTarget?>(null) }
    var gestureVolumeLevel by remember { mutableStateOf(0f) }
    var gestureBrightnessLevel by remember { mutableStateOf(0.5f) }
    LaunchedEffect(gestureNonce) {
        if (gestureNonce > 0) {
            delay(GESTURE_ADJUSTMENT_TIMEOUT_MS)
            gestureAdjustment = null
        }
    }

    fun showGestureAdjustment(target: PlayerGestureTarget, percent: Int) {
        gestureAdjustment = GestureAdjustment(target, percent.coerceIn(0, 100))
        gestureNonce++
    }

    fun startVerticalAdjustment(x: Float, width: Float) {
        if (state.isPlaceholderMode || !videoIsFullscreen || controlsVisible || width <= 0f) return
        val target = if (x < width / 2f) PlayerGestureTarget.Volume else PlayerGestureTarget.Brightness
        activeGestureTarget = target
        when (target) {
            PlayerGestureTarget.Volume -> {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                gestureVolumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                showGestureAdjustment(
                    target = target,
                    percent = ((gestureVolumeLevel / maxVolume) * 100f).roundToInt()
                )
            }
            PlayerGestureTarget.Brightness -> {
                gestureBrightnessLevel = activity?.window?.attributes?.screenBrightness
                    ?.takeIf { it >= 0f }
                    ?: 0.5f
                showGestureAdjustment(
                    target = target,
                    percent = (gestureBrightnessLevel * 100f).roundToInt()
                )
            }
        }
    }

    fun adjustVerticalGesture(deltaY: Float, height: Float) {
        val target = activeGestureTarget ?: return
        if (height <= 0f) return
        val fractionDelta = -deltaY / height
        when (target) {
            PlayerGestureTarget.Volume -> {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                gestureVolumeLevel = (gestureVolumeLevel + fractionDelta * maxVolume * 1.35f)
                    .coerceIn(0f, maxVolume.toFloat())
                val newVolume = gestureVolumeLevel.roundToInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                showGestureAdjustment(target, ((newVolume.toFloat() / maxVolume) * 100f).roundToInt())
            }
            PlayerGestureTarget.Brightness -> {
                gestureBrightnessLevel = (gestureBrightnessLevel + fractionDelta * 1.15f)
                    .coerceIn(0.05f, 1f)
                activity?.window?.let { window ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = gestureBrightnessLevel
                    }
                }
                showGestureAdjustment(target, (gestureBrightnessLevel * 100f).roundToInt())
            }
        }
    }

    fun finishVerticalAdjustment() {
        activeGestureTarget = null
    }

    fun seekBy(delta: Long) {
        if (state.isPlaceholderMode) return
        val base = pendingSeekMs ?: viewModel.player?.currentPosition ?: positionMs
        val max = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        val target = (base + delta).coerceIn(0L, max)
        seekFeedbackMs += target - base
        pendingSeekMs = target
        seekNonce++
        controlsNonce++
    }

    fun seekTo(position: Long) {
        if (state.isPlaceholderMode) return
        pendingSeekMs = null
        viewModel.onAction(PlayerScreenAction.SeekTo(position))
        positionMs = position
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
    BackHandler(enabled = adaptive.isTv && !controlsVisible && trackPicker == null) {
        if (backArmed) onBack() else backArmed = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Qualquer tecla com os controles visíveis reinicia o timer de ocultar.
                if (controlsVisible) controlsNonce++
                if (event.key != Key.Back) backArmed = false
                // Teclas de mídia do controle remoto.
                when (event.key) {
                    Key.MediaPlayPause -> { togglePlay(); reveal(); true }
                    Key.MediaPlay -> {
                        if (!state.snapshot.isPlaying) togglePlay()
                        reveal(); true
                    }
                    Key.MediaPause -> {
                        if (state.snapshot.isPlaying) togglePlay()
                        reveal(); true
                    }
                    Key.MediaFastForward -> { seekBy(dpadSeekStep(event)); reveal(); true }
                    Key.MediaRewind -> { seekBy(-dpadSeekStep(event)); reveal(); true }
                    else -> false
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(videoHeightDp)
            ) {
                VideoSurface(
                    modifier = Modifier.fillMaxSize(),
                    focusRequester = videoFocusRequester,
                    isPlaceholderMode = state.isPlaceholderMode,
                    thumbnailPath = state.thumbnailPath,
                    title = state.title,
                    playbackState = state.snapshot.state,
                    statusMessage = state.statusMessage,
                    player = { viewModel.player },
                    // Com os controles visíveis a linha do tempo já mostra o alvo; evita sobrepor o play.
                    seekFeedbackMs = if (controlsVisible) 0L else seekFeedbackMs,
                    verticalAdjustmentEnabled = videoIsFullscreen && !controlsVisible,
                    onReveal = { reveal() },
                    onSeek = { delta -> seekBy(delta); reveal() },
                    onToggle = { togglePlay(); reveal() },
                    onVerticalAdjustmentStart = ::startVerticalAdjustment,
                    onVerticalAdjustment = ::adjustVerticalGesture,
                    onVerticalAdjustmentEnd = ::finishVerticalAdjustment
                )

                gestureAdjustment?.let { adjustment ->
                    GestureAdjustmentOverlay(
                        modifier = Modifier.fillMaxSize(),
                        adjustment = adjustment
                    )
                }

                if (controlsVisible) {
                    StreamingControlsOverlay(
                        modifier = Modifier.fillMaxSize(),
                        title = state.title,
                        tracks = state.snapshot.tracks,
                        isPlaying = state.snapshot.isPlaying,
                        positionMs = displayPositionMs,
                        bufferedMs = state.snapshot.bufferedPositionMs,
                        durationMs = durationMs,
                        scrubberFocus = scrubberFocus,
                        settingsFocus = settingsFocus,
                        compact = compactControls,
                        isTv = adaptive.isTv,
                        showFullscreen = compactControls,
                        onBack = onBack,
                        onDismiss = { controlsVisible = false },
                        onInteract = { controlsNonce++ },
                        onSeek = { delta -> seekBy(delta) },
                        onSeekTo = { position -> seekTo(position) },
                        onRestart = { seekBy(-displayPositionMs) },
                        onToggle = { togglePlay(); controlsNonce++ },
                        onOpenAudio = { pickerFromSettings = false; trackPicker = TrackPicker.Audio },
                        onOpenSubtitle = { pickerFromSettings = false; trackPicker = TrackPicker.Subtitle },
                        onToggleSubtitle = {
                            val subtitles = state.snapshot.tracks.subtitles
                            if (subtitles.isNotEmpty()) {
                                val selected = subtitles.firstOrNull { it.isSelected }
                                viewModel.onAction(PlayerScreenAction.SelectSubtitle(if (selected == null) subtitles.first().id else null))
                                controlsNonce++
                            }
                        },
                        onOpenSettings = { trackPicker = TrackPicker.Settings },
                        onFullscreen = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            controlsNonce++
                        }
                    )
                }
            }
        }

        if (backArmed) {
            Text(
                text = "Pressione Voltar novamente para sair",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        trackPicker?.let { picker ->
            val tracks = state.snapshot.tracks
            when (picker) {
                TrackPicker.Settings -> PlayerSettingsOverlay(
                    tracks = tracks,
                    onOpenAudio = { pickerFromSettings = true; trackPicker = TrackPicker.Audio; controlsNonce++ },
                    onOpenSubtitle = { pickerFromSettings = true; trackPicker = TrackPicker.Subtitle; controlsNonce++ },
                    onDismiss = { trackPicker = null; controlsNonce++ }
                )
                TrackPicker.Audio,
                TrackPicker.Subtitle -> TrackPickerOverlay(
                    title = if (picker == TrackPicker.Audio) "Áudio" else "Legendas",
                    options = if (picker == TrackPicker.Audio) tracks.audios else tracks.subtitles,
                    allowOff = picker == TrackPicker.Subtitle,
                    onSelect = { id ->
                        when (picker) {
                            TrackPicker.Audio -> id?.let { viewModel.onAction(PlayerScreenAction.SelectAudio(it)) }
                            TrackPicker.Subtitle -> viewModel.onAction(PlayerScreenAction.SelectSubtitle(id))
                            TrackPicker.Settings -> Unit
                        }
                        trackPicker = null
                        pickerFromSettings = false
                        controlsNonce++
                    },
                    onDismiss = {
                        trackPicker = if (pickerFromSettings) TrackPicker.Settings else null
                        pickerFromSettings = false
                        controlsNonce++
                    }
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    modifier: Modifier,
    focusRequester: FocusRequester,
    isPlaceholderMode: Boolean,
    thumbnailPath: String?,
    title: String,
    playbackState: PlaybackState,
    statusMessage: String,
    player: () -> Player?,
    seekFeedbackMs: Long,
    verticalAdjustmentEnabled: Boolean,
    onReveal: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onVerticalAdjustmentStart: (Float, Float) -> Unit,
    onVerticalAdjustment: (Float, Float) -> Unit,
    onVerticalAdjustmentEnd: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown -> { onReveal(); true }
                    Key.DirectionLeft -> { onSeek(-dpadSeekStep(event)); true }
                    Key.DirectionRight -> { onSeek(dpadSeekStep(event)); true }
                    Key.DirectionCenter, Key.Enter -> { onToggle(); true }
                    else -> false
                }
            }
            .pointerInput(verticalAdjustmentEnabled) {
                if (verticalAdjustmentEnabled) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            onVerticalAdjustmentStart(offset.x, size.width.toFloat())
                        },
                        onVerticalDrag = { change, dragAmount ->
                            onVerticalAdjustment(dragAmount, size.height.toFloat())
                            change.consume()
                        },
                        onDragEnd = onVerticalAdjustmentEnd,
                        onDragCancel = onVerticalAdjustmentEnd
                    )
                }
            }
            .clickable { onReveal() },
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

@Composable
private fun GestureAdjustmentOverlay(
    modifier: Modifier,
    adjustment: GestureAdjustment
) {
    val alignment = when (adjustment.target) {
        PlayerGestureTarget.Volume -> Alignment.CenterStart
        PlayerGestureTarget.Brightness -> Alignment.CenterEnd
    }
    val label = when (adjustment.target) {
        PlayerGestureTarget.Volume -> "Volume"
        PlayerGestureTarget.Brightness -> "Brilho"
    }
    val icon = when (adjustment.target) {
        PlayerGestureTarget.Volume -> Icons.AutoMirrored.Filled.VolumeUp
        PlayerGestureTarget.Brightness -> Icons.Filled.Brightness6
    }
    val fill = adjustment.percent / 100f

    Box(
        modifier = modifier.padding(horizontal = 28.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .width(86.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xB3000000))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            Box(
                modifier = Modifier
                    .height(96.dp)
                    .width(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(fill.coerceIn(0f, 1f))
                        .fillMaxWidth()
                        .background(Color.White)
                )
            }
            Text(
                text = "${adjustment.percent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun StreamingControlsOverlay(
    modifier: Modifier,
    title: String,
    tracks: MediaTracksInfo,
    isPlaying: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    scrubberFocus: FocusRequester,
    settingsFocus: FocusRequester,
    compact: Boolean,
    isTv: Boolean,
    showFullscreen: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreen: () -> Unit
) {
    if (compact) {
        PortraitControlsOverlay(
            modifier = modifier,
            title = title,
            tracks = tracks,
            isPlaying = isPlaying,
            positionMs = positionMs,
            bufferedMs = bufferedMs,
            durationMs = durationMs,
            scrubberFocus = scrubberFocus,
            showFullscreen = showFullscreen,
            onBack = onBack,
            onDismiss = onDismiss,
            onInteract = onInteract,
            onSeek = onSeek,
            onSeekTo = onSeekTo,
            onRestart = onRestart,
            onToggle = onToggle,
            onOpenAudio = onOpenAudio,
            onOpenSubtitle = onOpenSubtitle,
            onToggleSubtitle = onToggleSubtitle,
            onOpenSettings = onOpenSettings,
            onFullscreen = onFullscreen
        )
        return
    }

    LandscapeControlsOverlay(
        modifier = modifier,
        title = title,
        tracks = tracks,
        isPlaying = isPlaying,
        positionMs = positionMs,
        bufferedMs = bufferedMs,
        durationMs = durationMs,
        scrubberFocus = scrubberFocus,
        settingsFocus = settingsFocus,
        isTv = isTv,
        showFullscreen = showFullscreen,
        onBack = onBack,
        onDismiss = onDismiss,
        onInteract = onInteract,
        onSeek = onSeek,
        onSeekTo = onSeekTo,
        onToggle = onToggle,
        onToggleSubtitle = onToggleSubtitle,
        onOpenSettings = onOpenSettings,
        onFullscreen = onFullscreen
    )
}

@Composable
private fun LandscapeControlsOverlay(
    modifier: Modifier,
    title: String,
    tracks: MediaTracksInfo,
    isPlaying: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    scrubberFocus: FocusRequester,
    settingsFocus: FocusRequester,
    isTv: Boolean,
    showFullscreen: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggle: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreen: () -> Unit
) {
    // Amarração de foco para o dpad da TV: barra superior <-> controles centrais <-> scrubber,
    // e a linha da barra superior (Fechar -> Legenda -> Opções).
    val topBarFocus = settingsFocus
    val centerFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val ccFocus = remember { FocusRequester() }
    val ccEnabled = tracks.subtitles.isNotEmpty()
    val settingsEnabled = tracks.audios.size > 1 || tracks.subtitles.isNotEmpty()
    // Link horizontal explícito só na TV (dpad); no celular deixa a navegação espacial/toque.
    fun Modifier.hLink(left: FocusRequester? = null, right: FocusRequester? = null): Modifier =
        if (!isTv) this else this.focusProperties {
            down = centerFocus
            left?.let { this.left = it }
            right?.let { this.right = it }
        }
    Box(
        modifier = modifier
            .background(Color(0x26000000))
            .clickable(onClick = onDismiss)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.30f)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xD9000000),
                        0.72f to Color(0x99000000),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.32f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.28f to Color(0x99000000),
                        1f to Color(0xF2000000)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 88.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { down = centerFocus },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    PortraitTopIcon(
                        Icons.Filled.Close,
                        "Fechar",
                        onBack,
                        modifier = Modifier
                            .focusRequester(closeFocus)
                            .hLink(right = if (ccEnabled) ccFocus else if (settingsEnabled) topBarFocus else null)
                    )
                    // PiP não se aplica à TV.
                    if (!isTv) {
                        PortraitTopIcon(Icons.Filled.PictureInPictureAlt, "Picture-in-picture", onDismiss)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Espelhamento/transmissão não se aplica à TV.
                    if (!isTv) {
                        PortraitTopIcon(Icons.Filled.Cast, "Transmitir", onInteract, enabled = false)
                    }
                    PortraitTopIcon(
                        Icons.Filled.ClosedCaption,
                        "Ligar ou desligar legenda",
                        { onToggleSubtitle(); onInteract() },
                        enabled = ccEnabled,
                        active = tracks.subtitles.any { it.isSelected },
                        modifier = Modifier
                            .focusRequester(ccFocus)
                            .hLink(left = closeFocus, right = if (settingsEnabled) topBarFocus else null)
                    )
                    // Velocidade (1x) removida temporariamente: função ainda não implementada.
                    PortraitTopIcon(
                        Icons.Filled.Settings,
                        "Opções",
                        { onOpenSettings(); onInteract() },
                        enabled = settingsEnabled,
                        modifier = Modifier
                            .focusRequester(topBarFocus)
                            .hLink(left = if (ccEnabled) ccFocus else closeFocus)
                    )
                }
            }

            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .focusProperties { up = if (settingsEnabled) topBarFocus else closeFocus },
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortraitSeekButton("-10") { onSeek(-DPAD_SEEK_MS); onInteract() }
            OverlayRoundButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                size = 86.dp,
                iconSize = 46.dp,
                strong = false,
                modifier = Modifier.focusRequester(centerFocus),
                onClick = { onToggle(); onInteract() }
            )
            PortraitSeekButton("+10") { onSeek(DPAD_SEEK_MS); onInteract() }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 88.dp, end = 88.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Scrubber(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .focusRequester(scrubberFocus)
                    .focusProperties { up = centerFocus },
                positionMs = positionMs,
                bufferedMs = bufferedMs,
                durationMs = durationMs,
                showTimeBubble = isTv,
                onToggle = { onToggle(); onInteract() },
                onSeek = { delta -> onSeek(delta); onInteract() },
                onSeekTo = { position -> onSeekTo(position); onInteract() }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
                    Text("-${formatTime(remaining)}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (showFullscreen) {
                        FullscreenControlButton { onFullscreen(); onInteract() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitControlsOverlay(
    modifier: Modifier,
    title: String,
    tracks: MediaTracksInfo,
    isPlaying: Boolean,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    scrubberFocus: FocusRequester,
    showFullscreen: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreen: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color(0x33000000))
            .clickable(onClick = onDismiss)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.36f)
                .background(Color(0xF2050A12))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.34f)
                .background(Color(0xF2050A12))
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PortraitTopIcon(Icons.Filled.Close, "Fechar", onBack)
                PortraitTopIcon(Icons.Filled.PictureInPictureAlt, "Picture-in-picture", onDismiss)
                PortraitTopIcon(Icons.Filled.Cast, "Transmitir", onInteract, enabled = false)
                    PortraitTopIcon(
                        Icons.Filled.ClosedCaption,
                        "Legenda",
                        { onToggleSubtitle(); onInteract() },
                        enabled = tracks.subtitles.isNotEmpty(),
                        active = tracks.subtitles.any { it.isSelected }
                    )
                // Velocidade (1x) removida temporariamente: função ainda não implementada.
                PortraitTopIcon(
                    Icons.Filled.Settings,
                    "Opções",
                    { onOpenSettings(); onInteract() },
                    enabled = tracks.audios.size > 1 || tracks.subtitles.isNotEmpty()
                )
            }

            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Scrubber(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .focusRequester(scrubberFocus),
                    positionMs = positionMs,
                    bufferedMs = bufferedMs,
                    durationMs = durationMs,
                    onToggle = { onToggle(); onInteract() },
                    onSeek = { delta -> onSeek(delta); onInteract() },
                    onSeekTo = { position -> onSeekTo(position); onInteract() }
                )
                if (showFullscreen) {
                    FullscreenControlButton { onFullscreen(); onInteract() }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val remaining = (durationMs - positionMs).coerceAtLeast(0L)
                Text("-${formatTime(remaining)}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PortraitSeekButton("-10") { onSeek(-DPAD_SEEK_MS); onInteract() }
                OverlayRoundButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    size = 86.dp,
                    iconSize = 46.dp,
                    strong = false,
                    onClick = { onToggle(); onInteract() }
                )
                PortraitSeekButton("+10") { onSeek(DPAD_SEEK_MS); onInteract() }
            }

            if (tracks.audios.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverlayChip(
                        icon = Icons.Filled.Audiotrack,
                        label = "Áudio",
                        showLabel = false
                    ) { onOpenAudio(); onInteract() }
                }
            }
        }
    }
}

@Composable
private fun PortraitTopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = true,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        focused -> Color.Black
        !enabled -> Color(0x66FFFFFF)
        active -> Color.White
        else -> Color(0x66FFFFFF)
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (focused) Color.White else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun FullscreenControlButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Fullscreen,
            contentDescription = "Tela cheia",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun PortraitTextAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (focused) Color.Black else Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun PortraitSeekButton(
    label: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OverlayRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    strong: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    strong -> Color(0xE6FFFFFF)
                    focused -> Color.White
                    else -> Color(0x66000000)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x33FFFFFF),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (strong || focused) Color.Black else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun OverlayChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    contentDescription: String = label,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val tint = if (focused) Color.Black else if (enabled) Color.White else Color(0xB3FFFFFF)
    val background = when {
        focused -> Color.White
        enabled -> Color(0x33FFFFFF)
        else -> Color(0x22000000)
    }
    val baseModifier = Modifier
        .clip(RoundedCornerShape(22.dp))
        .onFocusChanged { focused = it.isFocused }
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .background(background)
        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp))

    if (!showLabel) {
        Box(
            modifier = baseModifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        return
    }

    Row(
        modifier = baseModifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** Linha do tempo com buffer, progresso e knob. ← / → dão seek. */
@Composable
private fun Scrubber(
    modifier: Modifier,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekTo: ((Long) -> Unit)? = null,
    onToggle: (() -> Unit)? = null,
    showTimeBubble: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val played = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    fun seekAt(x: Float, width: Float) {
        if (durationMs <= 0L || width <= 0f) return
        val target = ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
        onSeekTo?.invoke(target)
    }
    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-dpadSeekStep(event)); true }
                    Key.DirectionRight -> { onSeek(dpadSeekStep(event)); true }
                    Key.DirectionCenter, Key.Enter -> {
                        if (onToggle == null) false else { onToggle(); true }
                    }
                    else -> false
                }
            }
            .pointerInput(durationMs, onSeekTo) {
                if (onSeekTo != null) {
                    detectTapGestures { offset -> seekAt(offset.x, size.width.toFloat()) }
                }
            }
            .pointerInput(durationMs, onSeekTo) {
                if (onSeekTo != null) {
                    detectDragGestures { change, _ ->
                        seekAt(change.position.x, size.width.toFloat())
                        change.consume()
                    }
                }
            }
            .focusable()
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val cy = size.height / 2f
            // Com foco (dpad), trilho mais grosso para ser legível à distância.
            val trackH = if (focused) 10.dp.toPx() else 6.dp.toPx()
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
            // Knob
            val knobR = if (focused) 13.dp.toPx() else 7.dp.toPx()
            if (focused) {
                drawCircle(color = Color(0x55FFFFFF), radius = knobR + 6.dp.toPx(), center = Offset(w * played, cy))
            }
            drawCircle(color = Color.White, radius = knobR, center = Offset(w * played, cy))
        }
        // Tempo sobre o knob enquanto a linha do tempo está focada (TV).
        if (showTimeBubble && focused) {
            val bubbleW = 96.dp
            val x = (maxWidth * played - bubbleW / 2).coerceIn(0.dp, (maxWidth - bubbleW).coerceAtLeast(0.dp))
            Text(
                text = formatTime(positionMs),
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .offset(x = x, y = (-44).dp)
                    .width(bubbleW)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PlayerSettingsOverlay(
    tracks: MediaTracksInfo,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedSubtitle = tracks.subtitles.firstOrNull { it.isSelected }?.label ?: "Desativadas"
    val selectedAudio = tracks.audios.firstOrNull { it.isSelected }?.label ?: "Padrão"
    val firstFocus = remember { FocusRequester() }

    BackHandler(enabled = true) { onDismiss() }

    BottomSheetScrim(onDismiss = onDismiss, focusTarget = firstFocus) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B1E22))
                .clickable(onClick = {})
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(56.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x88FFFFFF))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF343434))
                    .padding(vertical = 12.dp)
                    .focusGroup()
            ) {
                PlayerSettingsRow(
                    icon = Icons.Filled.ClosedCaption,
                    label = "Legendas",
                    value = selectedSubtitle,
                    enabled = tracks.subtitles.isNotEmpty(),
                    // Foco inicial na primeira linha habilitada (desabilitada não recebe foco).
                    modifier = if (tracks.subtitles.isNotEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = onOpenSubtitle
                )
                PlayerSettingsRow(
                    icon = Icons.Filled.Audiotrack,
                    label = "Áudio",
                    value = selectedAudio,
                    enabled = tracks.audios.size > 1,
                    modifier = if (tracks.subtitles.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = onOpenAudio
                )
            }
        }
    }
}

// Scrim + folha que desliza de baixo para cima. O conteúdo é ancorado embaixo.
// focusTarget: alvo focado após a folha entrar (dpad/TV). Precisa ser pedido só depois
// que o conteúdo do AnimatedVisibility é composto/anexado, com retry em alguns frames.
@Composable
private fun BottomSheetScrim(
    onDismiss: () -> Unit,
    focusTarget: FocusRequester? = null,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    if (focusTarget != null) {
        LaunchedEffect(shown) {
            if (shown) {
                repeat(12) {
                    if (runCatching { focusTarget.requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(16)
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(modifier = Modifier.padding(bottom = 28.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PlayerSettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (enabled) Color.White else Color(0xFF9A9A9A)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (focused) Color(0x1FFFFFFF) else Color.Transparent)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = if (enabled) Color(0xFFBDBDBD) else Color(0xFF8C8C8C),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (enabled) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
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
    BackHandler(enabled = true) { onDismiss() }

    BottomSheetScrim(onDismiss = onDismiss, focusTarget = firstFocus) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .clickable(onClick = {})
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
                        label = "Desativadas",
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
            CloseTrackPickerButton(onClick = onDismiss)
        }
    }
}

@Composable
private fun CloseTrackPickerButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0x22FFFFFF))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Fechar",
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.titleSmall
        )
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
