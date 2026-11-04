package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons

/**
 * Topo da Biblioteca do protótipo (10 Biblioteca): título "Biblioteca" e, à direita, a pílula do canal
 * atual com seta. No celular a pílula abre o seletor de canal; na TV é só indicativa (o Canais fica no rail).
 */
@Composable
internal fun LibraryHeader(
    channelName: String,
    useTvLayout: Boolean,
    onChannelClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Biblioteca",
            style = if (useTvLayout) NbrType.PageTitle else NbrType.ScreenTitle,
            color = TEXT_PRIMARY,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (channelName.isNotBlank()) {
            ChannelPill(channelName, onClick = onChannelClick)
        }
    }
}

/** Pílula "Canal atual": 36 dp, fundo translúcido, borda sutil, nome 13 semibold + seta. */
@Composable
private fun ChannelPill(name: String, onClick: (() -> Unit)?) {
    val shape = BrandShapes.Pill
    Row(
        modifier = Modifier
            .widthIn(max = 180.dp)
            .height(36.dp)
            .clip(shape)
            .background(BrandColors.Fill)
            .border(1.dp, BrandColors.BorderSubtle, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = NbrType.Chip,
            color = TEXT_PRIMARY,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(NbrIcons.ChevronDown, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(14.dp))
    }
}

/**
 * Estado "11 Biblioteca sem canal" do protótipo: ícone em círculo, título, texto e botão "Escolher canal"
 * (leva às Configurações, onde se escolhe o canal). O "Tentar novamente" que o app já tinha continua,
 * como botão secundário.
 */
@Composable
internal fun NoChannelPanel(
    onChooseChannel: () -> Unit,
    onRetry: () -> Unit,
    primaryFocus: FocusRequester,
    onLeft: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (onLeft != null && e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                    onLeft(); true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(280.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BrandColors.SurfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(NbrIcons.Library, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(40.dp))
            }
            Text(
                "Nenhum canal selecionado",
                style = NbrType.SheetTitle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = TEXT_PRIMARY,
                textAlign = TextAlign.Center
            )
            Text(
                "Escolha ao menos um canal em Configurações.",
                style = NbrType.Body.copy(lineHeight = 17.sp),
                color = BrandColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            BrandButton(
                onClick = onChooseChannel,
                modifier = Modifier.fillMaxWidth().focusRequester(primaryFocus)
            ) { BrandButtonLabel("Escolher canal") }
            BrandButton(
                onClick = onRetry,
                style = BrandButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth()
            ) { BrandButtonLabel("Tentar novamente") }
        }
    }
}
