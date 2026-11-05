package com.ntv2.app.feature.playback.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.icons.NbrIcons

/** Texto do aviso quando o vídeo não pode ser decodificado e só o áudio toca. */
internal fun unsupportedVideoMessage(formatLabel: String?): String {
    val formato = formatLabel?.let { " ($it)" }.orEmpty()
    return "Este aparelho não consegue exibir a imagem deste vídeo$formato. Só o áudio será reproduzido."
}

/**
 * Aviso fixo (sem imagem, só áudio) no centro da tela do player, no estilo dos avisos do protótipo
 * (tela 24, "Aviso de rede"): cartão de até 400, surface@95%, contorno warning, raio 20, padding 20,
 * espaço 12, ícone 32 warning e texto 13. Não tem botão: o áudio segue tocando.
 */
@Composable
internal fun BoxScope.UnsupportedVideoBanner(formatLabel: String?) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 16.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(PlayerPanelBg)
            .border(1.dp, BrandColors.Warning, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(NbrIcons.MonitorX, contentDescription = null, tint = BrandColors.Warning, modifier = Modifier.size(32.dp))
        Text(
            text = unsupportedVideoMessage(formatLabel),
            color = com.ntv2.app.core.ui.BrandColors.TextPrimary,
            style = NbrType.Secondary,
            textAlign = TextAlign.Center
        )
    }
}
