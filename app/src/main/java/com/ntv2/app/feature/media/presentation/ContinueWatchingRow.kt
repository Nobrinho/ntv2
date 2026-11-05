package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.core.ui.icons.NbrIcons

/** Título de seção do protótipo: 18 bold no celular, 32 px → 16 sp na TV. */
@Composable
internal fun LibrarySectionTitle(text: androidx.compose.ui.text.AnnotatedString, useTvLayout: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NbrType.Section.copy(fontSize = if (useTvLayout) 16.sp else 18.sp, lineHeight = if (useTvLayout) 19.sp else 22.sp),
        color = TEXT_PRIMARY,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun LibrarySectionTitle(text: String, useTvLayout: Boolean, modifier: Modifier = Modifier) =
    LibrarySectionTitle(androidx.compose.ui.text.AnnotatedString(text), useTvLayout, modifier)

/**
 * Trilha horizontal "Continuar assistindo" exibida acima da grade da biblioteca. Cada card é o
 * frame/pôster do vídeo com barra de progresso e "faltam X"; selecionar abre os Detalhes (onde ficam
 * Continuar/Recomeçar), igual a um card da grade.
 * Medidas do protótipo: celular 240×135 dp, raio 12; TV 360×203 px → 180×101 dp, raio 10.
 */
@Composable
internal fun ContinueWatchingRow(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    useTvLayout: Boolean,
    onCardClick: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val cardWidth = if (useTvLayout) 180.dp else 240.dp
    val cardHeight = if (useTvLayout) 101.dp else 135.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (useTvLayout) 4.dp else 14.dp)) {
        LibrarySectionTitle("Continuar assistindo", useTvLayout)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // Folga para o foco (escala + brilho) da TV não ser cortado.
            contentPadding = PaddingValues(vertical = if (useTvLayout) 6.dp else 0.dp)
        ) {
            items(items, key = { it.mediaId }) { media ->
                ContinueCard(
                    media = media,
                    showCovers = showCovers,
                    width = cardWidth,
                    height = cardHeight,
                    useTvLayout = useTvLayout,
                    onClick = { onCardClick(media) }
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    media: MediaCardUi,
    showCovers: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    useTvLayout: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = if (useTvLayout) RoundedCornerShape(10.dp) else BrandShapes.Md
    // Card horizontal (16:9): banner > frame do vídeo > pôster (retrato, só como último recurso).
    val cover = media.backdropPath ?: media.thumbnailPath ?: media.posterPath
    Column(modifier = Modifier.width(width), verticalArrangement = Arrangement.spacedBy(if (useTvLayout) 6.dp else 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .glideTarget(if (useTvLayout) 10.dp else 12.dp)
                // Foco do protótipo (TV): contorno azul com brilho.
                .then(
                    if (focused && !gliding && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        Modifier.shadow(12.dp, shape, ambientColor = BRAND_ACCENT, spotColor = BRAND_ACCENT)
                    } else Modifier
                )
                .clip(shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .background(com.ntv2.app.core.ui.BrandColors.SurfaceAlt)
        ) {
            if (showCovers && cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Degradê do protótipo: transparente até a metade, 70% preto embaixo (leitura da barra).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xB3000000)))
            )
            // Botão de play ao focar.
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (useTvLayout) 40.dp else 46.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(NbrIcons.PlayFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            // Barra de progresso: trilho branco 20% e progresso azul, colados no rodapé.
            if (media.progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(if (useTvLayout) 3.dp else 4.dp)
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(media.progress)
                            .fillMaxSize()
                            .background(BRAND_ACCENT)
                    )
                }
            }
            if (focused && !gliding) {
                Box(Modifier.matchParentSize().border(if (useTvLayout) 1.5.dp else 2.dp, BRAND_ACCENT, shape))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (useTvLayout) 6.dp else 8.dp)) {
            Text(
                media.title,
                color = TEXT_PRIMARY,
                style = if (useTvLayout) NbrType.ItemTitle.copy(fontSize = 11.sp, lineHeight = 13.sp) else NbrType.BodyStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            remainingLabel(media)?.let {
                Text(
                    it,
                    color = BRAND_ACCENT,
                    style = if (useTvLayout) NbrType.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp) else NbrType.Caption,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Trilha horizontal de pôsteres (2:3) — "Recomendados para você", "Minha lista" etc. Selecionar abre os Detalhes.
 * [libraryStyle] = visual da Biblioteca do protótipo (título de seção, nome sob o pôster, selo).
 */
@Composable
internal fun PosterTrackRow(
    label: String,
    items: List<MediaCardUi>,
    showCovers: Boolean,
    useTvLayout: Boolean,
    onCardClick: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier,
    libraryStyle: Boolean = false
) = PosterTrackRow(androidx.compose.ui.text.AnnotatedString(label), items, showCovers, useTvLayout, onCardClick, modifier, libraryStyle)

/** Igual, com título formatado (ex.: "Porque você viu" + título em destaque). */
@Composable
internal fun PosterTrackRow(
    label: androidx.compose.ui.text.AnnotatedString,
    items: List<MediaCardUi>,
    showCovers: Boolean,
    useTvLayout: Boolean,
    onCardClick: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier,
    libraryStyle: Boolean = false
) {
    if (items.isEmpty()) return
    // Protótipo: celular 112×168 dp; TV 200×300 px → 100×150 dp.
    val posterWidth = when {
        libraryStyle && useTvLayout -> 100.dp
        useTvLayout -> 132.dp
        else -> 112.dp
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (libraryStyle) (if (useTvLayout) 4.dp else 14.dp) else 10.dp)
    ) {
        if (libraryStyle) {
            LibrarySectionTitle(label, useTvLayout)
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color.White)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = if (libraryStyle && !useTvLayout) 0.dp else 6.dp)
        ) {
            items(items, key = { it.mediaId }) { media ->
                if (libraryStyle) {
                    Column(modifier = Modifier.width(posterWidth), verticalArrangement = Arrangement.spacedBy(if (useTvLayout) 6.dp else 8.dp)) {
                        PosterCard(
                            media = media,
                            showCovers = showCovers,
                            width = posterWidth,
                            showBadge = true,
                            radius = if (useTvLayout) 10.dp else 12.dp,
                            onClick = { onCardClick(media) }
                        )
                        Text(
                            media.title,
                            color = TEXT_PRIMARY,
                            style = if (useTvLayout) NbrType.ItemTitle.copy(fontSize = 11.sp, lineHeight = 13.sp) else NbrType.Chip,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    PosterCard(
                        media = media,
                        showCovers = showCovers,
                        width = posterWidth,
                        onClick = { onCardClick(media) }
                    )
                }
            }
        }
    }
}

/** Pôster 2:3 do protótipo: raio 12, contorno sutil; [showBadge] põe o selo (FHD/SÉRIE) embaixo à esquerda. */
@Composable
internal fun PosterCard(
    media: MediaCardUi,
    showCovers: Boolean,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    radius: androidx.compose.ui.unit.Dp = 12.dp,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = RoundedCornerShape(radius)
    val cover = (media.posterPath ?: media.thumbnailPath)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) }
    Box(
        modifier = modifier
            .width(width)
            .height(width * 3 / 2)
            .glideTarget(radius)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(com.ntv2.app.core.ui.BrandColors.SurfaceAlt)
    ) {
        if (showCovers && cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!showBadge) {
            Text(
                media.title,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
        if (showBadge) {
            val badge = if (media.mediaId.startsWith("series_")) "SÉRIE"
                else com.ntv2.app.core.player.VideoResolution.label(height = media.videoHeight)
            if (badge != null) {
                PosterBadge(badge, Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        // Contorno sutil do protótipo em repouso; azul no foco (quando o anel deslizante não está ativo).
        Box(
            Modifier.matchParentSize().border(
                if (focused && !gliding) 2.dp else 1.dp,
                if (focused && !gliding) BRAND_ACCENT else com.ntv2.app.core.ui.BrandColors.BorderSubtle,
                shape
            )
        )
    }
}

/** Selo do protótipo sobre a capa: fundo preto 67%, raio 6, texto 10 bold. */
@Composable
internal fun PosterBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TEXT_PRIMARY,
        style = NbrType.Tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        maxLines = 1,
        modifier = modifier
            .clip(BrandShapes.Xs)
            .background(Color(0xAA000000))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Texto principal do protótipo (#F5F5F7). */
internal val TEXT_PRIMARY = com.ntv2.app.core.ui.BrandColors.TextPrimary

/** "faltam X" a partir da fração assistida e da duração; null se não dá para estimar. */
private fun remainingLabel(media: MediaCardUi): String? {
    if (media.durationSeconds <= 0 || media.progress <= 0f) return null
    val remainingSeconds = (media.durationSeconds * (1f - media.progress)).toInt()
    if (remainingSeconds <= 0) return null
    return "faltam ${durationLabel(remainingSeconds)}"
}
