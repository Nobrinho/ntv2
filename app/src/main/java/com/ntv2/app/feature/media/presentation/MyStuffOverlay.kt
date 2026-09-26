package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.feature.media.presentation.state.HistoryEntryUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi

private enum class MyStuffTab { Continue, MyList, History }

/**
 * Aba "Meu" (mobile): controle segmentado com Continuar assistindo, Minha lista e Histórico —
 * reúne toda a personalização num só lugar no celular.
 */
@Composable
internal fun MyStuffOverlay(
    continueWatching: List<MediaCardUi>,
    myList: List<MediaCardUi>,
    history: List<HistoryEntryUi>,
    showCovers: Boolean,
    onCardClick: (MediaCardUi) -> Unit,
    onContinue: (MediaCardUi) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    var tab by remember { mutableStateOf(MyStuffTab.Continue) }
    var confirmClear by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2050505))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meu", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                if (tab == MyStuffTab.History && history.isNotEmpty()) {
                    TextPill(icon = Icons.Filled.DeleteSweep, label = "Limpar", destructive = true, onClick = { confirmClear = true })
                }
                CircleIconButton(icon = Icons.Filled.Close, description = "Fechar", onClick = onClose)
            }

            SegmentedControl(
                selected = tab,
                onSelect = { tab = it }
            )

            when (tab) {
                MyStuffTab.Continue -> RowList(
                    items = continueWatching,
                    showCovers = showCovers,
                    emptyMessage = "Nada em andamento. Comece a assistir algo e ele aparece aqui.",
                    trailing = { media -> PlayPill(onClick = { onContinue(media) }) },
                    subtitleFor = { media -> remainingSubtitle(media) },
                    onRowClick = onCardClick
                )
                MyStuffTab.MyList -> PosterGrid(
                    items = myList,
                    showCovers = showCovers,
                    emptyMessage = "Sua lista está vazia. Toque no coração de um filme para salvá-lo.",
                    onCardClick = onCardClick
                )
                MyStuffTab.History -> HistoryList(
                    entries = history,
                    showCovers = showCovers,
                    onContinue = onContinue,
                    onRemove = onRemoveHistory,
                    onRowClick = onCardClick
                )
            }
        }

        if (confirmClear) {
            ConfirmDialog(
                title = "Limpar histórico?",
                message = "Isto remove todos os títulos do seu histórico neste aparelho. Sua Minha lista não é afetada.",
                icon = Icons.Filled.DeleteSweep,
                confirmLabel = "Limpar",
                confirmIcon = Icons.Filled.DeleteSweep,
                cancelLabel = "Cancelar",
                cancelIcon = Icons.Filled.Close,
                destructive = true,
                onConfirm = { confirmClear = false; onClearHistory() },
                onDismiss = { confirmClear = false }
            )
        }
    }
}

@Composable
private fun SegmentedControl(selected: MyStuffTab, onSelect: (MyStuffTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141417))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Segment("Continuar", selected == MyStuffTab.Continue, Modifier.weight(1f)) { onSelect(MyStuffTab.Continue) }
        Segment("Minha lista", selected == MyStuffTab.MyList, Modifier.weight(1f)) { onSelect(MyStuffTab.MyList) }
        Segment("Histórico", selected == MyStuffTab.History, Modifier.weight(1f)) { onSelect(MyStuffTab.History) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .background(if (selected) BRAND_GREEN else Color.Transparent)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF0E0E0E) else Color(0xFF9A9A9A),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

/** Lista vertical de linhas (thumb + título + subtítulo + ação). Usada por Continuar. */
@Composable
private fun RowList(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    emptyMessage: String,
    trailing: @Composable (MediaCardUi) -> Unit,
    subtitleFor: (MediaCardUi) -> String?,
    onRowClick: (MediaCardUi) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(emptyMessage); return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(items, key = { it.mediaId }) { media ->
            ThumbRow(
                media = media,
                showCovers = showCovers,
                subtitle = subtitleFor(media),
                onClick = { onRowClick(media) },
                trailing = { trailing(media) }
            )
        }
    }
}

@Composable
private fun HistoryList(
    entries: List<HistoryEntryUi>,
    showCovers: Boolean,
    onContinue: (MediaCardUi) -> Unit,
    onRemove: (String) -> Unit,
    onRowClick: (MediaCardUi) -> Unit
) {
    if (entries.isEmpty()) {
        EmptyState("Nada por aqui ainda. Os títulos que você assistir aparecem no histórico."); return
    }
    val grouped = remember(entries) { entries.groupBy { dayBucket(it.updatedAt) } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        grouped.forEach { (label, rows) ->
            item(key = "d_$label") {
                Text(label.uppercase(), color = Color(0xFF6A6A6A), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
            }
            items(rows, key = { it.card.mediaId }) { entry ->
                ThumbRow(
                    media = entry.card,
                    showCovers = showCovers,
                    subtitle = historySubtitle(entry),
                    onClick = { onRowClick(entry.card) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (entry.completed) {
                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0x242BEE34)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = "Assistido", tint = BRAND_GREEN, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                PlayPill(onClick = { onContinue(entry.card) })
                            }
                            CircleIconButton(icon = Icons.Filled.Remove, description = "Remover do histórico", onClick = { onRemove(entry.card.mediaId) })
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ThumbRow(
    media: MediaCardUi,
    showCovers: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val cover = media.posterPath ?: media.thumbnailPath
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFF141417))
            .padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(104.dp).height(59.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF1C1C20))) {
            if (showCovers && cover != null) {
                AsyncImage(model = cover, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (media.progress > 0f) {
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x38FFFFFF))) {
                    Box(modifier = Modifier.fillMaxWidth(media.progress).fillMaxSize().background(BRAND_GREEN))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(media.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing()
    }
}

@Composable
private fun PosterGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    emptyMessage: String,
    onCardClick: (MediaCardUi) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(emptyMessage); return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        gridItems(items, key = { it.mediaId }) { media ->
            val cover = media.posterPath ?: media.thumbnailPath
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(246.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = { onCardClick(media) })
                    .background(Color(0xFF1C1C20))
            ) {
                if (showCovers && cover != null) {
                    AsyncImage(model = cover, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(media.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color(0xFF9A9A9A), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun PlayPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).background(BRAND_GREEN),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Continuar", tint = Color(0xFF0E0E0E), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TextPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean, onClick: () -> Unit) {
    val content = if (destructive) Color(0xFFFF7A7A) else Color.White
    Row(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).background(Color(0x1FFFFFFF)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).background(Color(0x1FFFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color(0xFFCFCFCF), modifier = Modifier.size(20.dp))
    }
}

/** "faltam X" para a aba Continuar. */
private fun remainingSubtitle(media: MediaCardUi): String? {
    if (media.durationSeconds <= 0 || media.progress <= 0f) return media.channelName
    val remaining = (media.durationSeconds * (1f - media.progress)).toInt()
    if (remaining <= 0) return media.channelName
    return "faltam ${durationLabel(remaining)}"
}
