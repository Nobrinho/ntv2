package com.ntv2.app.feature.playback.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.icons.NbrIcons

/**
 * Ocupa a área do vídeo enquanto transmite: capa desfocada + "Transmitindo para <aparelho>" e o
 * botão para parar. Os controles do player (play/pausa/linha do tempo) continuam por cima e agem
 * no Chromecast.
 */
@Composable
internal fun CastingOverlay(
    deviceName: String,
    connecting: Boolean,
    posterPath: String?,
    onStop: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(onClick = onReveal),
        contentAlignment = Alignment.Center
    ) {
        if (!posterPath.isNullOrBlank()) {
            AsyncImage(
                model = posterPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp)
            )
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            if (connecting) {
                PlayerSpinner(size = 40.dp, stroke = 3.dp)
            } else {
                Icon(NbrIcons.Cast, contentDescription = null, tint = com.ntv2.app.core.ui.BrandColors.Accent, modifier = Modifier.size(40.dp))
            }
            Text(
                if (connecting) "Conectando a $deviceName…" else "Transmitindo para $deviceName",
                color = com.ntv2.app.core.ui.BrandColors.TextPrimary,
                style = com.ntv2.app.core.ui.NbrType.Section,
                textAlign = TextAlign.Center
            )
            Text(
                "Mantenha o app aberto e o celular no mesmo Wi‑Fi durante o filme.",
                color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                style = com.ntv2.app.core.ui.NbrType.Secondary,
                textAlign = TextAlign.Center
            )
            PlayerSecondaryButton(
                label = "Parar transmissão",
                onClick = onStop,
                icon = NbrIcons.Cast,
                height = 44.dp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
