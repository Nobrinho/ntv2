package com.ntv2.app.feature.playback.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Quanto tempo o indicador do toque duplo fica na tela após o último toque. */
internal const val TAP_SEEK_FEEDBACK_MS = 800L

internal data class TapSeekFeedback(val forward: Boolean, val totalMs: Long, val nonce: Int)

/**
 * Indicador do toque duplo, estilo YouTube: meia-lua clara na lateral tocada com a seta e o total
 * acumulado ("20 s"), para ficar claro se voltou ou avançou.
 */
@Composable
internal fun TapSeekIndicator(feedback: TapSeekFeedback, modifier: Modifier = Modifier) {
    val shape = if (feedback.forward) {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    } else {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    }
    Box(modifier = modifier, contentAlignment = if (feedback.forward) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.38f)
                .fillMaxHeight()
                .clip(shape)
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (feedback.forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    contentDescription = if (feedback.forward) "Avançou" else "Voltou",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    "${feedback.totalMs / 1000} s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
