package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.ConfirmDialog
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.mainBottomNavReserve
import com.ntv2.app.feature.media.presentation.state.HistoryEntryUi
import com.ntv2.app.feature.media.presentation.state.MediaCardUi

private enum class MyStuffTab { Continue, MyList, History }

private val MyStuffTabs = listOf(
    MyStuffTab.Continue to "Continuar",
    MyStuffTab.MyList to "Minha lista",
    MyStuffTab.History to "Histórico"
)

/**
 * Aba "Meu" (mobile), telas 25 e 26 do protótipo: título "Meu", segmentos Continuar / Minha lista /
 * Histórico e o conteúdo da aba. Os atalhos do topo do protótipo (downloads, pedidos, notificações,
 * perfil) são funções que o app não tem e ficam de fora; no lugar fica o fechar que o app já tinha.
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
    // Conteúdo rola por trás da tab bar flutuante, como na Biblioteca.
    val bottomReserve = mainBottomNavReserve() + 12.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MyStuffTitle(useTvLayout = false, modifier = Modifier.weight(1f))
                if (tab == MyStuffTab.History && history.isNotEmpty()) {
                    MyStuffTextAction(
                        label = "Limpar tudo",
                        useTvLayout = false,
                        color = BrandColors.Error,
                        onClick = { confirmClear = true }
                    )
                }
                MyStuffCircleButton(icon = NbrIcons.X, description = "Fechar", useTvLayout = false, onClick = onClose)
            }

            MyStuffSegments(options = MyStuffTabs, selected = tab, onSelect = { tab = it })

            val contentPadding = PaddingValues(bottom = bottomReserve)
            when (tab) {
                MyStuffTab.Continue -> ContinueList(
                    items = continueWatching,
                    showCovers = showCovers,
                    contentPadding = contentPadding,
                    onContinue = onContinue,
                    onRowClick = onCardClick
                )
                MyStuffTab.MyList -> PosterGrid(
                    items = myList,
                    showCovers = showCovers,
                    contentPadding = contentPadding,
                    onCardClick = onCardClick
                )
                MyStuffTab.History -> HistoryList(
                    entries = history,
                    showCovers = showCovers,
                    contentPadding = contentPadding,
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
                icon = NbrIcons.Trash2,
                confirmLabel = "Limpar",
                confirmIcon = NbrIcons.Trash2,
                cancelLabel = "Cancelar",
                cancelIcon = NbrIcons.X,
                destructive = true,
                onConfirm = { confirmClear = false; onClearHistory() },
                onDismiss = { confirmClear = false }
            )
        }
    }
}

/** Continuar: mesmas linhas do histórico, com "faltam X" em accent e o play à direita. */
@Composable
private fun ContinueList(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    contentPadding: PaddingValues,
    onContinue: (MediaCardUi) -> Unit,
    onRowClick: (MediaCardUi) -> Unit
) {
    if (items.isEmpty()) {
        MyStuffEmpty("Nada em andamento. Comece a assistir algo e ele aparece aqui."); return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = contentPadding) {
        items(items, key = { it.mediaId }) { media ->
            MyStuffMediaRow(
                media = media,
                showCovers = showCovers,
                subtitle = remainingSubtitle(media),
                subtitleColor = myStuffSubtitleColor(completed = false, progress = media.progress),
                useTvLayout = false,
                onClick = { onRowClick(media) }
            ) {
                MyStuffRowAction(
                    icon = NbrIcons.PlayFilled,
                    description = "Continuar",
                    useTvLayout = false,
                    prominent = true,
                    onClick = { onContinue(media) }
                )
            }
        }
    }
}

/** Histórico (tela 26): grupos por dia ("Hoje", "Ontem") com as linhas; gap 18 como no protótipo. */
@Composable
private fun HistoryList(
    entries: List<HistoryEntryUi>,
    showCovers: Boolean,
    contentPadding: PaddingValues,
    onContinue: (MediaCardUi) -> Unit,
    onRemove: (String) -> Unit,
    onRowClick: (MediaCardUi) -> Unit
) {
    if (entries.isEmpty()) {
        MyStuffEmpty("Nada por aqui ainda. Os títulos que você assistir aparecem no histórico."); return
    }
    val grouped = remember(entries) { entries.groupBy { dayBucket(it.updatedAt) } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = contentPadding) {
        grouped.forEach { (label, rows) ->
            item(key = "d_$label") { MyStuffDayLabel(label, useTvLayout = false) }
            items(rows, key = { it.card.mediaId }) { entry ->
                MyStuffMediaRow(
                    media = entry.card,
                    showCovers = showCovers,
                    subtitle = historySubtitle(entry),
                    subtitleColor = myStuffSubtitleColor(entry.completed, entry.card.progress),
                    useTvLayout = false,
                    onClick = { onRowClick(entry.card) }
                ) {
                    if (entry.completed) {
                        MyStuffWatchedMark(useTvLayout = false)
                    } else {
                        MyStuffRowAction(
                            icon = NbrIcons.PlayFilled,
                            description = "Continuar",
                            useTvLayout = false,
                            prominent = true,
                            onClick = { onContinue(entry.card) }
                        )
                    }
                    MyStuffRowAction(
                        icon = NbrIcons.X,
                        description = "Remover do histórico",
                        useTvLayout = false,
                        prominent = false,
                        onClick = { onRemove(entry.card.mediaId) }
                    )
                }
            }
        }
    }
}

/** Minha lista (tela 25): grade de 3 PosterCard com selo, gap 10 entre colunas e 14 entre linhas. */
@Composable
private fun PosterGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    contentPadding: PaddingValues,
    onCardClick: (MediaCardUi) -> Unit
) {
    if (items.isEmpty()) {
        MyStuffEmpty("Sua lista está vazia. Toque no coração de um filme para salvá-lo."); return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellWidth = (maxWidth - 20.dp) / 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = contentPadding
        ) {
            gridItems(items, key = { it.mediaId }) { media ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosterCard(
                        media = media,
                        showCovers = showCovers,
                        width = cellWidth,
                        showBadge = true,
                        onClick = { onCardClick(media) }
                    )
                    MyStuffPosterTitle(media.title, useTvLayout = false)
                }
            }
        }
    }
}

/** "faltam X" para a aba Continuar. */
private fun remainingSubtitle(media: MediaCardUi): String? {
    if (media.durationSeconds <= 0 || media.progress <= 0f) return media.channelName
    val remaining = (media.durationSeconds * (1f - media.progress)).toInt()
    if (remaining <= 0) return media.channelName
    return "faltam ${durationLabel(remaining)}"
}
