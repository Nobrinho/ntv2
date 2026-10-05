package com.ntv2.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ntv2.app.R

/** Fundos sem logo do brand kit: "glow" para login/onboarding, "minimal" para o resto das telas. */
enum class BrandBackdropKind { Glow, Minimal }

/** Preenche a área do pai (cover), com a versão retrato ou paisagem conforme a proporção. */
@Composable
fun BoxWithConstraintsScope.BrandBackdrop(kind: BrandBackdropKind) {
    val portrait = maxHeight > maxWidth
    val res = when (kind) {
        BrandBackdropKind.Glow -> if (portrait) R.drawable.bg_glow_port else R.drawable.bg_glow_land
        BrandBackdropKind.Minimal -> if (portrait) R.drawable.bg_minimal_port else R.drawable.bg_minimal_land
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
