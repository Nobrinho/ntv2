package com.ntv2.app.feature.splash.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ntv2.app.R
import com.ntv2.app.core.ui.BrandColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

// Linha do tempo: a marca entra (fade + leve zoom), assenta, e o overlay some nos 0.26s finais,
// revelando a Biblioteca já pronta por trás. O "hold" dá tempo do app carregar.
private const val ENTER = 0.9f
private const val TOTAL = 2.6f
private const val FADE_OUT = 0.26f

private fun seg(t: Float, s: Float, e: Float): Float = ((t - s) / (e - s)).coerceIn(0f, 1f)
private fun easeOutCubic(x: Float): Float {
    val m = 1f - x; return 1f - m * m * m
}
private fun easeInOutSine(x: Float): Float = (-(cos(PI * x) - 1.0) / 2.0).toFloat()

/**
 * Intro de marca (cold start) com a logo oficial do NBR Play (brand kit). Pulável (tecla/clique/Voltar).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val t = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    var done by remember { mutableStateOf(false) }

    fun finish() {
        if (!done) {
            done = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        launch {
            t.animateTo(TOTAL, tween(durationMillis = (TOTAL * 1000).toInt(), easing = LinearEasing))
            finish()
        }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Voltar durante a intro pula direto (não abre o diálogo de sair que existe por trás).
    BackHandler(enabled = true) { finish() }

    val time = t.value
    val enter = easeOutCubic(seg(time, 0f, ENTER))
    val breathe = easeInOutSine(seg(time, ENTER, TOTAL))
    val out = easeOutCubic(seg(time, TOTAL - FADE_OUT, TOTAL))

    Box(
        modifier = Modifier
            .fillMaxSize()
            // O overlay INTEIRO some no fim, revelando a Biblioteca já pronta atrás.
            .graphicsLayer { alpha = 1f - out }
            .background(BrandColors.Background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) { finish(); true } else false
            }
            .clickable { finish() },
        contentAlignment = Alignment.Center
    ) {
        // Brilho suave por trás da marca.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter }
                .background(Brush.radialGradient(listOf(Color(0x33B8C8E0), Color(0x00000000))))
        )
        Image(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = "NBR Play",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(1f)
                .padding(8.dp)
                .graphicsLayer {
                    val s = 0.92f + 0.08f * enter + 0.02f * breathe + 0.06f * out
                    scaleX = s
                    scaleY = s
                    alpha = enter
                }
        )
    }
}
