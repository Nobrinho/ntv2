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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.presentation.state.MediaCardUi

/**
 * Trilha horizontal "Continuar assistindo" exibida acima da grade da biblioteca. Cada card é o
 * frame/pôster do vídeo com barra de progresso e "faltam X"; selecionar abre os Detalhes (onde ficam
 * Continuar/Recomeçar), igual a um card da grade.
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
    val cardWidth = if (useTvLayout) 236.dp else 210.dp
    val cardHeight = if (useTvLayout) 133.dp else 118.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Continuar assistindo",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (useTvLayout) 16.dp else 12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.mediaId }) { media ->
                ContinueCard(
                    media = media,
                    showCovers = showCovers,
                    width = cardWidth,
                    height = cardHeight,
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
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val cover = media.posterPath ?: media.thumbnailPath
    Column(modifier = Modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .background(Color(0xFF1C1C20))
                .then(
                    if (focused) Modifier.border(3.dp, BRAND_GREEN, RoundedCornerShape(12.dp))
                    else Modifier
                )
        ) {
            if (showCovers && cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Botão de play ao focar.
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            // Barra de progresso.
            if (media.progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color(0x38FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(media.progress)
                            .fillMaxSize()
                            .background(BRAND_GREEN)
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            media.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        remainingLabel(media)?.let {
            Text(it, color = BRAND_GREEN, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Trilha horizontal de pôsteres (2:3) — usada em "Minha lista". Selecionar abre os Detalhes.
 */
@Composable
internal fun PosterTrackRow(
    label: String,
    items: List<MediaCardUi>,
    showCovers: Boolean,
    useTvLayout: Boolean,
    onCardClick: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val posterWidth = if (useTvLayout) 132.dp else 112.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = Color.White)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (useTvLayout) 16.dp else 12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.mediaId }) { media ->
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

@Composable
private fun PosterCard(
    media: MediaCardUi,
    showCovers: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val cover = media.posterPath ?: media.thumbnailPath
    Box(
        modifier = Modifier
            .width(width)
            .height(width * 3 / 2)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(Color(0xFF1C1C20))
            .then(if (focused) Modifier.border(3.dp, BRAND_GREEN, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        if (showCovers && cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                media.title,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
    }
}

/** "faltam X" a partir da fração assistida e da duração; null se não dá para estimar. */
private fun remainingLabel(media: MediaCardUi): String? {
    if (media.durationSeconds <= 0 || media.progress <= 0f) return null
    val remainingSeconds = (media.durationSeconds * (1f - media.progress)).toInt()
    if (remainingSeconds <= 0) return null
    return "faltam ${durationLabel(remainingSeconds)}"
}
