@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.feature.playback.presentation

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.multipart.PartStatus
import com.ntv2.app.core.multipart.PartsSnapshot
import com.ntv2.app.core.player.MediaTracksInfo
import com.ntv2.app.core.player.PlaybackState

/**
 * Painel "Estado da rede" (aberto pelo ícone da barra de controles). Como o app faz streaming via
 * Telegram (TDLib), e não P2P, no lugar de "Pares" mostra o estado real da conexão e o buffer à
 * frente. Todos os dados já existem no snapshot do player + o sinal de conexão do TDLib.
 */
@Composable
internal fun NetworkStatusOverlay(
    speedBytesPerSec: Long,
    downloadedBytes: Long,
    expectedBytes: Long?,
    positionMs: Long,
    bufferedMs: Long,
    playbackState: PlaybackState,
    connectionReady: Boolean,
    fileName: String?,
    fileId: Int,
    onDismiss: () -> Unit,
    /** Filme dividido em partes: mostra a parte atual, o que há à frente e o mapa das partes. */
    parts: PartsSnapshot? = null,
    /** Formato real do vídeo e do áudio em uso (lido pelo ExoPlayer). */
    tracks: MediaTracksInfo = MediaTracksInfo()
) {
    BackHandler(enabled = true) { onDismiss() }

    val connectionColor = if (connectionReady) com.ntv2.app.core.ui.BrandColors.Accent else Color(0xFFFFB020)
    val connectionLabel = if (connectionReady) "Conectado" else "Reconectando…"
    val stateLabel = when (playbackState) {
        PlaybackState.Ready -> "Reproduzindo"
        PlaybackState.Buffering, PlaybackState.Preparing -> "Armazenando buffer"
        PlaybackState.Paused -> "Pausado"
        else -> "—"
    }
    val bufferAheadMs = (bufferedMs - positionMs).coerceAtLeast(0L)
    val percent = expectedBytes?.takeIf { it > 0L }?.let { (downloadedBytes.toDouble() / it * 100.0) }
    val loadedText = buildString {
        append(formatBytes(downloadedBytes))
        if (expectedBytes != null && expectedBytes > 0L) {
            append(" / ")
            append(formatBytes(expectedBytes))
            if (percent != null) append(java.util.Locale("pt", "BR").let { String.format(it, "  (%.2f%%)", percent) })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xF20E1526))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                .clickable(onClick = {})
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Cabeçalho: ícone pequeno + título, inline (sem o círculo grande).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.Public,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Estado da rede",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            InfoLine("Velocidade", formatSpeed(speedBytesPerSec))
            if (parts != null) {
                InfoLine("Partes", "${parts.currentPartNumber} de ${parts.partCount}")
                InfoLine("Parte atual", "${formatBytes(parts.currentPartDownloaded)} / ${formatBytes(parts.currentPartSize)}")
                InfoLine("À frente", if (parts.aheadBytes > 0L) formatBytes(parts.aheadBytes) else "—")
                InfoLine("Filme todo", "${formatBytes(parts.downloadedBytes)} / ${formatBytes(parts.totalBytes)}")
                PartsMap(parts)
            } else {
                InfoLine("Carregado", loadedText)
            }
            // Conexão numa linha só: bolinha + estado · reprodução · buffer.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(connectionColor))
                Text(
                    "  $connectionLabel · $stateLabel · buffer ${formatTime(bufferAheadMs)}",
                    color = Color(0xFFB8C0CC),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            VideoInfoTexts.videoLine(tracks)?.let { InfoBlock("Vídeo", it) }
            VideoInfoTexts.audioLine(tracks)?.let { InfoBlock("Áudio", it) }

            Text(
                fileName ?: "arquivo #$fileId",
                color = Color(0x66FFFFFF),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Rótulo pequeno e o valor embaixo, em até duas linhas (formatos longos não cabem ao lado do rótulo). */
@Composable
private fun InfoBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Color(0x99FFFFFF), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF8A94A6), style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** "624 KB/s", "1,4 MB/s". Reusa o formatador de bytes do player. */
internal fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"


/**
 * Mapa das partes do filme: uma célula por parte (agrupadas se forem muitas), com a atual em destaque.
 * Legenda curta embaixo para ninguém precisar adivinhar as cores.
 */
@Composable
private fun PartsMap(parts: PartsSnapshot) {
    val accent = com.ntv2.app.core.ui.BrandColors.Accent
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.groupedCells().forEach { cell ->
                val color = when (cell.status) {
                    PartStatus.WATCHED -> accent.copy(alpha = 0.35f)
                    PartStatus.CURRENT -> Color.White
                    PartStatus.DOWNLOADED -> accent
                    PartStatus.PARTIAL -> accent.copy(alpha = 0.25f + 0.5f * cell.fraction)
                    PartStatus.PENDING -> Color(0x22FFFFFF)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(width = 1.dp, height = 9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        Text(
            "branca = lendo · verde = baixada · apagada = já assistida",
            color = Color(0x80FFFFFF),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
