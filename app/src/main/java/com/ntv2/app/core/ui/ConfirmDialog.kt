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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val BRAND = Color(0xFF2BEE34)
private val DESTRUCTIVE = Color(0xFFFF6B6B)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
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
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB0B0B0),
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogButton(
                    label = confirmLabel,
                    icon = confirmIcon,
                    accent = accent,
                    onClick = onConfirm
                )
                DialogButton(
                    label = cancelLabel,
                    icon = cancelIcon,
                    accent = Color.White,
                    modifier = Modifier.focusRequester(cancelFocus),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(if (focused) accent else Color(0x14FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accent else Color(0x33FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = if (focused) Color(0xFF101010) else Color.White
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
