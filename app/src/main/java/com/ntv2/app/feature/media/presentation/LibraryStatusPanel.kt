package com.ntv2.app.feature.media.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ntv2.app.R
import com.ntv2.app.core.ui.BrandColors

/**
 * Estado sem grade (vazio ou erro): marca do kit + título + motivos + ações, centralizados. Substitui o
 * texto solto sobre o fundo; o foco já nasce na ação principal para o D-pad não ficar sem alvo.
 */
@Composable
internal fun LibraryStatusPanel(
    title: String,
    lines: List<String>,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primaryFocus.requestFocus() } }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = "NBR Play",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            lines.forEach {
                Text(it, color = BrandColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPrimary,
                modifier = Modifier.focusRequester(primaryFocus),
                colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Cta, contentColor = BrandColors.OnCta)
            ) { Text(primaryLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                Button(
                    onClick = onSecondary,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.SurfaceAlt, contentColor = Color.White)
                ) { Text(secondaryLabel) }
            }
        }
    }
}
