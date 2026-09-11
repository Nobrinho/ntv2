package com.ntv2.app.feature.splash.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Paleta (import do Claude Design). Ground escuro + ink verde (estilo Netflix); troque a ordem
// para o padrão do design (ground verde + ink escuro).
private val GROUND = Color(0xFF141414)
private val INK = Color(0xFF2BEE34)

// Linha do tempo autorada (Draw 1.0 + Wordmark 0.5 + Hold estendido). A escrita mantém o ritmo;
// o "hold" mais longo dá tempo do app carregar por trás. O lift continua nos 0.26s finais.
private const val WORDMARK = 1.0f
private const val HOLD = 1.5f
private const val TOTAL = 3.5f

// viewBox do SVG do design: "80 118 280 190".
private const val VB_X = 80f
private const val VB_Y = 118f
private const val VB_W = 280f
private const val VB_H = 190f

private const val SCRIPT_TX = 105.83f
private const val SCRIPT_TY = 239.51f
private const val PLAY_TX = 171.96f
private const val PLAY_TY = 291.95f

private fun seg(t: Float, s: Float, e: Float): Float = ((t - s) / (e - s)).coerceIn(0f, 1f)
private fun easeInOutSine(x: Float): Float = (-(cos(PI * x) - 1.0) / 2.0).toFloat()
private fun easeOutCubic(x: Float): Float {
    val m = 1f - x; return 1f - m * m * m
}
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f; val m = x - 1f
    return 1f + c3 * m * m * m + c1 * m * m
}

/**
 * Intro de marca (cold start) — porte fiel do projeto Claude Design "Nbr PLAY Splash":
 *   Draw    → o script "Nbr" se revela da esquerda p/ direita (máscara diagonal suave + ponta de caneta)
 *   Wordmark→ "PLAY" surge letra a letra (easeOutBack, sobe + fade)
 *   Hold    → a marca assenta, respira e no fim sobe/some, entregando ao app
 * Desenhado em Canvas + PathParser (vetor nativo, nítido, offline). Pulável (tecla/clique).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val t = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    var done by remember { mutableStateOf(false) }
    val pen = true

    val scriptPath = remember { PathParser().parsePathString(NbrArt.SCRIPT).toPath() }
    val glyphPaths = remember { NbrArt.GLYPHS.map { PathParser().parsePathString(it).toPath() } }

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

    // Câmera: assenta na "draw", respira no "hold", sobe no hand-off (idêntico ao design).
    val settle = easeOutCubic(seg(time, 0f, HOLD + 0.05f))
    val breathe = easeOutCubic(seg(time, HOLD, TOTAL))
    val thunk = sin(PI * seg(time, WORDMARK + 0.22f, WORDMARK + 0.44f)).toFloat()
    val out = easeOutCubic(seg(time, TOTAL - 0.26f, TOTAL))
    val camScale = 1.05f - 0.05f * settle + 0.012f * breathe + 0.02f * thunk + 0.07f * out

    Box(
        modifier = Modifier
            .fillMaxSize()
            // O overlay INTEIRO some no fim (últimos 0.26s), revelando a Biblioteca já pronta atrás.
            .graphicsLayer { alpha = 1f - out }
            .background(GROUND)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) { finish(); true } else false
            }
            .clickable { finish() }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = camScale
                    scaleY = camScale
                    transformOrigin = TransformOrigin(0.5f, 0.52f)
                }
        ) {
            val fit = minOf(size.width / VB_W, size.height / VB_H) * 0.62f
            val tx = (size.width - VB_W * fit) / 2f - VB_X * fit
            val ty = (size.height - VB_H * fit) / 2f - VB_Y * fit

            // Progresso da escrita do script.
            val p = easeInOutSine(seg(time, 0.12f, WORDMARK + 0.05f))
            val wipeX = 78f + p * 294f

            withTransform({
                translate(tx, ty)
                scale(fit, fit, pivot = Offset.Zero)
            }) {
                // SCRIPT com wipe diagonal suave (máscara por gradiente, DstIn).
                val maskRect = Rect(60f, 100f, 400f, 340f)
                drawContext.canvas.saveLayer(maskRect, Paint())
                withTransform({ translate(SCRIPT_TX, SCRIPT_TY) }) {
                    drawPath(scriptPath, color = INK)
                }
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.White,
                        1f to Color.Transparent,
                        start = Offset(wipeX - 17f, 252f),
                        end = Offset(wipeX + 17f, 228f)
                    ),
                    topLeft = Offset(60f, 100f),
                    size = Size(340f, 240f),
                    blendMode = BlendMode.DstIn
                )
                drawContext.canvas.restore()

                // Ponta de caneta na frente da escrita.
                val penOn = pen && p > 0.002f && p < 0.998f
                if (penOn) {
                    val penAlpha = (minOf(p * 9f, (1f - p) * 9f)).coerceIn(0f, 1f) * 0.5f
                    withTransform({
                        translate(wipeX, 0f)
                        rotate(-35f, pivot = Offset(0f, 196f))
                    }) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = penAlpha),
                            topLeft = Offset(-1.1f, 134f),
                            size = Size(2.2f, 124f),
                            cornerRadius = CornerRadius(1.1f, 1.1f)
                        )
                    }
                }

                // "PLAY" letra a letra.
                withTransform({ translate(PLAY_TX, PLAY_TY) }) {
                    glyphPaths.forEachIndexed { i, path ->
                        val a = easeOutBack(
                            seg(time, WORDMARK + i * 0.065f, WORDMARK + i * 0.065f + 0.28f)
                        )
                        withTransform({ translate(0f, (1f - a) * 15f) }) {
                            drawPath(path, color = INK, alpha = (a * 1.6f).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
    }
}
