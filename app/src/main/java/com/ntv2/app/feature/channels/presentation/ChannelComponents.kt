package com.ntv2.app.feature.channels.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget

/*
 * Peças da Seleção de canais do protótipo (09 do celular, TV 06), também usadas no seletor rápido de
 * canal da Biblioteca. Celular: cartão em linha (72 dp, raio 16). TV: cartão vertical da grade (raio 14).
 */

/** Branco do texto do protótipo (#F5F5F7). */
internal val ChannelText = com.ntv2.app.core.ui.BrandColors.TextPrimary

/** Fundo do canal selecionado: accent@8% (#66D9FF14). */
private val SelectedFill = BrandColors.Accent.copy(alpha = 0x14 / 255f)

/**
 * Avatar do canal: foto real quando houver; sem foto, círculo surface-alt com a inicial em accent
 * (celular 44 dp / 18 bold; TV 48 dp / 22 extra-bold).
 */
@Composable
internal fun ChannelAvatar(avatarPath: String?, title: String, size: Dp, initialSize: Int, initialWeight: FontWeight) {
    if (avatarPath != null) {
        AsyncImage(
            model = avatarPath,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(BrandColors.SurfaceAlt)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(BrandColors.SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channelInitial(title),
                color = BrandColors.Accent,
                style = NbrType.Body.copy(fontSize = initialSize.sp, lineHeight = (initialSize * 1.21f).sp, fontWeight = initialWeight)
            )
        }
    }
}

internal fun channelInitial(title: String): String =
    title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"

/**
 * Pílula de estado: "Selecionado" accent + texto on-accent, "Selecionar" fill + texto text-2.
 * Celular 28 dp (12/700); TV 24 dp (11/700). Padding 0/12, raio = metade da altura.
 */
@Composable
internal fun ChannelStatePill(selected: Boolean, tv: Boolean) {
    val h = if (tv) 24.dp else 28.dp
    val size = if (tv) 11 else 12
    Box(
        Modifier
            .height(h)
            .clip(RoundedCornerShape(h / 2))
            .background(if (selected) BrandColors.Accent else BrandColors.Fill)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (selected) "Selecionado" else "Selecionar",
            color = if (selected) BrandColors.OnCta else BrandColors.TextSecondary,
            style = NbrType.Caption.copy(fontSize = size.sp, lineHeight = (size * 1.21f).sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

/**
 * Canal em linha (09 do celular): padding 14, gap 12, raio 16, 72 dp. Selecionado accent@8% + contorno
 * 2 accent; demais surface + border-subtle. Avatar 44, nome 16/600. [trailing] fica à direita.
 */
@Composable
internal fun ChannelRowCard(
    title: String,
    avatarPath: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = { ChannelStatePill(selected, tv = false) }
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Mínimo de 72 (protótipo); nomes de 2 linhas crescem sem cortar.
            .heightIn(min = 72.dp)
            .glideTarget(16.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> BrandColors.SurfaceAlt
                    selected -> SelectedFill
                    else -> BrandColors.Surface
                }
            )
            .border(
                width = if ((focused && !gliding) || selected) 2.dp else 1.dp,
                color = if ((focused && !gliding) || selected) BrandColors.Accent else BrandColors.BorderSubtle,
                shape = shape
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelAvatar(avatarPath, title, 44.dp, 18, FontWeight.Bold)
        Text(
            title,
            color = ChannelText,
            style = NbrType.ItemTitle.copy(fontSize = 16.sp, lineHeight = 19.36.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/**
 * Cartão da grade da TV (TV 06): 168 dp, padding 16, gap 10, raio 14. Selecionado accent@8% + contorno
 * accent; demais surface + border-subtle. Avatar 48, nome 17/700 (até 2 linhas), pílula 24 embaixo.
 * O foco é o anel deslizante da tela; sem ele, contorno azul de 2 dp.
 */
@Composable
internal fun ChannelGridCard(
    title: String,
    avatarPath: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .glideTarget(14.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    focused && !selected -> BrandColors.SurfaceAlt
                    selected -> SelectedFill
                    else -> BrandColors.Surface
                }
            )
            .border(
                width = if (focused && !gliding) 2.dp else 1.dp,
                color = if ((focused && !gliding) || selected) BrandColors.Accent else BrandColors.BorderSubtle,
                shape = shape
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChannelAvatar(avatarPath, title, 48.dp, 22, FontWeight.ExtraBold)
        Text(
            title,
            color = ChannelText,
            style = NbrType.Section.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        ChannelStatePill(selected, tv = true)
    }
}

/** Placeholders com pulso suave no formato do cartão (linha no celular, grade na TV). */
@Composable
internal fun ChannelSkeleton(tv: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "channels-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "channels-skeleton-alpha"
    )
    val shimmer = Color.White.copy(alpha = alpha)
    val bar = @Composable { fraction: Float, h: Dp ->
        Box(Modifier.fillMaxWidth(fraction).height(h).clip(RoundedCornerShape(4.dp)).background(shimmer))
    }
    if (tv) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) {
                        Column(
                            Modifier
                                .weight(1f)
                                .height(168.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BrandColors.Surface)
                                .border(1.dp, BrandColors.BorderSubtle, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(shimmer))
                            bar(0.7f, 14.dp)
                            Spacer(Modifier.weight(1f))
                            bar(0.45f, 24.dp)
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BrandColors.Surface)
                        .border(1.dp, BrandColors.BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(shimmer))
                    Column(Modifier.weight(1f)) { bar(0.6f, 14.dp) }
                    Box(Modifier.size(width = 90.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(shimmer))
                }
            }
        }
    }
}

/** "2 de 4 canais selecionados": o total que o app já mostrava continua junto da contagem. */
internal fun selectedCountLabel(selected: Int, total: Int): String {
    val noun = if (selected == 1) "canal selecionado" else "canais selecionados"
    return if (total > 0) "$selected de $total $noun" else "$selected $noun"
}
