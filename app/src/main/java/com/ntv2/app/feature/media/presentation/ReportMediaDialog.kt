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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.feature.media.data.report.ReportOutcome
import kotlinx.coroutines.delay

/** Motivos de reporte de uma mídia. [code] é o valor estável enviado ao backend (`reason`). */
enum class MediaReportReason(val code: String, val label: String, val hint: String) {
    WrongVideo("wrong_video", "Vídeo errado", "Não é este filme ou está trocado"),
    NotDubbed("not_dubbed", "Não está dublado", "Só tem áudio original"),
    AudioProblem("audio_problem", "Problema no áudio", "Sem som, dessincronizado ou chiando"),
    SubtitleProblem("subtitle_problem", "Problema na legenda", "Ausente, errada ou fora de tempo"),
    BadQuality("bad_quality", "Qualidade ruim", "Imagem ruim ou gravação de cinema"),
    DoesNotPlay("does_not_play", "Não reproduz", "Não abre ou trava sempre"),
    Other("other", "Outro problema", "Algo diferente dos itens acima")
}

/** Envia o reporte (motivo + nota opcional) e avisa o resultado por [onResult]. */
typealias ReportSender = (reason: MediaReportReason, note: String?, onResult: (ReportOutcome) -> Unit) -> Unit

private const val NOTE_MAX_CHARS = 300

private sealed interface ReportStep {
    data object Choose : ReportStep
    data object Note : ReportStep
    data object Sending : ReportStep
    data class Done(val outcome: ReportOutcome) : ReportStep
}

/** Seleção do motivo → (Outro: texto opcional) → enviando → resultado → fecha sozinho. */
@Composable
internal fun ReportMediaDialog(
    title: String,
    onReport: ReportSender,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf<ReportStep>(ReportStep.Choose) }
    var note by remember { mutableStateOf("") }
    // TV: 2 colunas, para os 7 motivos caberem sem rolar (antes o último ficava cortado).
    val twoColumns = rememberAdaptiveLayoutInfo().useTvLayout
    val firstFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }

    fun send(reason: MediaReportReason, text: String?) {
        step = ReportStep.Sending
        onReport(reason, text?.trim()?.takeIf { it.isNotEmpty() }) { outcome -> step = ReportStep.Done(outcome) }
    }

    BackHandler(enabled = true) {
        // Voltar no texto do "Outro" retorna à lista; nos demais passos fecha.
        if (step == ReportStep.Note) step = ReportStep.Choose else onDismiss()
    }
    LaunchedEffect(step) {
        // O item focado some a cada troca de passo: devolve o foco ao conteúdo novo (controle remoto).
        when (val s = step) {
            ReportStep.Choose -> runCatching { firstFocus.requestFocus() }
            ReportStep.Note -> runCatching { noteFocus.requestFocus() }
            ReportStep.Sending -> Unit
            is ReportStep.Done -> {
                runCatching { doneFocus.requestFocus() }
                delay(if (s.outcome == ReportOutcome.Sent) 1_800L else 3_200L)
                onDismiss()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = step != ReportStep.Sending, onClick = onDismiss)
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
            when (val s = step) {
                is ReportStep.Done -> ReportResult(s.outcome, Modifier.focusRequester(doneFocus), onDismiss)
                ReportStep.Sending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = BRAND_ACCENT,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Enviando reporte…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                ReportStep.Choose, ReportStep.Note -> {
                    ReportHeader(title)
                    if (s == ReportStep.Note) {
                        ReportNoteStep(
                            note = note,
                            onNoteChange = { note = it.take(NOTE_MAX_CHARS) },
                            fieldFocus = noteFocus,
                            onSend = { send(MediaReportReason.Other, note) },
                            onBack = { step = ReportStep.Choose }
                        )
                    } else {
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
                                                if (reason == MediaReportReason.Other) step = ReportStep.Note
                                                else send(reason, null)
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
    }
}

@Composable
private fun ReportHeader(title: String) {
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
        color = BrandColors.TextMuted,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** "Outro problema": texto livre opcional (vai no campo `note`). */
@Composable
private fun ReportNoteStep(
    note: String,
    onNoteChange: (String) -> Unit,
    fieldFocus: FocusRequester,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    var fieldFocused by remember { mutableStateOf(false) }
    Text(
        "Conte o que aconteceu (opcional)",
        color = BrandColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium
    )
    BasicTextField(
        value = note,
        onValueChange = onNoteChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BrandColors.Fill)
            .border(
                if (fieldFocused) 3.dp else 1.dp,
                if (fieldFocused) BrandColors.Accent else BrandColors.BorderSubtle,
                RoundedCornerShape(6.dp)
            )
            .focusRequester(fieldFocus)
            .onFocusChanged { fieldFocused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        // Uma linha: no controle remoto ↓ sai do campo para os botões (multilinha prenderia o D-pad).
        singleLine = true,
        textStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = Color.White),
        cursorBrush = SolidColor(BrandColors.Accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (note.isEmpty()) {
                    Text("Ex.: o áudio some depois de 20 minutos", color = BrandColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                }
                inner()
            }
        }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportButton("Enviar", primary = true, onClick = onSend)
        ReportButton("Voltar", primary = false, onClick = onBack)
    }
}

@Composable
private fun ReportButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> BrandColors.Accent
                    primary -> BrandColors.SurfaceAlt
                    else -> BrandColors.Fill
                }
            )
            .border(1.dp, if (focused) BrandColors.Accent else BrandColors.BorderSubtle, RoundedCornerShape(6.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Resultado do envio; o botão "OK" recebe o foco para o controle remoto fechar na hora. */
@Composable
private fun ReportResult(outcome: ReportOutcome, buttonModifier: Modifier, onDismiss: () -> Unit) {
    val (icon, tint) = when (outcome) {
        ReportOutcome.Sent -> Icons.Filled.CheckCircle to BRAND_ACCENT
        ReportOutcome.Duplicate -> Icons.Filled.Info to BrandColors.Accent
        ReportOutcome.RateLimited, ReportOutcome.Failed -> Icons.Filled.ErrorOutline to BrandColors.Warning
    }
    val (headline, detail) = when (outcome) {
        ReportOutcome.Sent -> "Reporte enviado, obrigado" to
            "Ele vai nos ajudar a corrigir este vídeo."
        ReportOutcome.Duplicate -> "Você já reportou isso" to
            "Já recebemos este reporte nas últimas 24 horas."
        ReportOutcome.RateLimited -> "Não foi possível enviar" to
            "Você atingiu o limite de reportes de hoje. Tente amanhã."
        ReportOutcome.Failed -> "Não foi possível enviar" to
            "Sem conexão agora. Vamos tentar de novo na próxima vez que você abrir o app."
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(headline, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = BrandColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        ReportButton("OK", primary = true, modifier = buttonModifier, onClick = onDismiss)
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
