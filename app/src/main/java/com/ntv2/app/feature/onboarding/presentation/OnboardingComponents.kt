package com.ntv2.app.feature.onboarding.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ntv2.app.R
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.LocalAnimationsEnabled

/** Cor `text` do protótipo (títulos da introdução). */
internal val OnboardingTextColor = Color(0xFFF5F5F7)

/** Logo do NBR Play no topo da introdução (celular 56×44, TV 60×48). */
@Composable
internal fun OnboardingLogo(width: Dp, height: Dp) {
    Image(
        painter = painterResource(R.drawable.ic_brand_logo),
        contentDescription = "NBR Play",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(width, height)
    )
}

/**
 * Ilustração do protótipo: órbita (gradiente radial accent@20% → transparente) com o núcleo
 * surface-alt de contorno accent no centro. [icon] null = logo da marca no núcleo (boas-vindas).
 */
@Composable
internal fun OnboardingOrbit(
    icon: ImageVector?,
    orbit: Dp,
    core: Dp,
    iconSize: Dp,
    stroke: Dp = 2.dp
) {
    Box(
        modifier = Modifier
            .size(orbit)
            .background(
                Brush.radialGradient(listOf(BrandColors.Accent.copy(alpha = 0.2f), BrandColors.Accent.copy(alpha = 0f))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(core)
                .background(BrandColors.SurfaceAlt, CircleShape)
                .border(stroke, BrandColors.Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = BrandColors.Accent, modifier = Modifier.size(iconSize))
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_brand_logo),
                    contentDescription = "NBR Play",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(core * 0.62f)
                )
            }
        }
    }
}

/** Pontos de página: o ativo estica (celular 8→24, TV 7→22) em accent; os demais ficam em `border`. */
@Composable
internal fun OnboardingDots(
    count: Int,
    current: Int,
    dot: Dp,
    activeWidth: Dp,
    gap: Dp,
    onDot: (Int) -> Unit
) {
    val animations = LocalAnimationsEnabled.current
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(
                if (selected) activeWidth else dot,
                animationSpec = tween(if (animations) 280 else 0),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                if (selected) BrandColors.Accent else BrandColors.Border,
                animationSpec = tween(if (animations) 280 else 0),
                label = "dotColor"
            )
            val shape = RoundedCornerShape(dot / 2)
            Box(
                Modifier
                    .height(dot)
                    .width(width)
                    .clip(shape)
                    .background(color, shape)
                    .clickable(onClick = { onDot(i) })
            )
        }
    }
}
