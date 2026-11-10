package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.icons.NbrIcons

// Peças do detalhe do filme no visual do protótipo (telas 14, 15 e 20 do celular e 09 da TV).
// Medidas do celular em dp = px do protótipo; as da TV são os px de 1920x1080 divididos por 2.

/** Texto principal do protótipo (#F5F5F7). */
internal val MovieDetailTextPrimary = com.ntv2.app.core.ui.BrandColors.TextPrimary
private val RoundFill = Color(0x1AFFFFFF)
private val RoundStroke = Color(0x33FFFFFF)
private val ChipFill = Color(0x14FFFFFF)
private val ChipStroke = Color(0x1FFFFFFF)

/** Botão redondo de 48 dp (voltar no topo, recomeçar/favoritar/reportar ao lado do Assistir). */
@Composable
internal fun MovieDetailRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
    tint: Color = MovieDetailTextPrimary,
    fill: Color = RoundFill
) {
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    Box(
        modifier = modifier
            .size(size)
            .glideTarget(size / 2)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Border else fill)
            .border(if (ring) 3.dp else 1.dp, if (ring) BrandColors.Accent else RoundStroke, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Chip em pílula (FHD, Dublado, 5.1...): 32 dp no celular, 26 dp na TV. Com [onClick] fica focável. */
@Composable
internal fun MovieDetailChip(text: String, tv: Boolean, onClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    val style = if (tv) NbrType.Caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        else NbrType.Secondary.copy(fontWeight = FontWeight.Medium)
    Box(
        modifier = Modifier
            .height(if (tv) 26.dp else 32.dp)
            .then(if (onClick != null) Modifier.glideTarget(16.dp) else Modifier)
            .clip(BrandShapes.Pill)
            .then(if (onClick != null) Modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick) else Modifier)
            .background(ChipFill)
            .border(if (ring) 2.dp else 1.dp, if (ring) Color.White else ChipStroke, BrandShapes.Pill)
            .padding(horizontal = if (tv) 12.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (focused) Color.White else BrandColors.TextSecondary, style = style, maxLines = 1)
    }
}

/**
 * Botão principal em pílula (Assistir / Continuar / Abrindo / Falhou). Accent no normal e vermelho de
 * erro quando a abertura falhou (tela 20). Celular 52 dp; TV 36 dp.
 */
@Composable
internal fun MovieDetailPlayButton(
    label: String,
    tv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    failed: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    val content = BrandColors.OnCta
    Row(
        modifier = modifier
            .height(if (tv) 36.dp else 52.dp)
            .glideTarget(26.dp)
            .clip(BrandShapes.Pill)
            .onFocusChanged { focused = it.isFocused }
            // Enquanto carrega, ignora novos toques (evita disparos duplicados).
            .clickable(enabled = !loading, onClick = onClick)
            .background(if (failed) BrandColors.Error else BrandColors.Cta)
            .then(if (ring) Modifier.border(3.dp, Color.White, BrandShapes.Pill) else Modifier)
            .padding(horizontal = if (tv) 26.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tv) 6.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconSize = if (tv) 15.dp else 16.dp
        when {
            loading -> CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(iconSize))
            !failed -> Icon(NbrIcons.PlayFilled, contentDescription = null, tint = content, modifier = Modifier.size(iconSize))
        }
        Text(
            label,
            color = content,
            style = NbrType.Button.copy(fontSize = if (tv) 14.sp else 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Botão secundário em pílula com ícone e rótulo (TV: Recomeçar, Adicionar à minha lista, Reportar). */
@Composable
internal fun MovieDetailPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MovieDetailTextPrimary
) {
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    Row(
        modifier = modifier
            .height(36.dp)
            .glideTarget(18.dp)
            .clip(BrandShapes.Pill)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.Border else ChipFill)
            .border(if (ring) 3.dp else 1.dp, if (ring) BrandColors.Accent else RoundStroke, BrandShapes.Pill)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(label, color = MovieDetailTextPrimary, style = NbrType.Button.copy(fontSize = 14.sp), maxLines = 1)
    }
}

/**
 * Botão secundário da TV só com ícone (36 dp, redondo); com foco abre em pílula com o rótulo. Mantém a
 * fileira estreita para não passar por trás da coluna do elenco.
 */
@Composable
internal fun MovieDetailExpandingButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MovieDetailTextPrimary,
    animate: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    Row(
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 36.dp)
            .glideTarget(18.dp)
            .clip(BrandShapes.Pill)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick, onClickLabel = label)
            .background(if (focused) BrandColors.Border else ChipFill)
            .border(if (ring) 3.dp else 1.dp, if (ring) BrandColors.Accent else RoundStroke, BrandShapes.Pill)
            .then(if (animate) Modifier.animateContentSize() else Modifier)
            .padding(horizontal = if (focused) 16.dp else 10.5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        if (focused) {
            Text(label, color = MovieDetailTextPrimary, style = NbrType.Button.copy(fontSize = 14.sp), maxLines = 1)
        }
    }
}

/** Barra de progresso de 4 dp sob o "Continuar" (tela 15). */
@Composable
internal fun MovieDetailProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(ChipFill)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(BrandColors.Accent)
        )
    }
}

/** Aviso de falha ao abrir o vídeo (tela 20): fundo vermelho a 10%, borda vermelha, raio 12. */
@Composable
internal fun MovieDetailFailureNotice(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(BrandShapes.Md)
            .background(BrandColors.Error.copy(alpha = 0.10f))
            .border(1.dp, BrandColors.Error, BrandShapes.Md)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NbrIcons.CircleAlert, contentDescription = null, tint = BrandColors.Error, modifier = Modifier.size(18.dp))
        Text(text, color = BrandColors.TextSecondary, style = NbrType.Caption)
    }
}

/** "‹ Voltar" no topo da coluna de informações da TV (tela 09). */
@Composable
internal fun MovieDetailBackLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    Row(
        modifier = modifier
            .glideTarget(8.dp)
            .clip(BrandShapes.Sm)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .then(if (ring) Modifier.border(3.dp, BrandColors.Accent, BrandShapes.Sm) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (focused) Color.White else BrandColors.TextSecondary
        Icon(NbrIcons.ChevronLeft, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text("Voltar", color = color, style = NbrType.Body.copy(fontSize = 12.sp))
    }
}
