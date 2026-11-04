package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.feature.media.presentation.state.MediaCardUi

/*
 * Peças das telas "Meu" do protótipo (25 Meu — minha lista, 26 Meu — histórico e TV 12 Meu).
 * Medidas em docs/PROTOTIPO-ESPEC.md; celular 1 px = 1 dp, TV já dividida por 2.
 */

/** Título "Meu": 28/700 no celular, 32/800 na TV. */
@Composable
internal fun MyStuffTitle(useTvLayout: Boolean, modifier: Modifier = Modifier) {
    Text(
        "Meu",
        style = if (useTvLayout) NbrType.ScreenTitle.copy(fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold)
        else NbrType.ScreenTitle.copy(lineHeight = 34.sp),
        color = TEXT_PRIMARY,
        maxLines = 1,
        modifier = modifier
    )
}

/**
 * Segmentos do celular: trilho 44 (fundo surface, padding 4, pílula) com segmentos de peso igual,
 * 36 de altura; ativo accent / on-accent, repouso transparente / text-2, texto 13/700.
 */
@Composable
internal fun <T> MyStuffSegments(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(BrandShapes.Pill)
            .background(BrandColors.Surface)
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(BrandShapes.Pill)
                    .background(if (active) BrandColors.Accent else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = NbrType.Chip.copy(fontWeight = FontWeight.Bold),
                    color = if (active) BrandColors.OnCta else BrandColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Chip de segmento da TV: 26 de altura, pílula, padding 0/12, texto 11/600; ativo accent / on-accent,
 * repouso fill / text-2. Em foco: contorno 2 accent (branco quando o chip já está ativo).
 */
@Composable
internal fun MyStuffTvChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = BrandShapes.Pill
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .background(if (selected) BrandColors.Accent else BrandColors.Fill)
            .then(if (focused) Modifier.border(2.dp, if (selected) Color.White else BrandColors.Accent, shape) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = NbrType.Chip.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = if (selected) BrandColors.OnCta else BrandColors.TextSecondary,
            maxLines = 1
        )
    }
}

/** Ação de texto do topo ("Limpar tudo"): 13/600 error no celular; na TV vira chip 26 com foco. */
@Composable
internal fun MyStuffTextAction(
    label: String,
    useTvLayout: Boolean,
    color: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = BrandShapes.Pill
    Row(
        modifier = Modifier
            .height(if (useTvLayout) 26.dp else 36.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .then(
                if (useTvLayout) Modifier
                    .background(if (focused) BrandColors.SurfaceAlt else BrandColors.Fill)
                    .then(if (focused) Modifier.border(2.dp, BrandColors.Accent, shape) else Modifier)
                else Modifier
            )
            .padding(horizontal = if (useTvLayout) 12.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (useTvLayout) 12.dp else 15.dp))
        Text(
            label,
            style = if (useTvLayout) NbrType.Chip.copy(fontSize = 11.sp, lineHeight = 13.sp) else NbrType.Chip,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * Botão circular do topo (mesmo estilo dos atalhos do protótipo): 36 no celular / 26 na TV, fundo fill,
 * ícone 18 / 14 text. Na TV, em foco, contorno 2 accent.
 */
@Composable
internal fun MyStuffCircleButton(
    icon: ImageVector,
    description: String,
    useTvLayout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(if (useTvLayout) 26.dp else 36.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.SurfaceAlt else BrandColors.Fill)
            .then(if (focused) Modifier.border(2.dp, BrandColors.Accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = TEXT_PRIMARY, modifier = Modifier.size(if (useTvLayout) 14.dp else 18.dp))
    }
}

/** Rótulo do dia no histórico: 13/700 muted no celular ("Hoje", "Ontem"); 11/700 na TV. */
@Composable
internal fun MyStuffDayLabel(label: String, useTvLayout: Boolean, modifier: Modifier = Modifier) {
    Text(
        label,
        style = if (useTvLayout) NbrType.Overline.copy(letterSpacing = 0.sp) else NbrType.Chip.copy(fontWeight = FontWeight.Bold),
        color = BrandColors.TextMuted,
        modifier = modifier
    )
}

/** Cor do subtítulo de uma linha: concluído success, em andamento accent, sem progresso muted. */
internal fun myStuffSubtitleColor(completed: Boolean, progress: Float): Color = when {
    completed -> BrandColors.Success
    progress > 0f -> BrandColors.Accent
    else -> BrandColors.TextMuted
}

/**
 * Linha do histórico / continuar (protótipo 26): cartão surface, raio 14, padding 10, gap 12;
 * miniatura 96×56 raio 8 com escurecimento e trilho 3; título 14/600 + subtítulo 11/400 (gap 3).
 * TV: padding 7, raio 10, miniatura 112×63 raio 7, trilho 4, título 13/700, subtítulo 10/400.
 * As ações ficam à direita ([trailing]).
 */
@Composable
internal fun MyStuffMediaRow(
    media: MediaCardUi,
    showCovers: Boolean,
    subtitle: String?,
    subtitleColor: Color,
    useTvLayout: Boolean,
    onClick: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(if (useTvLayout) 10.dp else 14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .then(if (useTvLayout) Modifier.border(1.dp, BrandColors.BorderSubtle, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(if (useTvLayout) 7.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MyStuffThumb(
            media = media,
            showCovers = showCovers,
            width = if (useTvLayout) 112.dp else 96.dp,
            height = if (useTvLayout) 63.dp else 56.dp,
            radius = if (useTvLayout) 7.dp else 8.dp,
            railHeight = if (useTvLayout) 4.dp else 3.dp
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                media.title,
                style = if (useTvLayout) NbrType.Chip.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                else NbrType.BodyStrong.copy(lineHeight = 17.sp),
                color = TEXT_PRIMARY,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = NbrType.Caption.copy(fontSize = if (useTvLayout) 10.sp else 11.sp, lineHeight = 13.sp),
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

/** Miniatura 16:9 com escurecimento (transparente até 50% → preto 70%) e trilho de progresso no fundo. */
@Composable
private fun MyStuffThumb(
    media: MediaCardUi,
    showCovers: Boolean,
    width: Dp,
    height: Dp,
    radius: Dp,
    railHeight: Dp
) {
    val cover = media.posterPath ?: media.thumbnailPath
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(BrandColors.SurfaceAlt)
    ) {
        if (showCovers && cover != null) {
            AsyncImage(model = cover, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xB3000000))
                )
            )
        }
        if (media.progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(railHeight)
                    .background(Color(0x33FFFFFF))
            ) {
                Box(modifier = Modifier.fillMaxWidth(media.progress.coerceIn(0f, 1f)).fillMaxHeight().background(BrandColors.Accent))
            }
        }
    }
}

/**
 * Ação compacta da linha: círculo 32 (TV 26) com ícone. [prominent] = fundo accent@15% e ícone accent
 * (Continuar); senão sem fundo e ícone muted (remover, como o "x" do protótipo). Em foco: contorno 2 accent.
 */
@Composable
internal fun MyStuffRowAction(
    icon: ImageVector,
    description: String,
    useTvLayout: Boolean,
    prominent: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(if (useTvLayout) 26.dp else 32.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    prominent -> Color(0x2666D9FF)
                    focused -> BrandColors.Fill
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(2.dp, BrandColors.Accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                prominent -> BrandColors.Accent
                focused -> TEXT_PRIMARY
                else -> BrandColors.TextMuted
            },
            modifier = Modifier.size(if (prominent) (if (useTvLayout) 12.dp else 14.dp) else (if (useTvLayout) 14.dp else 16.dp))
        )
    }
}

/** Marca de concluído na linha do histórico: `CircleCheck` success (20 celular / 16 TV). */
@Composable
internal fun MyStuffWatchedMark(useTvLayout: Boolean) {
    Box(modifier = Modifier.size(if (useTvLayout) 26.dp else 32.dp), contentAlignment = Alignment.Center) {
        Icon(
            NbrIcons.CircleCheck,
            contentDescription = "Assistido",
            tint = BrandColors.Success,
            modifier = Modifier.size(if (useTvLayout) 16.dp else 20.dp)
        )
    }
}

/** Título abaixo do pôster: 13/600 text no celular (até 2 linhas); 11/600 na TV. */
@Composable
internal fun MyStuffPosterTitle(title: String, useTvLayout: Boolean, modifier: Modifier = Modifier) {
    Text(
        title,
        style = if (useTvLayout) NbrType.Chip.copy(fontSize = 11.sp, lineHeight = 14.sp) else NbrType.Chip.copy(lineHeight = 16.sp),
        color = TEXT_PRIMARY,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Mensagem de lista vazia, centralizada (14/400 muted). */
@Composable
internal fun MyStuffEmpty(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = NbrType.Body.copy(lineHeight = 20.sp),
            color = BrandColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
