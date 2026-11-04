package com.ntv2.app.feature.playback.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.player.MediaTracksInfo
import com.ntv2.app.core.player.VideoResolution
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType

// Peças visuais do player no estilo do protótipo (telas 21–24 do celular e TV 11).

/** Fundo dos botões translúcidos do player (protótipo: #FFFFFF26 no play, #FFFFFF1F nos botões da TV). */
internal val PlayerGlass = Color(0x26FFFFFF)
internal val PlayerGlassTv = Color(0x1FFFFFFF)
/** Painel lateral e cartões sobre o vídeo: surface a 95% (#151A22F2). */
internal val PlayerPanelBg = Color(0xF2151A22)

/**
 * Linha de apoio do título ("FHD · Português (AC3 5.1)"). O protótipo mostra "canal · qualidade · áudio";
 * o canal não chega até os controles, então a linha usa só o que o player já sabe. Null se não houver nada.
 */
internal fun playerMetaLine(tracks: MediaTracksInfo): String? {
    val quality = VideoResolution.label(width = tracks.videoWidth, height = tracks.videoHeight)
    val audio = tracks.audios.firstOrNull { it.isSelected }?.label?.takeIf { it.isNotBlank() }
    return listOfNotNull(quality, audio).joinToString(" · ").ifBlank { null }
}

/** Anel de carregamento do protótipo (tela 23): arco accent de 260°, girando. */
@Composable
internal fun PlayerSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 4.dp,
    color: Color = BrandColors.Accent
) {
    val transition = rememberInfiniteTransition(label = "player-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "player-spinner-rotation"
    )
    Canvas(modifier = modifier.size(size).graphicsLayer { rotationZ = rotation }) {
        val w = stroke.toPx()
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w / 2f, w / 2f),
            size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Round)
        )
    }
}

/**
 * Ícone solto do player (barra superior: 22 dp, sem fundo). A área de toque é maior que o desenho.
 * Com foco (D-pad): fundo accent e ícone escuro, como os botões da TV no protótipo.
 */
@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    touchSize: Dp = 40.dp,
    enabled: Boolean = true,
    active: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val tint = when {
        focused -> BrandColors.OnCta
        !enabled || !active -> BrandColors.ContentDisabled
        else -> Color(0xFFF5F5F7)
    }
    Box(
        modifier = modifier
            .size(touchSize)
            .clip(RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (focused) BrandColors.Accent else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Ícone + rótulo da barra inferior do celular (tela 21): ícone 20, texto 12/600, espaço 6. */
@Composable
internal fun PlayerLabeledAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val content = when {
        focused -> BrandColors.OnCta
        enabled -> Color(0xFFF5F5F7)
        else -> BrandColors.ContentDisabled
    }
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (focused) BrandColors.Accent else Color.Transparent)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, color = content, style = NbrType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
    }
}

/**
 * Botão em pílula da TV (TV 11): altura 40, raio 20, padding 0/14, ícone 18 + texto 12/600, fundo branco@12%.
 * [primary] = Pausar/Reproduzir: só ícone, 42×40, fundo accent. Em foco: fundo accent e conteúdo on-accent;
 * o primário ganha o anel branco para não sumir.
 */
@Composable
internal fun PlayerPillButton(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val background = when {
        primary || focused -> BrandColors.Accent
        else -> PlayerGlassTv
    }
    val content = when {
        primary || focused -> BrandColors.OnCta
        enabled -> Color(0xFFF5F5F7)
        else -> BrandColors.ContentDisabled
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .then(if (label == null) Modifier.width(42.dp) else Modifier)
            .then(if (primary && focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(background)
            .padding(horizontal = if (label == null) 0.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(18.dp))
        if (label != null) {
            Text(label, color = content, style = NbrType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** Botão secundário em pílula dos avisos do player (tela 24): altura 52, fundo branco@8%, contorno border. */
@Composable
internal fun PlayerSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val content = if (focused) BrandColors.OnCta else Color(0xFFF5F5F7)
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Accent else BrandColors.Fill)
            .border(1.dp, if (focused) BrandColors.Accent else BrandColors.Border, shape)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = content,
            style = NbrType.BodyLarge.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false
        )
    }
}
