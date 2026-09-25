package com.ntv2.app.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.network.CheckResult
import com.ntv2.app.core.network.ConnectionReport
import com.ntv2.app.core.ui.trapFocus
import kotlinx.coroutines.flow.Flow

private val OK_GREEN = Color(0xFF2BEE34)
private val WARN_YELLOW = Color(0xFFFFC857)
private val FAIL_RED = Color(0xFFFF6B6B)

/** Teste de conexão: roda ao abrir; cada etapa mostra o resultado assim que termina. */
@Composable
internal fun ConnectionTestOverlay(
    runTest: () -> Flow<ConnectionReport>,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    var run by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf(ConnectionReport()) }
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(run) {
        report = ConnectionReport()
        runTest().collect { report = it }
    }
    LaunchedEffect(Unit) { runCatching { actionFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(enabled = !report.running, onClick = onClose)
            .trapFocus(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Teste de conexão", color = Color.White, style = MaterialTheme.typography.titleLarge)
            CheckRow(Icons.Filled.Wifi, "Rede", report.network)
            CheckRow(Icons.Filled.Cloud, "Internet", report.internet)
            CheckRow(Icons.Filled.Send, "Telegram", report.telegram)
            CheckRow(Icons.Filled.Speed, "Velocidade", report.speed)

            report.verdict?.let { verdict ->
                val color = if (report.verdictGood) OK_GREEN else WARN_YELLOW
                Text(
                    verdict,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // Celular estreito: botões empilhados (lado a lado o "Testar de novo" era cortado).
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val retry: @Composable (Modifier) -> Unit = { m ->
                    TestButton(
                        icon = Icons.Filled.Refresh,
                        label = if (report.running) "Testando…" else "Testar de novo",
                        enabled = !report.running,
                        modifier = m.focusRequester(actionFocus),
                        onClick = { run++ }
                    )
                }
                if (maxWidth < 400.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        retry(Modifier.fillMaxWidth())
                        TestButton(icon = Icons.Filled.Close, label = "Fechar", modifier = Modifier.fillMaxWidth(), onClick = onClose)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        retry(Modifier.weight(1f))
                        TestButton(icon = Icons.Filled.Close, label = "Fechar", modifier = Modifier.weight(1f), onClick = onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(icon: ImageVector, label: String, result: CheckResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x0FFFFFFF))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
        when (result) {
            CheckResult.Pending -> Text("—", color = Color(0xFF6A6A6A), style = MaterialTheme.typography.titleSmall)
            CheckResult.Running -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            is CheckResult.Ok -> StatusValue(
                result.value,
                if (result.good) OK_GREEN else WARN_YELLOW,
                if (result.good) Icons.Filled.CheckCircle else Icons.Filled.Warning
            )
            is CheckResult.Failed -> StatusValue(result.reason, FAIL_RED, Icons.Filled.Cancel)
        }
    }
}

@Composable
private fun StatusValue(text: String, color: Color, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = color, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TestButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            // Continua focável quando "desligado" (o foco não pode sumir no dpad); só ignora o clique.
            .clickable { if (enabled) onClick() }
            .background(if (focused) Color.White else Color(0x22FFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = when {
            focused -> Color.Black
            enabled -> Color.White
            else -> Color(0x66FFFFFF)
        }
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, color = content, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
    }
}
