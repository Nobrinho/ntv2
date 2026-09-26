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
import java.util.Calendar

/**
 * Histórico (personalização): lista agrupada por dia, com "Continuar" e remover por item, e
 * "Limpar tudo". Responsivo (TV/mobile). Único por dispositivo.
 */
@Composable
internal fun HistoryOverlay(
    entries: List<HistoryEntryUi>,
    showCovers: Boolean,
    useTvLayout: Boolean,
    onContinue: (com.ntv2.app.feature.media.presentation.state.MediaCardUi) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    var confirmClear by remember { mutableStateOf(false) }
    // Sem pedir foco, o overlay abria "morto": o D-pad continuava na grade atrás e não dava para
    // navegar nem fechar. Focar o botão Fechar ao abrir garante a navegação dentro do overlay.
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
    // Ao remover o último item, a linha focada some e o foco se perde: traz de volta para o Fechar.
    LaunchedEffect(entries.isEmpty()) {
        if (entries.isEmpty()) runCatching { closeFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2050505))
            .statusBarsPadding()
            .navigationBarsPadding()
            .trapFocus()
            .padding(horizontal = if (useTvLayout) 48.dp else 16.dp, vertical = if (useTvLayout) 32.dp else 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Histórico", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    HistoryTextButton(
                        icon = Icons.Filled.DeleteSweep,
                        label = "Limpar tudo",
                        destructive = true,
                        onClick = { confirmClear = true }
                    )
                }
                HistoryIconButton(icon = Icons.Filled.Close, description = "Fechar", onClick = onClose, modifier = Modifier.focusRequester(closeFocus))
            }

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nada por aqui ainda. Os títulos que você assistir aparecem no histórico.",
                        color = Color(0xFF9A9A9A),
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141417))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C1C20))
        ) {
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
            Text(media.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(historySubtitle(entry), color = Color(0xFF8A8A8A), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (entry.completed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = BRAND_GREEN, modifier = Modifier.size(18.dp))
                Text("Assistido", color = BRAND_GREEN, style = MaterialTheme.typography.labelMedium)
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
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x33FFFFFF) else Color(0x1FFFFFFF))
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
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
        Icon(icon, contentDescription = description, tint = if (focused) Color.Black else Color(0xFFCFCFCF), modifier = Modifier.size(20.dp))
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
