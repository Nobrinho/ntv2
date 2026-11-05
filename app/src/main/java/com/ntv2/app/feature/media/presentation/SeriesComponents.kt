package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.feature.media.domain.MediaItemSummary
import kotlin.math.roundToInt

/*
 * Peças da tela de série do protótipo (nbrplay.pen: celular 16/17/18, TV 10). Medidas em
 * docs/PROTOTIPO-ESPEC.md (seções 2.5, 2.13, 2.14 e telas 16-18 / TV 10).
 */

/** "text" do protótipo (#F5F5F7). */
internal val SeriesText = com.ntv2.app.core.ui.BrandColors.TextPrimary

/** A partir daqui o episódio conta como assistido (créditos finais não são vistos). */
internal const val SERIES_WATCHED_FRACTION = 0.92f

/** Chip 2.5 (repouso): fill + contorno sutil, texto text-2. Celular 32/13; TV 26/11. */
@Composable
internal fun SeriesChip(text: String, tv: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(if (tv) 26.dp else 32.dp)
            .clip(RoundedCornerShape(50))
            .background(BrandColors.Fill)
            .border(1.dp, BrandColors.BorderSubtle, RoundedCornerShape(50))
            .padding(horizontal = if (tv) 12.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = if (tv) NbrType.Chip.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            else NbrType.Chip.copy(fontWeight = FontWeight.Medium),
            color = BrandColors.TextSecondary,
            maxLines = 1
        )
    }
}

/**
 * Chip de temporada da TV (2.5 TV): 26 de altura, selecionada em accent/on-accent; foco com o anel
 * da tela. Clicar seleciona a temporada.
 */
@Composable
internal fun SeriesTvSeasonChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .height(26.dp)
            .glideTarget(13.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (selected) BrandColors.Accent else BrandColors.Fill)
            .then(
                when {
                    focused && !gliding -> Modifier.border(2.dp, BrandColors.Accent, shape)
                    selected -> Modifier
                    else -> Modifier.border(1.dp, BrandColors.BorderSubtle, shape)
                }
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = NbrType.Chip.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) BrandColors.OnCta else BrandColors.TextSecondary,
            maxLines = 1
        )
    }
}

/** Chip seletor de temporada (celular): 36 de altura, pílula, fill + contorno border, ChevronDown. Abre o sheet 17. */
@Composable
internal fun SeriesSeasonSelectorChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(BrandColors.Fill)
            .border(1.dp, BrandColors.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NbrType.Chip, color = SeriesText, maxLines = 1)
        Icon(NbrIcons.ChevronDown, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(14.dp))
    }
}

/** Progresso da temporada: trilho 4 (fill) com barra success + "N de M assistidos". */
@Composable
internal fun SeriesSeasonProgress(watched: Int, total: Int, tv: Boolean, modifier: Modifier = Modifier) {
    if (total <= 0) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (tv) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BrandColors.Fill)
        ) {
            val f = (watched.toFloat() / total).coerceIn(0f, 1f)
            if (f > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BrandColors.Success)
                )
            }
        }
        Text(
            "$watched de $total assistidos",
            style = NbrType.Caption.copy(fontSize = if (tv) 10.sp else 11.sp, lineHeight = if (tv) 12.sp else 13.sp),
            color = BrandColors.TextMuted,
            maxLines = 1
        )
    }
}

/** Estado do episódio na lista (ícone à direita do cartão 2.14). */
internal enum class SeriesEpisodeState { None, Current, Watched }

internal fun seriesEpisodeState(progress: Float): SeriesEpisodeState = when {
    progress >= SERIES_WATCHED_FRACTION -> SeriesEpisodeState.Watched
    progress > 0f -> SeriesEpisodeState.Current
    else -> SeriesEpisodeState.None
}

/** "E3 · Nome" (ou o código/título quando faltam números). */
internal fun MediaItemSummary.seriesEpisodeTitle(): String {
    val n = episodeNumber
    val name = title.trim()
    return when {
        n != null && name.isNotEmpty() -> "E$n · $name"
        n != null -> "Episódio $n"
        name.isNotEmpty() -> name
        else -> "Episódio"
    }
}

/** "58min · sinopse" (partes ausentes somem). */
private fun MediaItemSummary.seriesEpisodeSubtitle(): String? {
    val parts = buildList {
        if (durationSeconds > 0) add(durationLabel(durationSeconds))
        synopsis?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Cartão de episódio 2.14. Celular: padding 10, miniatura 112×68 (raio 10), raio 14; em andamento
 * fundo accent@7% e contorno 2 accent. TV: padding 7, miniatura 112×63 (raio 7), raio 10, altura 77,
 * foco pelo anel da tela. Toque longo (celular) abre as opções do episódio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesEpisodeCard(
    episode: MediaItemSummary,
    progress: Float,
    showCovers: Boolean,
    tv: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val state = seriesEpisodeState(progress)
    val current = state == SeriesEpisodeState.Current
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val radius = if (tv) 10.dp else 14.dp
    val shape = RoundedCornerShape(radius)
    val bg = when {
        current -> BrandColors.Accent.copy(alpha = if (tv) 0.08f else 0.07f)
        tv && focused -> BrandColors.SurfaceAlt
        else -> BrandColors.Surface
    }
    val (borderW, borderC) = when {
        tv && focused && !gliding -> 2.dp to BrandColors.Accent
        !tv && current -> 2.dp to BrandColors.Accent
        tv && current -> 1.dp to BrandColors.Accent.copy(alpha = 0.4f)
        else -> 1.dp to BrandColors.BorderSubtle
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (tv) Modifier.heightIn(min = 77.dp).glideTarget(radius) else Modifier)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(bg)
            .border(borderW, borderC, shape)
            .padding(if (tv) 7.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeriesEpisodeThumb(
            episode = episode,
            progress = progress,
            showCovers = showCovers,
            showTrack = current,
            width = 112,
            height = if (tv) 63 else 68,
            radius = if (tv) 7 else 10
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                episode.seriesEpisodeTitle(),
                style = NbrType.Secondary.copy(fontWeight = FontWeight.Bold),
                color = SeriesText,
                maxLines = if (tv) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            episode.seriesEpisodeSubtitle()?.let {
                Text(
                    it,
                    style = if (tv) NbrType.Caption.copy(fontSize = 10.sp, lineHeight = 13.5.sp)
                    else NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 14.85.sp),
                    color = BrandColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        when (state) {
            SeriesEpisodeState.Watched -> Icon(NbrIcons.CircleCheck, contentDescription = "Assistido", tint = BrandColors.Success, modifier = Modifier.size(20.dp))
            SeriesEpisodeState.Current -> Icon(NbrIcons.PlayFilled, contentDescription = "Em andamento", tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
            SeriesEpisodeState.None -> Unit
        }
    }
}

/** Miniatura do episódio com o escurecimento do 2.7 e, em andamento, trilho 4 + barra accent. */
@Composable
private fun SeriesEpisodeThumb(
    episode: MediaItemSummary,
    progress: Float,
    showCovers: Boolean,
    showTrack: Boolean,
    width: Int,
    height: Int,
    radius: Int
) {
    // w300 em vez do banner w1280 (decodificação e upload menores no Fire TV).
    val thumb = (episode.backdropPath ?: episode.posterPath)?.let { com.ntv2.app.core.ui.tmdbAtWidth(it, "w300") }
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2A3A5C), Color(0xFF0F1320)))
            )
    ) {
        if (showCovers && thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color(0xB3000000)))
        )
        if (showTrack) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .height(4.dp)
                        .background(BrandColors.Accent)
                )
            }
        }
    }
}

/**
 * Sheet 2.13 do celular: escurecimento preto@70%, painel ancorado embaixo (raio 28 em cima, surface,
 * contorno sutil, padding 10/24/28/24, gap 14), handle 40×4 e título 20/700. Entra deslizando em
 * 220 ms; voltar, tocar fora ou arrastar para baixo fecha.
 */
@Composable
internal fun BoxScope.SeriesSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val density = LocalDensity.current
        val dismissPx = with(density) { 80.dp.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, drag.roundToInt()) }
                .draggable(
                    state = rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { if (drag > dismissPx) onDismiss() else drag = 0f }
                )
                .clip(shape)
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.BorderSubtle, shape)
                // Engole toques no painel (não fecham o sheet).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandColors.Border)
            )
            Text(
                title,
                style = NbrType.SheetTitle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = SeriesText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content()
        }
    }
}

/** Opção do sheet 17: "Temporada N" + "X episódios · Y assistidos"; selecionada com contorno accent e Check. */
@Composable
internal fun SeriesSeasonOption(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .background(if (selected) BrandColors.Accent.copy(alpha = 0.08f) else BrandColors.SurfaceAlt)
            .border(if (selected) 2.dp else 1.dp, if (selected) BrandColors.Accent else BrandColors.BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = NbrType.Button.copy(fontSize = 16.sp, lineHeight = 19.sp), color = SeriesText, maxLines = 1)
            Text(subtitle, style = NbrType.Caption, color = BrandColors.TextMuted, maxLines = 1)
        }
        if (selected) Icon(NbrIcons.Check, contentDescription = "Selecionada", tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
    }
}

/** Lista rolável das opções de temporada (gap 14), limitada para o sheet não passar da tela. */
@Composable
internal fun SeriesSeasonOptions(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** Ação do sheet 18: linha de 52, ícone 20, gap 14, texto 15/500. */
@Composable
internal fun SeriesSheetAction(
    icon: ImageVector,
    label: String,
    color: Color = SeriesText,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = NbrType.BodyLarge.copy(fontWeight = FontWeight.Medium), color = color, maxLines = 1)
    }
}
