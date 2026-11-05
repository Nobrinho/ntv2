package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ntv2.app.core.ui.trapFocus
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.presentation.state.ChannelChipUi
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.channels.presentation.ChannelRowCard
import com.ntv2.app.feature.channels.presentation.ChannelText

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
    // Voltar por gesto/botão do celular: fecha o seletor. Sem isto o Voltar ia para a raiz e abria
    // "Deseja fechar o aplicativo?" por cima do seletor (a tecla Back da TV já era tratada abaixo).
    BackHandler(enabled = true) { onDismiss() }
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
        // Painel no estilo da Seleção de canais: surface, raio 24, borda sutil, padding 24.
        val tv = rememberAdaptiveLayoutInfo().useTvLayout
        val panelShape = BrandShapes.Xl
        Column(
            modifier = Modifier
                // widthIn antes do fillMaxWidth: na ordem inversa o limite era ignorado na TV.
                .widthIn(max = if (tv) 480.dp else 680.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .trapFocus()
                .clip(panelShape)
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.BorderSubtle, panelShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Escolher canal", style = NbrType.SheetTitle, color = ChannelText)
            Text(
                text = "Selecione o canal usado para listar os vídeos na biblioteca.",
                style = NbrType.Body,
                color = BrandColors.TextSecondary
            )
            // Lista rolável (muitos canais não cabem na tela); foco inicial no canal ativo.
            val focusIndex = channels.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
            FocusGlideScope(Modifier.weight(1f, fill = false)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .focusGroup()
                        // Folga para o anel de foco não ser cortado nas bordas da lista.
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    channels.forEachIndexed { index, ch ->
                        val selected = ch.id == activeId
                        ChannelRowCard(
                            title = ch.title,
                            avatarPath = ch.avatarPath,
                            selected = selected,
                            onClick = { onSelect(ch.id) },
                            modifier = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier
                        ) {
                            if (selected) {
                                Icon(NbrIcons.CircleCheck, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            BrandButton(
                onClick = onDismiss,
                style = BrandButtonStyle.Secondary,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(NbrIcons.X, contentDescription = null, tint = ChannelText, modifier = Modifier.size(18.dp))
                BrandButtonLabel("Fechar")
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
