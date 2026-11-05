package com.ntv2.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val BRAND = com.ntv2.app.core.ui.BrandColors.Accent
private val DESTRUCTIVE = com.ntv2.app.core.ui.BrandColors.Error
private val TEXT = com.ntv2.app.core.ui.BrandColors.TextPrimary // token "text" do protótipo

/**
 * Diálogo de confirmação padronizado do app.
 *
 * - Escurece a tela inteira e prende o foco (Voltar/Esc cancelam; ← / → são consumidos
 *   para o foco não escapar para o conteúdo ao fundo).
 * - Ícone em círculo no topo, título e mensagem centralizados.
 * - Dois botões **empilhados** (confirmar em cima, cancelar embaixo com foco inicial),
 *   largura total, com ícone e forte feedback visual de foco.
 * - [destructive] pinta o acento em vermelho (ex.: sair da conta / fechar app).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    icon: ImageVector,
    confirmLabel: String,
    confirmIcon: ImageVector,
    cancelLabel: String,
    cancelIcon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    val accent = if (destructive) DESTRUCTIVE else BRAND
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    val tv = rememberAdaptiveLayoutInfo().useTvLayout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC7000000))
            .onPreviewKeyEvent { e ->
                when {
                    e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape) -> {
                        onDismiss(); true
                    }
                    // Prende o foco: só navega com ↑ / ↓ entre os botões empilhados.
                    e.type == KeyEventType.KeyDown &&
                        (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) -> true
                    else -> false
                }
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Protótipo (36 Sair da conta / TV 16): cartão surface, raio 24 (18 na TV), padding 24 (28 na TV),
        // ícone em círculo de 56, título 20 bold, mensagem 14 com 1,45 de entrelinha, botões em pílula.
        val shape = RoundedCornerShape(if (tv) 18.dp else 24.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.BorderSubtle, shape)
                .padding(if (tv) 28.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Text(
                title,
                style = NbrType.SheetTitle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                color = TEXT,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Text(
                    message,
                    style = NbrType.Body.copy(lineHeight = 20.sp),
                    color = BrandColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .trapFocus(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogButton(
                    label = confirmLabel,
                    icon = confirmIcon,
                    primary = true,
                    accent = accent,
                    destructive = destructive,
                    tv = tv,
                    onClick = onConfirm
                )
                DialogButton(
                    label = cancelLabel,
                    icon = cancelIcon,
                    primary = false,
                    accent = accent,
                    destructive = false,
                    tv = tv,
                    modifier = Modifier.focusRequester(cancelFocus),
                    onClick = onDismiss
                )
            }
        }
    }
}

/** Botão do diálogo como no protótipo: pílula de 52 dp (44 na TV); confirmar cheio, cancelar neutro. */
@Composable
private fun DialogButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    accent: Color,
    destructive: Boolean,
    tv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = BrandShapes.Pill
    val fill = if (primary) accent else BrandColors.Fill
    val content = when {
        primary && destructive -> Color.White
        primary -> BrandColors.OnCta
        else -> TEXT
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.scale(1.03f) else Modifier)
            .clip(shape)
            .clickable { onClick() }
            .background(fill)
            .border(
                width = if (focused) 2.dp else if (primary) 0.dp else 1.dp,
                color = if (focused) (if (primary) Color.White else BrandColors.Accent) else BrandColors.BorderSubtle,
                shape = shape
            )
            .heightIn(min = if (tv) 44.dp else 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // O protótipo não tem ícone nos botões; na TV ele ajuda a ler à distância.
        if (tv) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, color = content, style = NbrType.Button)
    }
}
