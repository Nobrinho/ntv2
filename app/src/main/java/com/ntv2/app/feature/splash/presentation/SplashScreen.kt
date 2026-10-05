package com.ntv2.app.feature.splash.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ntv2.app.R
import com.ntv2.app.core.ui.BrandBackdropKind
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.brandBackdrop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

// Linha do tempo (s): o brilho de fundo acende, a marca "escreve" da esquerda p/ a direita com um pop
// elástico, um reflexo de luz cruza a logo, o anel de pulso e a linha de acento fecham a entrada.
// A marca assenta e o overlay some nos FADE_OUT finais, revelando a Biblioteca já pronta por trás.
private const val GLOW_IN = 0.6f
private const val WIPE_START = 0.15f
private const val WIPE_END = 0.95f
private const val SWEEP_START = 0.95f
private const val SWEEP_END = 1.65f
private const val RING_START = 0.8f
private const val RING_END = 1.9f
private const val LINE_START = 1.0f
private const val LINE_END = 1.6f
private const val TOTAL = 3.0f
private const val FADE_OUT = 0.3f

private fun seg(t: Float, s: Float, e: Float): Float = ((t - s) / (e - s)).coerceIn(0f, 1f)
private fun easeOutCubic(x: Float): Float {
    val m = 1f - x; return 1f - m * m * m
}
private fun easeInOutCubic(x: Float): Float {
    if (x < 0.5f) return 4f * x * x * x
    val m = -2f * x + 2f
    return 1f - m * m * m / 2f
}
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val m = x - 1f
    return 1f + c3 * m * m * m + c1 * m * m
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

    // Lambdas: o estado da animação só é lido nas fases de draw/layer (sem recompor a tela por frame).
    val glowP = { easeOutCubic(seg(t.value, 0f, GLOW_IN)) }
    val wipeP = { easeInOutCubic(seg(t.value, WIPE_START, WIPE_END)) }
    val popP = { easeOutBack(seg(t.value, WIPE_START, WIPE_END + 0.25f)) }
    val sweepP = { easeInOutCubic(seg(t.value, SWEEP_START, SWEEP_END)) }
    val ringP = { easeOutCubic(seg(t.value, RING_START, RING_END)) }
    val lineP = { easeOutCubic(seg(t.value, LINE_START, LINE_END)) }
    val breatheP = { easeInOutSine(seg(t.value, WIPE_END, TOTAL)) }
    val outP = { easeOutCubic(seg(t.value, TOTAL - FADE_OUT, TOTAL)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // O overlay INTEIRO some no fim, revelando a Biblioteca já pronta atrás.
            .graphicsLayer { alpha = 1f - outP() }
            .background(BrandColors.Background)
            .brandBackdrop(BrandBackdropKind.Gradient)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) { finish(); true } else false
            }
            .clickable { finish() },
        contentAlignment = Alignment.Center
    ) {
        // Brilho, anel de pulso e linha de acento atrás/abaixo da marca.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glow = glowP()
            val ring = ringP()
            val line = lineP()
            val c = center
            val side = size.width * 0.42f
            val glowR = side * 1.25f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BrandColors.Accent.copy(alpha = 0.22f * glow), Color.Transparent),
                    center = c, radius = glowR
                ),
                radius = glowR, center = c
            )
            if (ring in 0.001f..0.999f) {
                drawCircle(
                    color = BrandColors.Accent.copy(alpha = 0.45f * (1f - ring)),
                    radius = side * (0.35f + 0.75f * ring), center = c,
                    style = Stroke(width = 3.dp.toPx() * (1f - ring) + 1f)
                )
            }
            if (line > 0f) {
                val y = c.y + side * 0.5f + 20.dp.toPx()
                val half = side * 0.22f * line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, BrandColors.Accent, Color.Transparent),
                        startX = c.x - half, endX = c.x + half
                    ),
                    start = Offset(c.x - half, y), end = Offset(c.x + half, y),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = "NBR Play",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(1f)
                .padding(8.dp)
                .graphicsLayer {
                    val pop = popP()
                    val s = (0.78f + 0.22f * pop) + 0.02f * breatheP() + 0.05f * outP()
                    scaleX = s
                    scaleY = s
                    rotationZ = -5f * (1f - pop.coerceAtMost(1f))
                    // Offscreen: a máscara (DstIn) e o reflexo (SrcAtop) só atingem os pixels da logo.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val wipe = wipeP()
                    val sweep = sweepP()
                    val w = size.width
                    val h = size.height
                    // Wipe esquerda→direita com borda suave.
                    val soft = w * 0.18f
                    val x0 = -soft + (w + soft) * wipe
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Black, Color.Transparent), startX = x0, endX = x0 + soft
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    // Reflexo diagonal de luz (gelo → branco) cruzando a marca.
                    if (sweep in 0.001f..0.999f) {
                        val band = w * 0.45f
                        val sx = -band + (w + band * 2f) * sweep
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BrandColors.Accent.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.95f),
                                    BrandColors.Accent.copy(alpha = 0.55f),
                                    Color.Transparent
                                ),
                                start = Offset(sx, h * 0.15f),
                                end = Offset(sx + band, h * 0.15f + band * 0.35f)
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
        )
    }
}
