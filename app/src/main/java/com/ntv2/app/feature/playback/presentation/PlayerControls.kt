@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.feature.playback.presentation

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Controles do player: sobreposições (paisagem/retrato), barra de progresso, título, configurações e faixas.

@Composable
internal fun GestureAdjustmentOverlay(
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
internal fun StreamingControlsOverlay(
    modifier: Modifier,
    title: String,
    year: Int?,
    animationsEnabled: Boolean,
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
        year = year,
        animationsEnabled = animationsEnabled,
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
internal fun LandscapeControlsOverlay(
    modifier: Modifier,
    title: String,
    year: Int?,
    animationsEnabled: Boolean,
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
    // Margem lateral: TV respeita a área segura de overscan (48dp); celular usa quase toda a
    // largura, somando o recorte da câmera quando ele fica na lateral.
    val sidePadding = if (isTv) 48.dp else 24.dp
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
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = sidePadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Uma linha só: ícones da esquerda · título (ano) · ícones da direita.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { down = centerFocus },
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
                ScrollingTitle(
                    title = title,
                    year = year,
                    fontSize = if (isTv) 22.sp else 20.sp,
                    animate = animationsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                )
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
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(start = sidePadding, end = sidePadding, bottom = 34.dp),
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
internal fun PortraitControlsOverlay(
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
internal fun PortraitTopIcon(
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
internal fun FullscreenControlButton(onClick: () -> Unit) {
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
internal fun PortraitSeekButton(
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
internal fun OverlayRoundButton(
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
internal fun OverlayChip(
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
internal fun Scrubber(
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
internal fun PlayerSettingsOverlay(
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
internal fun BottomSheetScrim(
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
internal fun PlayerSettingsRow(
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
internal fun TrackPickerOverlay(
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
internal fun CloseTrackPickerButton(onClick: () -> Unit) {
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
internal fun TrackRow(
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

internal const val CONTROLS_FADE_IN_MS = 220
internal const val CONTROLS_FADE_OUT_MS = 280
// Velocidade de leitura do título que não cabe na linha.
internal const val TITLE_SCROLL_DP_PER_SEC = 40f
internal val YEAR_IN_TEXT = Regex("(?<!\\d)(19[2-9]\\d|20[0-4]\\d)(?!\\d)")

/** Ano do filme: dos detalhes (índice/TMDB) ou, na falta, do nome do arquivo. Null se já está no título. */
internal fun titleYear(title: String, detailsYear: Int?, fileName: String?): Int? {
    val year = detailsYear ?: fileName?.let { YEAR_IN_TEXT.find(it)?.value?.toIntOrNull() }
    return year?.takeIf { !title.contains(it.toString()) }
}

/**
 * Título "Nome (ano)" numa linha só. Se não couber, o excedente some num degradê e, a cada vez que
 * os controles aparecem, o texto rola até o fim para completar a leitura, volta ao início e repete
 * enquanto estiver visível. Com animações desligadas fica parado, só com o degradê no fim.
 */
@Composable
internal fun ScrollingTitle(
    title: String,
    year: Int?,
    fontSize: TextUnit,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val text = remember(title, year) {
        buildAnnotatedString {
            append(title)
            if (year != null) {
                withStyle(SpanStyle(color = Color(0xB3FFFFFF), fontWeight = FontWeight.Normal)) {
                    append(" ($year)")
                }
            }
        }
    }
    LaunchedEffect(text, animate, scroll.maxValue) {
        if (!animate || scroll.maxValue <= 0) {
            scroll.scrollTo(0)
            return@LaunchedEffect
        }
        val pxPerSecond = with(density) { TITLE_SCROLL_DP_PER_SEC.dp.toPx() }
        while (true) {
            delay(1_500L)
            val max = scroll.maxValue
            val durationMs = (max / pxPerSecond * 1_000f).toInt().coerceAtLeast(400)
            scroll.animateScrollTo(max, tween(durationMs, easing = LinearEasing))
            delay(2_000L)
            scroll.animateScrollTo(0, tween(600, easing = FastOutSlowInEasing))
        }
    }
    val fadeWidthPx = with(density) { 28.dp.toPx() }
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            // Offscreen: o DstIn recorta a transparência só do próprio texto (degradê nas bordas).
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = fadeWidthPx.coerceAtMost(size.width / 2f)
                if (scroll.value > 0) {
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
                if (scroll.value < scroll.maxValue) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startX = size.width - fade,
                            endX = size.width
                        ),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
            .horizontalScroll(scroll, enabled = false)
    )
}
