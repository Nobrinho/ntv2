package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.delay

/** Motivos de reporte de uma mídia (o envio ainda será definido; por ora só registra). */
enum class MediaReportReason(val label: String, val hint: String) {
    WrongVideo("Vídeo errado", "Não é este filme ou está trocado"),
    NotDubbed("Não está dublado", "Só tem áudio original"),
    AudioProblem("Problema no áudio", "Sem som, dessincronizado ou chiando"),
    SubtitleProblem("Problema na legenda", "Ausente, errada ou fora de tempo"),
    BadQuality("Qualidade ruim", "Imagem ruim ou gravação de cinema"),
    DoesNotPlay("Não reproduz", "Não abre ou trava sempre"),
    Other("Outro problema", "Algo diferente dos itens acima")
}

/** Seleção do motivo → confirmação "Obrigado" → fecha sozinho. */
@Composable
internal fun ReportMediaDialog(
    title: String,
    onReport: (MediaReportReason) -> Unit,
    onDismiss: () -> Unit
) {
    var sent by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    BackHandler(enabled = true) { onDismiss() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(sent) {
        if (sent) {
            delay(1_800L)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss)
            .trapFocus(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                // Absorve o toque: clicar dentro do cartão não fecha.
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sent) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BRAND_GREEN, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Obrigado!", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Seu reporte foi registrado e vai nos ajudar a corrigir este vídeo.",
                            color = Color(0xFFBDBDBD),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Text("Reportar problema", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    title,
                    color = Color(0xFF9A9A9A),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaReportReason.entries.forEachIndexed { index, reason ->
                        ReportReasonRow(
                            reason = reason,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = {
                                onReport(reason)
                                sent = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportReasonRow(reason: MediaReportReason, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(reason.label, color = if (focused) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                reason.hint,
                color = if (focused) Color(0xFF404040) else Color(0xFF9A9A9A),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (focused) Color.Black else Color(0x80FFFFFF),
            modifier = Modifier.size(20.dp)
        )
    }
}
