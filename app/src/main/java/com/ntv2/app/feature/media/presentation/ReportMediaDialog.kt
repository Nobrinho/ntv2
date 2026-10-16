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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
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
    // TV: 2 colunas, para os 7 motivos caberem sem rolar (antes o último ficava cortado).
    val twoColumns = rememberAdaptiveLayoutInfo().useTvLayout
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
                .widthIn(max = if (twoColumns) 820.dp else 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandColors.Surface)
                .border(1.dp, BrandColors.Border, RoundedCornerShape(8.dp))
                // Absorve o toque: clicar dentro do cartão não fecha.
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sent) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BRAND_ACCENT, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Obrigado!", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Seu reporte foi registrado e vai nos ajudar a corrigir este vídeo.",
                            color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(BrandColors.Warning.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = BrandColors.Warning, modifier = Modifier.size(22.dp))
                    }
                    Text("Reportar problema", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    title,
                    color = com.ntv2.app.core.ui.BrandColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val rows = MediaReportReason.entries.chunked(if (twoColumns) 2 else 1)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { col, reason ->
                                ReportReasonRow(
                                    reason = reason,
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (rowIndex == 0 && col == 0) Modifier.focusRequester(firstFocus) else Modifier),
                                    onClick = {
                                        onReport(reason)
                                        sent = true
                                    }
                                )
                            }
                            // Linha ímpar no fim (2 colunas): ocupa o espaço para o cartão não esticar.
                            if (row.size < (if (twoColumns) 2 else 1)) Box(Modifier.weight(1f))
                        }
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
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) BrandColors.SurfaceAlt else BrandColors.Fill)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) BrandColors.Accent else BrandColors.BorderSubtle,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(BrandColors.SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(reason.icon(), contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(reason.label, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(reason.hint, color = BrandColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = BrandColors.TextMuted, modifier = Modifier.size(20.dp))
    }
}

private fun MediaReportReason.icon(): ImageVector = when (this) {
    MediaReportReason.WrongVideo -> Icons.Filled.SwapHoriz
    MediaReportReason.NotDubbed -> Icons.Filled.Mic
    MediaReportReason.AudioProblem -> Icons.Filled.VolumeOff
    MediaReportReason.SubtitleProblem -> Icons.Filled.ClosedCaption
    MediaReportReason.BadQuality -> Icons.Filled.HighQuality
    MediaReportReason.DoesNotPlay -> Icons.Filled.ErrorOutline
    MediaReportReason.Other -> Icons.Filled.MoreHoriz
}
