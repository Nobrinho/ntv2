@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.feature.playback.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
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
import com.ntv2.app.core.ui.icons.NbrIcons

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
    val icon = when (adjustment.target) {
        PlayerGestureTarget.Volume -> NbrIcons.Volume2
        PlayerGestureTarget.Brightness -> NbrIcons.Sun
    }
    val fill = adjustment.percent / 100f

    Box(
        modifier = modifier.padding(horizontal = 28.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xB3000000))
                .border(1.dp, com.ntv2.app.core.ui.BrandColors.Border, RoundedCornerShape(6.dp))
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Só ícone + barra (sem texto): o ícone já diz se é volume ou brilho.
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .height(96.dp)
                    .width(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(com.ntv2.app.core.ui.BrandColors.Border),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(fill.coerceIn(0f, 1f))
                        .fillMaxWidth()
                        .background(Color.White)
                )
            }
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
    onTapSeek: (Long) -> Unit = onSeek,
    onSeekTo: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onPip: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    onOpenNetwork: (() -> Unit)? = null,
    connectionReady: Boolean = true
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
            onTapSeek = onTapSeek,
            onSeekTo = onSeekTo,
            onRestart = onRestart,
            onToggle = onToggle,
            onOpenAudio = onOpenAudio,
            onOpenSubtitle = onOpenSubtitle,
            onToggleSubtitle = onToggleSubtitle,
            onOpenSettings = onOpenSettings,
            onFullscreen = onFullscreen,
            onPip = onPip,
            onCast = onCast,
            castActive = castActive,
            onOpenNetwork = onOpenNetwork,
            connectionReady = connectionReady
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
        onTapSeek = onTapSeek,
        onSeekTo = onSeekTo,
        onToggle = onToggle,
        onToggleSubtitle = onToggleSubtitle,
        onOpenSettings = onOpenSettings,
        onFullscreen = onFullscreen,
        onOpenAudio = onOpenAudio,
        onOpenSubtitle = onOpenSubtitle,
        onPip = onPip,
        onCast = onCast,
        castActive = castActive,
        onOpenNetwork = onOpenNetwork,
        connectionReady = connectionReady
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
    onTapSeek: (Long) -> Unit = onSeek,
    onSeekTo: (Long) -> Unit,
    onToggle: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onOpenAudio: () -> Unit = {},
    onOpenSubtitle: () -> Unit = {},
    onPip: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    onOpenNetwork: (() -> Unit)? = null,
    connectionReady: Boolean = true
) {
    // Protótipo: celular 21 (Py3N1, paisagem 844×390) e TV 11 (IwV7b).
    // Amarração de foco para o dpad da TV: barra superior <-> linha do tempo <-> botões de baixo,
    // e a linha da barra superior (Voltar -> Rede -> Legenda -> Opções).
    val topBarFocus = settingsFocus
    val closeFocus = remember { FocusRequester() }
    val netFocus = remember { FocusRequester() }
    val ccFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val ccEnabled = tracks.subtitles.isNotEmpty()
    val audioEnabled = tracks.audios.size > 1
    val settingsEnabled = audioEnabled || ccEnabled
    val meta = playerMetaLine(tracks)
    // Link horizontal explícito só na TV (dpad); no celular deixa a navegação espacial/toque.
    fun Modifier.hLink(left: FocusRequester? = null, right: FocusRequester? = null): Modifier =
        if (!isTv) this else this.focusProperties {
            down = scrubberFocus
            left?.let { this.left = it }
            right?.let { this.right = it }
        }
    // Margem lateral: TV respeita a área segura de overscan (5% da largura = 48 dp no Fire TV); celular 28 dp
    // como no protótipo, somando o recorte da câmera quando ele fica na lateral.
    val tvSafeSide = com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo().tvSafeHorizontal
    val sidePadding = if (isTv) tvSafeSide else 28.dp
    // Os ícones têm área de toque maior que o desenho; a sobra sai da margem para o desenho cair no lugar do protótipo.
    val iconTouch = if (isTv) 40.dp else 38.dp
    val iconSlack = (iconTouch - 22.dp) / 2
    val textColor = Color(0xFFF5F5F7)
    val timeStyle = com.ntv2.app.core.ui.NbrType.Caption.copy(fontWeight = FontWeight.SemiBold)
    val totalStyle = com.ntv2.app.core.ui.NbrType.Caption
    Box(
        modifier = modifier
            .then(
                if (isTv) Modifier.clickable(onClick = onDismiss)
                else Modifier.tapToSeek(onTap = onDismiss, onSeek = { onTapSeek(it); onInteract() })
            )
    ) {
        // Barra superior: altura 72 (TV 90), degradê preto → transparente.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color(if (isTv) 0xB3000000 else 0xAA000000),
                        1f to Color.Transparent
                    )
                )
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .height(if (isTv) 90.dp else 72.dp)
                .padding(start = sidePadding - if (isTv) iconSlack else 7.dp, end = sidePadding - iconSlack)
                .focusProperties { down = scrubberFocus },
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(
                icon = NbrIcons.ChevronLeft,
                contentDescription = "Voltar",
                onClick = onBack,
                iconSize = if (isTv) 22.dp else 26.dp,
                touchSize = 40.dp,
                modifier = Modifier
                    .focusRequester(closeFocus)
                    .hLink(right = if (onOpenNetwork != null) netFocus else if (ccEnabled) ccFocus else if (settingsEnabled) topBarFocus else null)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (isTv) 3.dp else 9.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 3.dp else 2.dp)
            ) {
                ScrollingTitle(
                    title = title,
                    year = year,
                    fontSize = if (isTv) 20.sp else 17.sp,
                    animate = animationsEnabled
                )
                if (meta != null) {
                    Text(
                        meta,
                        color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                        style = com.ntv2.app.core.ui.NbrType.Caption.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PiP e transmissão não se aplicam à TV.
                if (!isTv) {
                    if (onPip != null) PlayerIconButton(NbrIcons.PictureInPicture2, "Picture-in-picture", onPip, touchSize = iconTouch)
                    if (onCast != null) PlayerIconButton(NbrIcons.Cast, "Transmitir", { onCast(); onInteract() }, touchSize = iconTouch, active = !castActive)
                }
                // "Info" do protótipo = painel de estado da rede e formato do vídeo.
                if (onOpenNetwork != null) {
                    PlayerIconButton(
                        NbrIcons.Info,
                        "Estado da rede",
                        { onOpenNetwork(); onInteract() },
                        touchSize = iconTouch,
                        active = connectionReady,
                        modifier = Modifier
                            .focusRequester(netFocus)
                            .hLink(left = closeFocus, right = if (ccEnabled) ccFocus else if (settingsEnabled) topBarFocus else null)
                    )
                }
                // Fora do protótipo, mantidos: ligar/desligar legenda e Opções.
                PlayerIconButton(
                    NbrIcons.Captions,
                    "Ligar ou desligar legenda",
                    { onToggleSubtitle(); onInteract() },
                    touchSize = iconTouch,
                    enabled = ccEnabled,
                    active = tracks.subtitles.any { it.isSelected },
                    modifier = Modifier
                        .focusRequester(ccFocus)
                        .hLink(left = if (onOpenNetwork != null) netFocus else closeFocus, right = if (settingsEnabled) topBarFocus else null)
                )
                // Velocidade (1x) removida temporariamente: função ainda não implementada.
                PlayerIconButton(
                    NbrIcons.Settings,
                    "Opções",
                    { onOpenSettings(); onInteract() },
                    touchSize = iconTouch,
                    enabled = settingsEnabled,
                    modifier = Modifier
                        .focusRequester(topBarFocus)
                        .hLink(left = if (ccEnabled) ccFocus else if (onOpenNetwork != null) netFocus else closeFocus)
                )
            }
        }

        // Centro (celular): voltar 10 s · play/pausa 84 · avançar 10 s, espaço 44 entre os desenhos.
        if (!isTv) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    NbrIcons.RotateCcw,
                    "Voltar 10 segundos",
                    { onSeek(-DPAD_SEEK_MS); onInteract() },
                    iconSize = 36.dp,
                    touchSize = 56.dp
                )
                OverlayRoundButton(
                    icon = if (isPlaying) NbrIcons.PauseFilled else NbrIcons.PlayFilled,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    size = 84.dp,
                    iconSize = 38.dp,
                    onClick = { onToggle(); onInteract() }
                )
                PlayerIconButton(
                    NbrIcons.RotateCw,
                    "Avançar 10 segundos",
                    { onSeek(DPAD_SEEK_MS); onInteract() },
                    iconSize = 36.dp,
                    touchSize = 56.dp
                )
            }
        }

        // Barra inferior: altura 152 (TV 160), conteúdo no fundo, degradê transparente → preto.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color(if (isTv) 0xD9000000 else 0xCC000000)
                    )
                )
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(
                    start = sidePadding,
                    end = sidePadding,
                    top = if (isTv) 52.dp else 78.dp,
                    bottom = if (isTv) 32.dp else 18.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(positionMs), color = textColor, style = timeStyle, maxLines = 1)
                Scrubber(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(scrubberFocus)
                        .focusProperties {
                            up = if (settingsEnabled) topBarFocus else closeFocus
                            if (isTv) down = playFocus
                        },
                    positionMs = positionMs,
                    bufferedMs = bufferedMs,
                    durationMs = durationMs,
                    trackHeight = if (isTv) 5.dp else 4.dp,
                    showTimeBubble = isTv,
                    onToggle = { onToggle(); onInteract() },
                    onSeek = { delta -> onSeek(delta); onInteract() },
                    onSeekTo = { position -> onSeekTo(position); onInteract() }
                )
                // Mantido o tempo restante (o protótipo mostra a duração total).
                val remaining = (durationMs - positionMs).coerceAtLeast(0L)
                Text("-${formatTime(remaining)}", color = com.ntv2.app.core.ui.BrandColors.TextSecondary, style = totalStyle, maxLines = 1)
            }
            if (isTv) {
                val upToScrubber = Modifier.focusProperties { up = scrubberFocus }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerPillButton(
                        NbrIcons.RotateCcw, "-10s", "Voltar 10 segundos",
                        { onSeek(-DPAD_SEEK_MS); onInteract() },
                        modifier = upToScrubber
                    )
                    PlayerPillButton(
                        if (isPlaying) NbrIcons.PauseFilled else NbrIcons.PlayFilled,
                        label = null,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        onClick = { onToggle(); onInteract() },
                        primary = true,
                        modifier = Modifier.focusRequester(playFocus).then(upToScrubber)
                    )
                    PlayerPillButton(
                        NbrIcons.RotateCw, "+10s", "Avançar 10 segundos",
                        { onSeek(DPAD_SEEK_MS); onInteract() },
                        modifier = upToScrubber
                    )
                    PlayerPillButton(
                        NbrIcons.AudioLines, "Áudio", "Áudio",
                        { onOpenAudio(); onInteract() },
                        enabled = audioEnabled,
                        modifier = upToScrubber
                    )
                    PlayerPillButton(
                        NbrIcons.Captions, "Legendas", "Legendas",
                        { onOpenSubtitle(); onInteract() },
                        enabled = ccEnabled,
                        modifier = upToScrubber
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.offset(x = (-6).dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerLabeledAction(NbrIcons.AudioLines, "Áudio", { onOpenAudio(); onInteract() }, enabled = audioEnabled)
                        PlayerLabeledAction(NbrIcons.Captions, "Legendas", { onOpenSubtitle(); onInteract() }, enabled = ccEnabled)
                    }
                    if (showFullscreen) {
                        PlayerIconButton(
                            NbrIcons.Maximize,
                            "Tela cheia",
                            { onFullscreen(); onInteract() },
                            touchSize = 38.dp,
                            modifier = Modifier.offset(x = 8.dp)
                        )
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
    onTapSeek: (Long) -> Unit = onSeek,
    onSeekTo: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onPip: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    castActive: Boolean = false,
    onOpenNetwork: (() -> Unit)? = null,
    connectionReady: Boolean = true
) {
    Box(
        modifier = modifier
            .background(Color(0x33000000))
            .tapToSeek(onTap = onDismiss, onSeek = { onTapSeek(it); onInteract() })
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.36f)
                .background(Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color(0x66000000)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.34f)
                .background(Brush.verticalGradient(0f to Color(0x66000000), 1f to Color(0xCC000000)))
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
                PortraitTopIcon(NbrIcons.ChevronLeft, "Voltar", onBack)
                if (onPip != null) PortraitTopIcon(NbrIcons.PictureInPicture2, "Picture-in-picture", onPip)
                if (onCast != null) PortraitTopIcon(NbrIcons.Cast, "Transmitir", { onCast(); onInteract() }, active = !castActive)
                if (onOpenNetwork != null) PortraitTopIcon(
                    NbrIcons.Globe,
                    "Estado da rede",
                    { onOpenNetwork(); onInteract() },
                    active = connectionReady
                )
                    PortraitTopIcon(
                        NbrIcons.Captions,
                        "Legenda",
                        { onToggleSubtitle(); onInteract() },
                        enabled = tracks.subtitles.isNotEmpty(),
                        active = tracks.subtitles.any { it.isSelected }
                    )
                // Velocidade (1x) removida temporariamente: função ainda não implementada.
                PortraitTopIcon(
                    NbrIcons.Settings,
                    "Opções",
                    { onOpenSettings(); onInteract() },
                    enabled = tracks.audios.size > 1 || tracks.subtitles.isNotEmpty()
                )
            }

            Text(
                text = title,
                color = Color(0xFFF5F5F7),
                // Mesmo peso do título do player no protótipo (17/700), um pouco maior em pé.
                style = com.ntv2.app.core.ui.NbrType.SheetTitle,
                maxLines = 2,
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
                Text(formatTime(positionMs), color = Color(0xFFF5F5F7), style = com.ntv2.app.core.ui.NbrType.Caption.copy(fontWeight = FontWeight.SemiBold))
                val remaining = (durationMs - positionMs).coerceAtLeast(0L)
                Text("-${formatTime(remaining)}", color = com.ntv2.app.core.ui.BrandColors.TextSecondary, style = com.ntv2.app.core.ui.NbrType.Caption)
            }

            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverlayRoundButton(
                    icon = if (isPlaying) NbrIcons.PauseFilled else NbrIcons.PlayFilled,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    size = CENTER_PLAY_SIZE,
                    iconSize = CENTER_PLAY_ICON,
                    strong = false,
                    onClick = { onToggle(); onInteract() }
                )
            }

            if (tracks.audios.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverlayChip(
                        icon = NbrIcons.AudioLines,
                        label = "Áudio",
                        showLabel = false
                    ) { onOpenAudio(); onInteract() }
                }
            }
        }
    }
}

private val CENTER_PLAY_SIZE = 64.dp
private val CENTER_PLAY_ICON = 34.dp

/**
 * Toque simples → [onTap]; toque duplo na metade esquerda/direita → volta/avança [DPAD_SEEK_MS]
 * (substitui os botões -10/+10). Toques duplos seguidos acumulam no feedback de seek.
 */
internal fun Modifier.tapToSeek(onTap: () -> Unit, onSeek: (Long) -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    val seek by rememberUpdatedState(onSeek)
    pointerInput(Unit) {
        detectTapGestures(
            onTap = { tap() },
            onDoubleTap = { offset -> seek(if (offset.x < size.width / 2f) -DPAD_SEEK_MS else DPAD_SEEK_MS) }
        )
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
        focused -> com.ntv2.app.core.ui.BrandColors.OnCta
        !enabled -> com.ntv2.app.core.ui.BrandColors.ContentDisabled
        active -> Color(0xFFF5F5F7)
        else -> com.ntv2.app.core.ui.BrandColors.ContentDisabled
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (focused) com.ntv2.app.core.ui.BrandColors.Accent else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun FullscreenControlButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            NbrIcons.Maximize,
            contentDescription = "Tela cheia",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
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
            // Protótipo (tela 21): círculo branco@15% com contorno border; em foco, accent.
            .background(
                when {
                    strong -> Color(0xE6FFFFFF)
                    focused -> com.ntv2.app.core.ui.BrandColors.Accent
                    else -> PlayerGlass
                }
            )
            .border(
                width = 1.dp,
                color = if (focused) com.ntv2.app.core.ui.BrandColors.Accent else com.ntv2.app.core.ui.BrandColors.Border,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (strong || focused) com.ntv2.app.core.ui.BrandColors.OnCta else Color(0xFFF5F5F7),
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
        enabled -> com.ntv2.app.core.ui.BrandColors.Border
        else -> Color(0x22000000)
    }
    val baseModifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .onFocusChanged { focused = it.isFocused }
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .background(background)
        .border(1.dp, com.ntv2.app.core.ui.BrandColors.BorderSubtle, RoundedCornerShape(4.dp))

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
    showTimeBubble: Boolean = false,
    /** Espessura do trilho (protótipo: 4 no celular, 5 na TV). */
    trackHeight: androidx.compose.ui.unit.Dp = 4.dp
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
            val trackH = if (focused) (trackHeight * 2).toPx() else trackHeight.toPx()
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            // Trilho: branco@20%; buffer branco@33%; assistido accent (protótipo 21 / TV 11).
            drawRoundRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w, trackH),
                cornerRadius = radius
            )
            // Buffer
            if (buffered > 0f) {
                drawRoundRect(
                    color = Color(0x54FFFFFF),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(w * buffered, trackH),
                    cornerRadius = radius
                )
            }
            // Reproduzido
            // Trecho assistido e marcador em azul da marca (antes branco), como as barras de progresso dos cards.
            drawRoundRect(
                color = com.ntv2.app.core.ui.BrandColors.Accent,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w * played, trackH),
                cornerRadius = radius
            )
            // Knob
            // O protótipo não desenha marcador; fica um pequeno (6 dp) para a área de arrastar continuar visível.
            val knobR = if (focused) 10.dp.toPx() else 6.dp.toPx()
            if (focused) {
                drawCircle(color = com.ntv2.app.core.ui.BrandColors.Accent.copy(alpha = 0.33f), radius = knobR + 6.dp.toPx(), center = Offset(w * played, cy))
            }
            drawCircle(color = com.ntv2.app.core.ui.BrandColors.Accent, radius = knobR, center = Offset(w * played, cy))
        }
        // Tempo sobre o knob enquanto a linha do tempo está focada (TV).
        if (showTimeBubble && focused) {
            val bubbleW = 96.dp
            val x = (maxWidth * played - bubbleW / 2).coerceIn(0.dp, (maxWidth - bubbleW).coerceAtLeast(0.dp))
            Text(
                text = formatTime(positionMs),
                color = com.ntv2.app.core.ui.BrandColors.OnCta,
                style = com.ntv2.app.core.ui.NbrType.Chip,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .offset(x = x, y = (-40).dp)
                    .width(bubbleW)
                    .background(com.ntv2.app.core.ui.BrandColors.Accent, RoundedCornerShape(50))
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

    PlayerSidePanel(onDismiss = onDismiss, focusTarget = firstFocus) {
        PanelGroupTitle("Opções")
        Column(
            modifier = Modifier.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayerSettingsRow(
                icon = NbrIcons.Captions,
                label = "Legendas",
                value = selectedSubtitle,
                enabled = tracks.subtitles.isNotEmpty(),
                // Foco inicial na primeira linha habilitada (desabilitada não recebe foco).
                modifier = if (tracks.subtitles.isNotEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = onOpenSubtitle
            )
            PlayerSettingsRow(
                icon = NbrIcons.AudioLines,
                label = "Áudio",
                value = selectedAudio,
                enabled = tracks.audios.size > 1,
                modifier = if (tracks.subtitles.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = onOpenAudio
            )
        }
    }
}

/**
 * Painel do player no estilo do protótipo (tela 22): em paisagem, coluna à direita com 340 de largura, altura
 * cheia, fundo surface@95%, contorno border-subtle, padding 24 e espaço 10. Em pé não cabe ao lado, então vira
 * a sheet padrão (2.13). focusTarget: alvo focado depois que o painel entra (dpad/TV), pedido com retry porque
 * o conteúdo do AnimatedVisibility só existe depois de composto.
 */
@Composable
internal fun PlayerSidePanel(
    onDismiss: () -> Unit,
    focusTarget: FocusRequester? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = maxWidth > maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (side) Color(0x59000000) else Color(0xB3000000))
                .clickable(onClick = onDismiss),
            contentAlignment = if (side) Alignment.CenterEnd else Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = if (side) slideInHorizontally(tween(220)) { it } + fadeIn(tween(220))
                else slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = if (side) slideOutHorizontally { it } + fadeOut() else slideOutVertically { it } + fadeOut()
            ) {
                if (side) {
                    Column(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .background(PlayerPanelBg)
                            .border(1.dp, com.ntv2.app.core.ui.BrandColors.BorderSubtle)
                            .clickable(onClick = {})
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.End)
                            )
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content
                    )
                } else {
                    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(com.ntv2.app.core.ui.BrandColors.Surface)
                            .border(1.dp, com.ntv2.app.core.ui.BrandColors.BorderSubtle, shape)
                            .clickable(onClick = {})
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 4.dp)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(com.ntv2.app.core.ui.BrandColors.Border)
                        )
                        content()
                    }
                }
            }
        }
    }
}

/** Título de grupo do painel ("Áudio", "Legendas"): 16/700 text. */
@Composable
internal fun PanelGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color(0xFFF5F5F7),
        style = com.ntv2.app.core.ui.NbrType.Section.copy(fontSize = 16.sp),
        modifier = modifier.padding(top = 2.dp, bottom = 2.dp)
    )
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
    val contentColor = if (enabled) Color(0xFFF5F5F7) else com.ntv2.app.core.ui.BrandColors.TextMuted
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (focused) com.ntv2.app.core.ui.BrandColors.Fill else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) com.ntv2.app.core.ui.BrandColors.Accent else Color.Transparent, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = contentColor,
            style = com.ntv2.app.core.ui.NbrType.Chip,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = if (enabled) com.ntv2.app.core.ui.BrandColors.TextSecondary else com.ntv2.app.core.ui.BrandColors.TextMuted,
            style = com.ntv2.app.core.ui.NbrType.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp)
        )
        if (enabled) {
            Spacer(Modifier.width(6.dp))
            Icon(NbrIcons.ChevronRight, contentDescription = null, tint = com.ntv2.app.core.ui.BrandColors.TextSecondary, modifier = Modifier.size(16.dp))
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

    PlayerSidePanel(onDismiss = onDismiss, focusTarget = firstFocus) {
        PanelGroupTitle(title)
        Column(
            modifier = Modifier.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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

/** "Fechar" (fora do protótipo, mantido): botão secundário em pílula. */
@Composable
internal fun CloseTrackPickerButton(onClick: () -> Unit) {
    PlayerSecondaryButton(
        label = "Fechar",
        onClick = onClick,
        height = 44.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    )
}

/**
 * Opção do painel (tela 22): altura 40, padding 0/12, raio 10. Selecionada: fundo accent@12%, texto 13/700
 * accent e check 16. Demais: transparente, 13/500 text. Em foco (D-pad): contorno accent.
 */
@Composable
internal fun TrackRow(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val accent = com.ntv2.app.core.ui.BrandColors.Accent
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    selected -> accent.copy(alpha = 0.12f)
                    focused -> com.ntv2.app.core.ui.BrandColors.Fill
                    else -> Color.Transparent
                }
            )
            .border(if (focused) 2.dp else 0.dp, if (focused) accent else Color.Transparent, shape)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color(0xFFF5F5F7),
            style = com.ntv2.app.core.ui.NbrType.Chip.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(NbrIcons.Check, contentDescription = "Selecionado", tint = accent, modifier = Modifier.size(16.dp))
        }
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
            // Protótipo: "Resident Evil (2026)" inteiro no mesmo peso e cor.
            append(title)
            if (year != null) append(" ($year)")
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
        color = Color(0xFFF5F5F7),
        style = com.ntv2.app.core.ui.NbrType.Section,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
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
