package com.ntv2.app.core.ui

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Animação mostrada no card enquanto a capa não chega. Escolhida nas Configurações, com prévia.
 * Os efeitos vêm dos exemplos em components/preview.html.
 */
enum class CardLoadingStyle(val label: String, val description: String) {
    SKELETON_SHIMMER("Skeleton shimmer", "Placeholder claro com brilho passando na horizontal."),
    DARK_SHIMMER("Shimmer escuro", "O mesmo brilho, em tons escuros — combina com o fundo do app."),
    BREATHING_GRADIENT("Gradiente respirando", "Gradiente suave que se move devagar, sem repetição marcada."),
    BLUR_UP("Blur-up", "Mostra a capa em baixa resolução, desfocada, até a definitiva chegar."),
    FADE_IN("Fade-in", "Fundo neutro e a capa surgindo aos poucos, sem troca seca."),
    SPINNER("Spinner", "O indicador circular verde no centro do card.");

    /** Precisa aparecer imediatamente (a miniatura do blur-up tem que comecar a baixar ja). */
    val startsImmediately: Boolean get() = this == BLUR_UP

    companion object {
        val DEFAULT = SKELETON_SHIMMER
        fun fromName(name: String?): CardLoadingStyle =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Prévia da capa em resolução mínima para o blur-up: no TMDB troca a largura na URL (w342/w780 →
 * w92), o que baixa ~5 KB e costuma chegar antes da capa. Em caminhos locais (miniatura do
 * Telegram) devolve o próprio caminho.
 */
fun tinyCoverUrl(cover: String): String = tmdbAtWidth(cover, "w92")

/**
 * Força a largura de uma URL do TMDB. As legendas dos canais às vezes trazem o endereço já com uma
 * largura enorme (w1280 na arte de fundo): pedir uma menor é a diferença entre centenas de KB e
 * algumas dezenas em quem está numa rede ruim. Caminhos locais passam sem alteração.
 */
fun tmdbAtWidth(url: String, width: String): String =
    if (url.startsWith("http")) TMDB_WIDTH.replace(url, "/$width/") else url

private val TMDB_WIDTH = Regex("/w(?:92|154|185|342|500|780|1280)/")

@Composable
fun CardLoadingPlaceholder(
    style: CardLoadingStyle,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    /** Capa em tamanho real; só o blur-up usa (baixa a versão mínima dela). */
    cover: String? = null,
    /** Prévia das Configurações: simula o efeito em laço, sem uma capa real. */
    preview: Boolean = false
) {
    when (style) {
        CardLoadingStyle.SKELETON_SHIMMER -> Shimmer(modifier, animate, LIGHT_SHIMMER)
        CardLoadingStyle.DARK_SHIMMER -> Shimmer(modifier, animate, DARK_SHIMMER)
        CardLoadingStyle.BREATHING_GRADIENT -> BreathingGradient(modifier, animate)
        CardLoadingStyle.BLUR_UP -> BlurUp(modifier, animate, cover, preview)
        CardLoadingStyle.FADE_IN -> FadeIn(modifier, animate, preview)
        CardLoadingStyle.SPINNER -> Box(modifier.background(PLACEHOLDER_BG), Alignment.Center) {
            CircularProgressIndicator(color = BRAND_GREEN, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

private val LIGHT_SHIMMER = listOf(Color(0xFFD7DDE5), Color(0xFFF7F9FB), Color(0xFFD7DDE5))
private val DARK_SHIMMER = listOf(Color(0xFF202A37), Color(0xFF364356), Color(0xFF202A37))
private val PLACEHOLDER_BG = Color(0xFF1C1C1C)
private val BRAND_GREEN = Color(0xFF2BEE34)
// "Pôster" falso das prévias (fade-in e blur-up não têm capa real para mostrar ali).
private val FAKE_POSTER = listOf(Color(0xFF7A4FA3), Color(0xFF2F6FB0), Color(0xFF1F9C8B))

@Composable
private fun Shimmer(modifier: Modifier, animate: Boolean, colors: List<Color>) {
    val progress = if (animate) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_250, easing = LinearEasing)),
            label = "shimmer-progress"
        ).value
    } else {
        0.5f
    }
    Box(
        modifier = modifier.drawBehind {
            // A faixa clara percorre o card: desloca o gradiente de fora a fora da largura.
            val span = size.width * 2f
            val start = -span + progress * (size.width + span)
            drawRect(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(start, 0f),
                    end = Offset(start + span, size.height)
                )
            )
        }
    )
}

@Composable
private fun BreathingGradient(modifier: Modifier, animate: Boolean) {
    val shift = if (animate) {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath-shift"
        ).value
    } else {
        0.5f
    }
    Box(
        modifier = modifier.drawBehind {
            val offset = shift * size.minDimension
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF283242), Color(0xFF3C4A5E), Color(0xFF232C39)),
                    start = Offset(-offset, -offset),
                    end = Offset(size.width + offset, size.height + offset)
                )
            )
        }
    )
}

/**
 * Fundo neutro: o efeito em si é a CAPA surgindo por cima (entrada com alpha no MediaCard).
 * Na prévia, uma "capa" falsa aparece em laço para dar para ver o efeito.
 */
@Composable
private fun FadeIn(modifier: Modifier, animate: Boolean, preview: Boolean) {
    Box(modifier = modifier.background(Color(0xFF232C39))) {
        if (!preview) return@Box
        val alpha = if (animate) {
            rememberInfiniteTransition(label = "fade").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "fade-alpha"
            ).value
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(Brush.linearGradient(FAKE_POSTER))
        )
    }
}

@Composable
private fun BlurUp(modifier: Modifier, animate: Boolean, cover: String?, preview: Boolean) {
    Box(modifier = modifier.background(PLACEHOLDER_BG)) {
        if (cover == null) {
            // Sem capa real (prévia): um "pôster" falso saindo do desfoque, em laço.
            val radius = if (animate && preview) {
                rememberInfiniteTransition(label = "blur-preview").animateFloat(
                    initialValue = 18f,
                    targetValue = 2f,
                    animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "blur-radius"
                ).value
            } else {
                12f
            }
            val fake = Modifier.fillMaxSize().background(Brush.linearGradient(FAKE_POSTER))
            Box(
                modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.fillMaxSize().blur(radius.dp).then(fake)
                } else {
                    fake
                }
            )
            return@Box
        }
        val context = LocalContext.current
        val tiny = remember(cover) { ImageRequest.Builder(context).data(tinyCoverUrl(cover)).build() }
        // API 31+: desfoque real. Abaixo disso a própria ampliação da miniatura já borra.
        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.fillMaxSize().blur(16.dp)
        } else {
            Modifier.fillMaxSize()
        }
        AsyncImage(
            model = tiny,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = blurModifier
        )
    }
}
