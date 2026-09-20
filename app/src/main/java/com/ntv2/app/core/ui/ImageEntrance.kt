package com.ntv2.app.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Entradas de imagem da tela de detalhes, tiradas dos exemplos em components/preview.html
 * (aba "Entrada de imagens"): nº 4 para o fundo e nº 11 para o elenco.
 *
 * Ambas mexem só em alpha/translação/escala — o que a GPU faz sem redesenhar o conteúdo.
 * Com as animações desligadas nas Configurações, a imagem já nasce no lugar.
 */
private val ENTRANCE_EASING = CubicBezierEasing(0.2f, 0.82f, 0.28f, 1f)

/** Nº 4 — "slide da direita": a imagem chega deslocada do lado direito. */
@Composable
fun Modifier.slideInFromRight(visible: Boolean, enabled: Boolean = true): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible || !enabled) 1f else 0f,
        animationSpec = tween(durationMillis = if (enabled) 720 else 0, easing = ENTRANCE_EASING),
        label = "entrance-right"
    )
    return this.graphicsLayer {
        alpha = progress
        translationX = (1f - progress) * 76.dp.toPx()
        val scale = 0.96f + 0.04f * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Nº 11 — "stagger em sequência": sobe com fade, cada item saindo um pouco depois do anterior
 * (`index`), para a fileira aparecer em cascata em vez de toda de uma vez.
 */
@Composable
fun Modifier.fadeInUpStaggered(visible: Boolean, index: Int, enabled: Boolean = true): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible || !enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (enabled) 620 else 0,
            delayMillis = if (enabled) index * STAGGER_STEP_MS else 0,
            easing = ENTRANCE_EASING
        ),
        label = "entrance-stagger"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 38.dp.toPx()
        val scale = 0.97f + 0.03f * progress
        scaleX = scale
        scaleY = scale
    }
}

private const val STAGGER_STEP_MS = 110
