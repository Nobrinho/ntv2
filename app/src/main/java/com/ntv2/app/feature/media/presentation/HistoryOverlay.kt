package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.feature.media.presentation.state.HistoryEntryUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import java.util.Calendar

/** Abas da tela "Minha lista" da TV. */
internal enum class ShelfTab(val label: String) { MyList("Minha lista"), History("Histórico") }

/**
 * "Minha lista" da TV (aberta pelo rail): abas Minha lista (grade de capas → Detalhes) e Histórico
 * (lista agrupada por dia, com "Continuar", remover por item e "Limpar tudo"). Único por dispositivo.
 * Com foco nas abas, ← / → trocam de aba; ↓ entra no conteúdo.
 */
@Composable
internal fun MyListHistoryOverlay(
    tab: ShelfTab,
    onTabChange: (ShelfTab) -> Unit,
    myList: List<MediaCardUi>,
    entries: List<HistoryEntryUi>,
    showCovers: Boolean,
    useTvLayout: Boolean,
    onOpenDetails: (MediaCardUi) -> Unit,
    onContinue: (MediaCardUi) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    // Card da Minha lista que abriu os Detalhes: ao voltar deles, o foco retorna a esse card.
    focusMediaId: String? = null
) {
    BackHandler(enabled = true) { onClose() }
    var confirmClear by remember { mutableStateOf(false) }
    // Sem pedir foco, o overlay abria "morto": o D-pad continuava na grade atrás e não dava para
    // navegar nem fechar. Ao abrir, o foco vai para a aba atual (ou para o card de onde se saiu).
    val tabFocus = remember { ShelfTab.entries.associateWith { FocusRequester() } }
    val cardFocus = remember { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        val index = focusMediaId?.let { id -> myList.indexOfFirst { it.mediaId == id } } ?: -1
        if (tab == ShelfTab.MyList && index >= 0) {
            runCatching { gridState.scrollToItem(index) }
            withFrameNanos { }
            if (runCatching { cardFocus[myList[index].mediaId]?.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        runCatching { tabFocus.getValue(tab).requestFocus() }
    }
    // Ao remover o último item do histórico, a linha focada some e o foco se perde: volta para a aba.
    LaunchedEffect(entries.isEmpty()) {
        if (entries.isEmpty() && tab == ShelfTab.History) runCatching { tabFocus.getValue(tab).requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.ntv2.app.core.ui.BrandColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .trapFocus()
            .padding(horizontal = if (useTvLayout) 48.dp else 16.dp, vertical = if (useTvLayout) 32.dp else 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShelfTab.entries.forEach { t ->
                    ShelfTabPill(
                        label = t.label,
                        selected = t == tab,
                        modifier = Modifier.focusRequester(tabFocus.getValue(t)),
                        onSelect = { onTabChange(t) }
                    )
                }
                Box(modifier = Modifier.weight(1f))
                if (tab == ShelfTab.History && entries.isNotEmpty()) {
                    HistoryTextButton(
                        icon = Icons.Filled.DeleteSweep,
                        label = "Limpar tudo",
                        destructive = true,
                        onClick = { confirmClear = true }
                    )
                }
                HistoryIconButton(icon = Icons.Filled.Close, description = "Fechar", onClick = onClose)
            }

            if (tab == ShelfTab.MyList) {
                MyListGrid(
                    items = myList,
                    showCovers = showCovers,
                    gridState = gridState,
                    focusFor = { id -> cardFocus.getOrPut(id) { FocusRequester() } },
                    onOpenDetails = onOpenDetails
                )
            } else if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nada por aqui ainda. Os títulos que você assistir aparecem no histórico.",
                        color = Color(0xFF8E98A8),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                val grouped = remember(entries) { entries.groupBy { dayBucket(it.updatedAt) } }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    grouped.forEach { (label, rows) ->
                        item(key = "h_$label") {
                            Text(
                                label.uppercase(),
                                color = Color(0xFF6A6A6A),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(rows, key = { it.card.mediaId }) { entry ->
                            HistoryRow(
                                entry = entry,
                                showCovers = showCovers,
                                onContinue = { onContinue(entry.card) },
                                onRemove = { onRemove(entry.card.mediaId) }
                            )
                        }
                    }
                }
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
                onConfirm = { confirmClear = false; onClear() },
                onDismiss = { confirmClear = false }
            )
        }
    }
}

/** Aba selecionada: preenchida. Ao receber foco (D-pad) a aba é escolhida — ← / → trocam de aba. */
@Composable
private fun ShelfTabPill(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused && !selected) onSelect()
            }
            .clickable(onClick = onSelect)
            .background(if (selected) Color.White else Color(0x1FFFFFFF))
            .then(if (focused) Modifier.border(2.dp, BRAND_ACCENT, RoundedCornerShape(4.dp)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MyListGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    gridState: LazyGridState,
    focusFor: (String) -> FocusRequester,
    onOpenDetails: (MediaCardUi) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Sua lista está vazia. Use o coração nos detalhes de um filme para salvá-lo.",
                color = Color(0xFF8E98A8),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    LazyVerticalGrid(
        state = gridState,
        // Mesmo tamanho das capas das trilhas da grade principal.
        columns = GridCells.FixedSize(132.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        gridItems(items, key = { it.mediaId }) { media ->
            PosterCard(
                media = media,
                showCovers = showCovers,
                width = 132.dp,
                modifier = Modifier.focusRequester(focusFor(media.mediaId)),
                onClick = { onOpenDetails(media) }
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntryUi,
    showCovers: Boolean,
    onContinue: () -> Unit,
    onRemove: () -> Unit
) {
    val media = entry.card
    val cover = media.posterPath ?: media.thumbnailPath
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(com.ntv2.app.core.ui.BrandColors.Surface)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(com.ntv2.app.core.ui.BrandColors.SurfaceAlt)
        ) {
            if (showCovers && cover != null) {
                AsyncImage(model = cover, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (media.progress > 0f) {
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x38FFFFFF))) {
                    Box(modifier = Modifier.fillMaxWidth(media.progress).fillMaxSize().background(BRAND_ACCENT))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(media.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(historySubtitle(entry), color = Color(0xFF8E98A8), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (entry.completed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = BRAND_ACCENT, modifier = Modifier.size(18.dp))
                Text("Assistido", color = BRAND_ACCENT, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            HistoryTextButton(icon = Icons.Filled.PlayArrow, label = "Continuar", destructive = false, onClick = onContinue)
        }
        HistoryIconButton(icon = Icons.Filled.Remove, description = "Remover do histórico", onClick = onRemove)
    }
}

/** "Canal • parou em 1 h 27 de 2 h 08" ou "Canal • concluído". */
internal fun historySubtitle(entry: HistoryEntryUi): String {
    val media = entry.card
    val total = media.durationSeconds
    val channel = media.channelName
    if (entry.completed || media.progress <= 0f) {
        return if (entry.completed) "$channel • concluído" else channel
    }
    val watched = (total * media.progress).toInt()
    val totalLabel = if (total > 0) durationLabel(total) else ""
    return if (totalLabel.isNotEmpty()) "$channel • parou em ${durationLabel(watched)} de $totalLabel"
    else "$channel • parou em ${durationLabel(watched)}"
}

@Composable
private fun HistoryTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val content = when {
        destructive -> Color(0xFFFF7A7A)
        else -> Color.White
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x33FFFFFF) else Color(0x1FFFFFFF))
            .then(if (focused) Modifier.border(3.dp, com.ntv2.app.core.ui.BrandColors.Accent, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HistoryIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0x1FFFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (focused) Color.Black else com.ntv2.app.core.ui.BrandColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** Rótulo do dia: Hoje / Ontem / dd/MM/yyyy. */
internal fun dayBucket(timeMs: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMs }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Hoje"
        sameYear && dayDiff == 1 -> "Ontem"
        else -> {
            val d = then.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val m = (then.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            "$d/$m/${then.get(Calendar.YEAR)}"
        }
    }
}
