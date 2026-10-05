package com.ntv2.app.feature.playback.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Texto do aviso quando o vídeo não pode ser decodificado e só o áudio toca. */
internal fun unsupportedVideoMessage(formatLabel: String?): String {
    val formato = formatLabel?.let { " ($it)" }.orEmpty()
    return "Este aparelho não consegue exibir a imagem deste vídeo$formato. Só o áudio será reproduzido."
}

/** Aviso fixo (sem imagem, só áudio) no alto da tela do player. */
@Composable
internal fun BoxScope.UnsupportedVideoBanner(formatLabel: String?) {
    Text(
        text = unsupportedVideoMessage(formatLabel),
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 32.dp)
            .background(Color(0xE6000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    )
}
