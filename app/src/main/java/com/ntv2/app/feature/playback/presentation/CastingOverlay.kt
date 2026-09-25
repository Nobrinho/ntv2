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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
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
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            } else {
                Icon(Icons.Filled.CastConnected, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Text(
                if (connecting) "Conectando a $deviceName…" else "Transmitindo para $deviceName",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Mantenha o app aberto e o celular no mesmo Wi‑Fi durante o filme.",
                color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(20.dp))
                    .clickable(onClick = onStop)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Parar transmissão", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
