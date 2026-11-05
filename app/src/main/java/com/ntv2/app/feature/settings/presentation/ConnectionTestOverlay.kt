package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.network.CheckResult
import com.ntv2.app.core.network.ConnectionReport
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import kotlinx.coroutines.flow.Flow

/**
 * Teste de conexão: roda ao abrir; cada etapa mostra o resultado assim que termina.
 * Celular = subtela (protótipo 37); TV = painel central (TV 17).
 */
@Composable
internal fun ConnectionTestOverlay(
    runTest: () -> Flow<ConnectionReport>,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    val tv = rememberAdaptiveLayoutInfo().useTvLayout
    var run by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf(ConnectionReport()) }
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(run) {
        report = ConnectionReport()
        runTest().collect { report = it }
    }
    LaunchedEffect(Unit) { runCatching { actionFocus.requestFocus() } }

    val checks: @Composable () -> Unit = {
        CheckCard(tv, NbrIcons.Wifi, "Rede", report.network)
        CheckCard(tv, NbrIcons.Globe, "Internet", report.internet)
        CheckCard(tv, NbrIcons.Send, "Telegram", report.telegram)
        CheckCard(tv, NbrIcons.Gauge, "Velocidade", report.speed)
        report.verdict?.let { verdict ->
            val color = if (report.verdictGood) BrandColors.Success else BrandColors.Warning
            SettingsNotice(if (report.verdictGood) NbrIcons.CircleCheck else NbrIcons.TriangleAlert, color, verdict, tv = tv)
        }
    }
    val retry: @Composable (Modifier) -> Unit = { m ->
        TestButton(
            icon = NbrIcons.RefreshCw,
            label = if (report.running) "Testando…" else "Testar de novo",
            enabled = !report.running,
            primary = true,
            modifier = m.focusRequester(actionFocus),
            onClick = { run++ }
        )
    }

    if (tv) {
        SettingsTvDialog {
            Text("Teste de conexão", style = NbrType.PageTitle, fontWeight = FontWeight.ExtraBold, color = SettingsText)
            checks()
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                retry(Modifier.width(180.dp))
                TestButton(icon = NbrIcons.X, label = "Fechar", modifier = Modifier.width(120.dp), onClick = onClose)
            }
        }
    } else {
        SettingsPhonePage(
            title = "Teste de conexão",
            onBack = onClose,
            actions = {
                retry(Modifier.fillMaxWidth())
                TestButton(icon = NbrIcons.X, label = "Fechar", modifier = Modifier.fillMaxWidth(), onClick = onClose)
            }
        ) {
            Text(
                "Medimos a rede deste aparelho, o acesso ao Telegram e a velocidade de download.",
                style = NbrType.Body, lineHeight = 20.sp, color = BrandColors.TextSecondary
            )
            checks()
        }
    }
}

/** Cartão de uma etapa: celular surface raio 16, padding 14; TV surface-alt raio 10, padding 10/12. */
@Composable
private fun CheckCard(tv: Boolean, icon: ImageVector, label: String, result: CheckResult) {
    val running = result == CheckResult.Running
    val (status, color) = when (result) {
        CheckResult.Pending -> "Aguardando" to BrandColors.TextMuted
        CheckResult.Running -> "Testando…" to BrandColors.Accent
        is CheckResult.Ok -> result.value to if (result.good) BrandColors.Success else BrandColors.Warning
        is CheckResult.Failed -> result.reason to BrandColors.Error
    }
    SettingsCard(
        radius = if (tv) 10.dp else 16.dp,
        background = if (tv) BrandColors.SurfaceAlt else BrandColors.Surface,
        borderColor = if (running) BrandColors.Accent else BrandColors.BorderSubtle,
        padding = if (tv) 10.dp else 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (tv) Modifier.padding(horizontal = 2.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSize = if (tv) 18.dp else 20.dp
            Icon(icon, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(iconSize))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = SettingsText, style = NbrType.ItemTitle, fontSize = if (tv) 14.sp else 15.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text(status, color = color, style = NbrType.Caption, fontSize = if (tv) 11.sp else 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when (result) {
                CheckResult.Pending -> Unit
                CheckResult.Running -> CircularProgressIndicator(
                    color = BrandColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(iconSize - 2.dp)
                )
                is CheckResult.Ok -> Icon(
                    if (result.good) NbrIcons.CircleCheck else NbrIcons.TriangleAlert, contentDescription = null,
                    tint = color, modifier = Modifier.size(iconSize)
                )
                is CheckResult.Failed -> Icon(NbrIcons.CircleX, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/**
 * Botão das telas de Configurações: pílula do protótipo ([BrandButton]), só texto.
 * Continua focável quando "desligado" (o foco não pode sumir no D-pad); só ignora o clique e fica esmaecido.
 * [icon] fica por compatibilidade: o protótipo não usa ícone nesses botões.
 */
@Composable
internal fun TestButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    style: BrandButtonStyle = if (primary) BrandButtonStyle.Primary else BrandButtonStyle.Secondary,
    onClick: () -> Unit
) {
    BrandButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        style = style
    ) {
        BrandButtonLabel(label)
    }
}

