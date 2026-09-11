package com.ntv2.app.feature.splash.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ntv2.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intro de marca (cold start) estilo Netflix: fundo escuro, glow verde ambiente e o logo
 * Nbr PLAY em VERDE surgindo com um leve push-in (scale 1.08 → 1.0) + fade, depois chama
 * [onFinished]. Pulável (qualquer tecla/clique). Usa o logo vetorial (VectorDrawable) →
 * nítido em qualquer tela, offline, sem dependências.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val iconScale = remember { Animatable(1.08f) }
    val iconAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    var done by remember { mutableStateOf(false) }

    fun finish() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        launch { glowAlpha.animateTo(1f, tween(800)) }
        launch { iconAlpha.animateTo(1f, tween(500)) }
        // Push-in premium (sem bounce), estilo abertura de streaming.
        iconScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(650)
        finish()
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    finish(); true
                } else false
            }
            .clickable { finish() },
        contentAlignment = Alignment.Center
    ) {
        // Glow verde ambiente atrás do logo.
        Box(
            modifier = Modifier
                .size(680.dp)
                .graphicsLayer { alpha = glowAlpha.value }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x552BEE34), Color(0x00000000))
                    )
                )
        )
        // Logo verde sobre o fundo escuro (estilo Netflix), com push-in + fade.
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFF2BEE34)),
            modifier = Modifier
                .size(340.dp)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    alpha = iconAlpha.value
                }
        )
    }
}
