package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
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
    val adaptive = com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo()
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
    // Ao remover do histórico, a linha focada some e o foco se perdia (o D-pad ia parar no menu
    // lateral, por baixo). O foco passa para o "remover" da linha vizinha (a de baixo; senão a de cima);
    // sem linhas, volta para a aba.
    val removeFocus = remember { mutableMapOf<String, FocusRequester>() }
    var pendingRemoveFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entries) {
        if (entries.isEmpty()) {
            pendingRemoveFocus = null
            if (tab == ShelfTab.History) runCatching { tabFocus.getValue(tab).requestFocus() }
            return@LaunchedEffect
        }
        val target = pendingRemoveFocus ?: return@LaunchedEffect
        pendingRemoveFocus = null
        withFrameNanos { }
        runCatching { removeFocus[target]?.requestFocus() }
    }
    val removeEntry: (String) -> Unit = { id ->
        // Mesma ordem da tela (agrupada por dia).
        val order = entries.groupBy { dayBucket(it.updatedAt) }.values.flatten().map { it.card.mediaId }
        val i = order.indexOf(id)
        pendingRemoveFocus = order.getOrNull(i + 1) ?: order.getOrNull(i - 1)
        onRemove(id)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .trapFocus()
            .padding(
                horizontal = if (useTvLayout) adaptive.tvSafeHorizontal else 16.dp,
                vertical = if (useTvLayout) adaptive.tvSafeVertical else 16.dp
            )
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Topo do protótipo (TV 12 Meu): "Meu" 32/800 à esquerda e os chips de segmento à direita.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MyStuffTitle(useTvLayout = true, modifier = Modifier.weight(1f))
                ShelfTab.entries.forEach { t ->
                    // Ao receber foco (D-pad) a aba é escolhida — ← / → trocam de aba.
                    MyStuffTvChip(
                        label = t.label,
                        selected = t == tab,
                        modifier = Modifier.focusRequester(tabFocus.getValue(t)),
                        onFocused = { if (t != tab) onTabChange(t) },
                        onClick = { onTabChange(t) }
                    )
                }
                if (tab == ShelfTab.History && entries.isNotEmpty()) {
                    MyStuffTextAction(
                        label = "Limpar tudo",
                        useTvLayout = true,
                        color = BrandColors.Error,
                        icon = NbrIcons.Trash2,
                        onClick = { confirmClear = true }
                    )
                }
                MyStuffCircleButton(icon = NbrIcons.X, description = "Fechar", useTvLayout = true, onClick = onClose)
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
                MyStuffEmpty("Nada por aqui ainda. Os títulos que você assistir aparecem no histórico.")
            } else {
                val grouped = remember(entries) { entries.groupBy { dayBucket(it.updatedAt) } }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    grouped.forEach { (label, rows) ->
                        item(key = "h_$label") {
                            MyStuffDayLabel(label, useTvLayout = true, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                        }
                        items(rows, key = { it.card.mediaId }) { entry ->
                            HistoryRow(
                                entry = entry,
                                showCovers = showCovers,
                                onContinue = { onContinue(entry.card) },
                                removeFocus = removeFocus.getOrPut(entry.card.mediaId) { FocusRequester() },
                                onRemove = { removeEntry(entry.card.mediaId) }
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
                icon = NbrIcons.Trash2,
                confirmLabel = "Limpar",
                confirmIcon = NbrIcons.Trash2,
                cancelLabel = "Cancelar",
                cancelIcon = NbrIcons.X,
                destructive = true,
                onConfirm = { confirmClear = false; onClear() },
                onDismiss = { confirmClear = false }
            )
        }
    }
}

/**
 * Minha lista da TV: pôsteres 100×150 (raio 10) com título 11/600 embaixo (gap 6), 12 entre cards,
 * e a dica do protótipo no fim. A parte "segure OK para remover" da dica fica de fora: o app não tem.
 */
@Composable
private fun MyListGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    gridState: LazyGridState,
    focusFor: (String) -> FocusRequester,
    onOpenDetails: (MediaCardUi) -> Unit
) {
    if (items.isEmpty()) {
        MyStuffEmpty("Sua lista está vazia. Use o coração nos detalhes de um filme para salvá-lo.")
        return
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.FixedSize(100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
    ) {
        gridItems(items, key = { it.mediaId }) { media ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PosterCard(
                    media = media,
                    showCovers = showCovers,
                    width = 100.dp,
                    radius = 10.dp,
                    modifier = Modifier.focusRequester(focusFor(media.mediaId)),
                    onClick = { onOpenDetails(media) }
                )
                MyStuffPosterTitle(media.title, useTvLayout = true, modifier = Modifier.width(100.dp))
            }
        }
        item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Aperte OK em um título para abrir",
                style = NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = BrandColors.TextMuted
            )
        }
    }
}

/** Linha do histórico na TV: cartão do protótipo (2.14 TV) com Continuar / Assistido e remover. */
@Composable
private fun HistoryRow(
    entry: HistoryEntryUi,
    showCovers: Boolean,
    onContinue: () -> Unit,
    removeFocus: FocusRequester,
    onRemove: () -> Unit
) {
    MyStuffMediaRow(
        media = entry.card,
        showCovers = showCovers,
        subtitle = historySubtitle(entry),
        subtitleColor = myStuffSubtitleColor(entry.completed, entry.card.progress),
        useTvLayout = true,
        onClick = null
    ) {
        if (entry.completed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(NbrIcons.CircleCheck, contentDescription = null, tint = BrandColors.Success, modifier = Modifier.size(16.dp))
                Text("Assistido", color = BrandColors.Success, style = NbrType.Chip.copy(fontSize = 11.sp, lineHeight = 13.sp))
            }
        } else {
            MyStuffTextAction(
                label = "Continuar",
                useTvLayout = true,
                color = TEXT_PRIMARY,
                icon = NbrIcons.PlayFilled,
                onClick = onContinue
            )
        }
        MyStuffCircleButton(
            icon = NbrIcons.X,
            description = "Remover do histórico",
            useTvLayout = true,
            onClick = onRemove,
            modifier = Modifier.focusRequester(removeFocus)
        )
    }
}

/** "Canal · parou em 1 h 27 de 2 h 08" ou "Canal · concluído" (separador do protótipo). */
internal fun historySubtitle(entry: HistoryEntryUi): String {
    val media = entry.card
    val total = media.durationSeconds
    val channel = media.channelName
    if (entry.completed || media.progress <= 0f) {
        return if (entry.completed) "$channel · concluído" else channel
    }
    val watched = (total * media.progress).toInt()
    val totalLabel = if (total > 0) durationLabel(total) else ""
    return if (totalLabel.isNotEmpty()) "$channel · parou em ${durationLabel(watched)} de $totalLabel"
    else "$channel · parou em ${durationLabel(watched)}"
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
