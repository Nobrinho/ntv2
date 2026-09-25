@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.feature.playback.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.animation.core.tween
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.core.player.PlaybackState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.abs

internal const val DPAD_SEEK_MS = 10_000L
internal const val CONTROLS_TIMEOUT_MS = 6_000L
internal const val GESTURE_ADJUSTMENT_TIMEOUT_MS = 900L
// Seek do dpad é acumulado e só aplicado ao player após essa pausa sem teclas.
internal const val SEEK_COMMIT_DELAY_MS = 500L
internal const val BACK_EXIT_WINDOW_MS = 2_000L

/** Passo do seek do dpad com aceleração conforme a tecla é segurada (repetições). */
internal fun dpadSeekStep(event: KeyEvent): Long {
    val repeat = event.nativeKeyEvent.repeatCount
    return when {
        repeat < 10 -> DPAD_SEEK_MS
        repeat < 30 -> 30_000L
        else -> 60_000L
    }
}

internal enum class TrackPicker { Audio, Subtitle, Settings }
internal enum class PlayerGestureTarget { Volume, Brightness }

internal data class GestureAdjustment(
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
    onBack: () -> Unit,
    // Configurações: "Animações" (luz pulsante + fade) e a variante da iluminação da capa.
    animationsEnabled: Boolean = true,
    nativeBlurGlow: Boolean = true
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

    // O AFTKM apresentou bloqueios longos do compositor ao reproduzir VP9/23,976 fps com a
    // saída fixa em 59,94 Hz. Para esse conjunto específico, usa um modo da mesma resolução e
    // com a cadência do conteúdo. Celulares e outros modelos não entram neste workaround.
    val videoFrameRate = state.snapshot.tracks.videoFrameRate
    val videoMimeType = state.snapshot.tracks.videoMimeType
    DisposableEffect(activity, videoFrameRate, videoMimeType) {
        val window = activity?.window
        val originalModeId = window?.attributes?.preferredDisplayModeId ?: 0
        val affectedFireTvVp9 = Build.MANUFACTURER.equals("Amazon", ignoreCase = true) &&
            Build.MODEL.equals("AFTKM", ignoreCase = true) &&
            videoMimeType == MimeTypes.VIDEO_VP9 && videoFrameRate > 0f
        if (window != null && affectedFireTvVp9) {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .asSequence()
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { abs(it.refreshRate - videoFrameRate) }
                ?.takeIf { abs(it.refreshRate - videoFrameRate) <= 0.15f }
            if (best != null && best.modeId != current.modeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
                android.util.Log.i(
                    "NtvPlayer",
                    "VP9/AFTKM: saída ${current.refreshRate}Hz → ${best.refreshRate}Hz para vídeo ${videoFrameRate}fps"
                )
            }
        }
        onDispose {
            if (window != null && window.attributes.preferredDisplayModeId != originalModeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = originalModeId }
            }
        }
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
    // Fechou a tela de erro (Tentar novamente): o foco volta ao vídeo para o D-pad seguir funcionando.
    val hasLoadError = state.loadError != null
    LaunchedEffect(hasLoadError) {
        if (!hasLoadError) runCatching { videoFocusRequester.requestFocus() }
    }

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
    // Com a tela de erro aberta, Voltar sai do player direto.
    BackHandler(enabled = state.loadError != null) { onBack() }

    // Vídeo terminou: fecha o player e volta aos Detalhes.
    val ended = !state.isPlaceholderMode && state.snapshot.state == PlaybackState.Ended
    LaunchedEffect(ended) { if (ended) onBack() }

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
                    downloadProgress = state.downloadProgress,
                    loadingHint = state.loadingHint,
                    player = { viewModel.player },
                    // Com os controles visíveis a linha do tempo já mostra o alvo; evita sobrepor o play.
                    seekFeedbackMs = if (controlsVisible) 0L else seekFeedbackMs,
                    animationsEnabled = animationsEnabled,
                    nativeBlurGlow = nativeBlurGlow,
                    verticalAdjustmentEnabled = videoIsFullscreen && !controlsVisible,
                    isTv = adaptive.isTv,
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

                // Aparecer/sumir dos controles com fade (ligado ao toggle "Animações" das configurações).
                val controlsOverlay: @Composable () -> Unit = {
                    StreamingControlsOverlay(
                        modifier = Modifier.fillMaxSize(),
                        title = state.title,
                        year = titleYear(state.title, state.details?.year, state.fileName),
                        animationsEnabled = animationsEnabled,
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
                if (animationsEnabled) {
                    // FQN: dentro do Column externo o Kotlin escolheria ColumnScope.AnimatedVisibility.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(tween(CONTROLS_FADE_IN_MS)),
                        exit = fadeOut(tween(CONTROLS_FADE_OUT_MS))
                    ) { controlsOverlay() }
                } else if (controlsVisible) {
                    controlsOverlay()
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

        state.loadError?.let { error ->
            LoadErrorOverlay(
                error = error,
                onRetry = { viewModel.onAction(PlayerScreenAction.RetryLoad) },
                onBack = onBack
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
internal fun VideoSurface(
    modifier: Modifier,
    focusRequester: FocusRequester,
    isPlaceholderMode: Boolean,
    thumbnailPath: String?,
    title: String,
    playbackState: PlaybackState,
    statusMessage: String,
    downloadProgress: DownloadProgress?,
    loadingHint: String?,
    player: () -> Player?,
    seekFeedbackMs: Long,
    animationsEnabled: Boolean,
    nativeBlurGlow: Boolean,
    verticalAdjustmentEnabled: Boolean,
    isTv: Boolean,
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
            // A capa iluminada da fase de download continua até o PRIMEIRO FRAME ser desenhado
            // (preparar + buffer inicial deixariam a tela preta); aí some com fade sobre o vídeo.
            val currentPlayer = player()
            var firstFrameRendered by remember { mutableStateOf(false) }
            DisposableEffect(currentPlayer) {
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrameRendered = true }
                }
                currentPlayer?.addListener(listener)
                onDispose { currentPlayer?.removeListener(listener) }
            }
            val showPoster = !firstFrameRendered && !thumbnailPath.isNullOrBlank()
            if (animationsEnabled) {
                AnimatedVisibility(visible = showPoster, enter = fadeIn(), exit = fadeOut(tween(700))) {
                    AmbientPoster(thumbnailPath.orEmpty(), title, nativeBlurGlow, animationsEnabled)
                }
            } else if (showPoster) {
                AmbientPoster(thumbnailPath.orEmpty(), title, nativeBlurGlow, animationsEnabled)
            }
            // Spinner + rótulo do estado enquanto prepara/armazena em buffer.
            loadingLabel(playbackState)?.let { label ->
                LoadingStatus(label, downloadProgress, loadingHint, isTv)
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
                AmbientPoster(thumbnailPath, title, nativeBlurGlow, animationsEnabled)
            }
            LoadingStatus(statusMessage, downloadProgress, loadingHint, isTv)
        }
    }
}

internal fun formatTime(ms: Long): String {
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
