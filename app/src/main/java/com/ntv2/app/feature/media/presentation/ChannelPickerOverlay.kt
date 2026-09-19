package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ntv2.app.core.ui.trapFocus
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.presentation.state.ChannelChipUi

// Seletor de canal ativo.

@Composable
internal fun ChannelPickerOverlay(
    channels: List<ChannelChipUi>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .onPreviewKeyEvent { e ->
                when {
                    // Back/Esc fecha.
                    e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape) -> {
                        onDismiss(); true
                    }
                    // Prende o foco no modal: ← / → não têm alvo aqui e escapavam para a grade
                    // ao fundo — consome para o foco ficar na lista (navega só com ↑ / ↓).
                    e.type == KeyEventType.KeyDown &&
                        (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) -> true
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 680.dp)
                .padding(vertical = 24.dp)
                .trapFocus()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Escolher canal", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                text = "Selecione o canal usado para listar os vídeos na biblioteca.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xCCFFFFFF)
            )
            // Lista rolável (muitos canais não cabem na tela); foco inicial no canal ativo.
            val focusIndex = channels.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                channels.forEachIndexed { index, ch ->
                    val selected = ch.id == activeId
                    var focused by remember { mutableStateOf(false) }
                    val mod = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier
                    val accent = Color(0xFF2BEE34)
                    Row(
                        modifier = mod
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onSelect(ch.id) }
                            .background(
                                when {
                                    focused -> Color(0x33FFFFFF)
                                    selected -> Color(0x1F2BEE34)
                                    else -> Color(0x14FFFFFF)
                                }
                            )
                            .border(
                                width = if (focused || selected) 2.dp else 1.dp,
                                color = when {
                                    focused -> Color.White
                                    selected -> accent
                                    else -> Color(0x33FFFFFF)
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar real do canal quando disponível; fallback: inicial colorida por título.
                        if (ch.avatarPath != null) {
                            AsyncImage(
                                model = ch.avatarPath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorForTitle(ch.title))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorForTitle(ch.title)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initialFor(ch.title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Text(
                            text = ch.title,
                            color = if (selected) accent else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            var closeFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(10.dp))
                    .onFocusChanged { closeFocused = it.isFocused }
                    .clickable(onClick = onDismiss)
                    .background(if (closeFocused) Color(0x33FFFFFF) else Color(0x1FFFFFFF))
                    .border(
                        width = if (closeFocused) 2.dp else 1.dp,
                        color = if (closeFocused) Color.White else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Fechar", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

internal val CHANNEL_AVATAR_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFF42A5F5), Color(0xFFFFA726), Color(0xFF66BB6A), Color(0xFFEC407A)
)

internal fun colorForTitle(title: String): Color =
    CHANNEL_AVATAR_COLORS[kotlin.math.abs(title.hashCode()) % CHANNEL_AVATAR_COLORS.size]

internal fun initialFor(title: String): String =
    title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
