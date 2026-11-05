@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.feature.playback.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import com.ntv2.app.core.ui.trapFocus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import com.ntv2.app.core.player.PlaybackState
import com.ntv2.app.core.ui.icons.NbrIcons

// Telas de espera do player: status de carregamento, capa iluminada e erro.

/**
 * Spinner + estado ("Armazenando em buffer…") + progresso do download. Na TV fica compacto no
 * canto superior direito, sem cobrir o pôster/vídeo; no celular, centralizado.
 */
@Composable
internal fun BoxScope.LoadingStatus(
    label: String,
    progress: DownloadProgress?,
    hint: String?,
    isTv: Boolean,
    /** Controles abertos: fica abaixo da barra superior (senão cobria os ícones de legenda e opções). */
    belowTopBar: Boolean = false
) {
    if (isTv) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (belowTopBar) 88.dp else 32.dp, end = 40.dp)
                // 20% menor, ancorado no canto superior direito.
                .graphicsLayer {
                    scaleX = 0.8f
                    scaleY = 0.8f
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .background(PlayerPanelBg, RoundedCornerShape(16.dp))
                .border(1.dp, com.ntv2.app.core.ui.BrandColors.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerSpinner(size = 28.dp, stroke = 3.dp)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(label, color = Color(0xFFF5F5F7), style = com.ntv2.app.core.ui.NbrType.BodyStrong)
                LoadingProgress(progress, hint, TextAlign.End)
            }
        }
    } else {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Protótipo 23 (f8SzG): anel accent 64, espaço 16, "Abrindo…" 16/700, apoio 12/400 text-2.
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlayerSpinner()
            Text(label, color = Color(0xFFF5F5F7), style = com.ntv2.app.core.ui.NbrType.Section.copy(fontSize = 16.sp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LoadingProgress(progress, hint)
            }
        }
    }
}

/**
 * Capa com "iluminação dinâmica": o pôster nítido no centro e, atrás, uma luz com as cores dele
 * preenchendo as bordas (em vez de faixas pretas).
 * - [nativeBlur] e Android 12+: o próprio pôster ampliado com desfoque nativo (Modifier.blur).
 * - Senão (versão compatível): o pôster decodificado minúsculo (24 px) e ampliado com filtro —
 *   a ampliação já espalha as cores; roda em qualquer versão e é leve.
 * [animate] liga a luz "respirando" (pulso lento), vinculada ao toggle Animações.
 */
/** Saturação 1.7x e leve ganho de brilho para a luz da versão compatível. */
internal fun vividGlowMatrix(): ColorMatrix {
    val saturation = ColorMatrix().apply { setToSaturation(1.7f) }
    val gain = 1.15f
    val brightness = ColorMatrix(
        floatArrayOf(
            gain, 0f, 0f, 0f, 0f,
            0f, gain, 0f, 0f, 0f,
            0f, 0f, gain, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    saturation.timesAssign(brightness)
    return saturation
}

@Composable
internal fun AmbientPoster(url: String, title: String, nativeBlur: Boolean, animate: Boolean) {
    val useNativeBlur = nativeBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glowAlpha = if (animate) {
        val transition = rememberInfiniteTransition(label = "ambient-glow")
        transition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "ambient-glow-alpha"
        ).value
    } else {
        0.9f
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val glowModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = glowAlpha
                scaleX = 1.2f
                scaleY = 1.2f
            }
        if (useNativeBlur) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = glowModifier.blur(72.dp, BlurredEdgeTreatment.Unbounded)
            )
        } else {
            val context = LocalContext.current
            // 32 px: poucos pixels = cores bem espalhadas, mas sem virar uma média única.
            val tiny = remember(url) { ImageRequest.Builder(context).data(url).size(32).build() }
            AsyncImage(
                model = tiny,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                // Reduzir a imagem "lava" as cores (média): recupera saturação e brilho para a luz
                // ficar viva como no desfoque nativo (vermelho vermelho, não marrom).
                colorFilter = remember { ColorFilter.colorMatrix(vividGlowMatrix()) },
                modifier = glowModifier
            )
        }
        // Vinheta: escurece as bordas e dá contraste ao pôster, spinner e textos.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(0f to Color(0x11000000), 1f to Color(0x99000000)))
        )
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(vertical = 28.dp)
        )
        // Leve escurecimento geral para o spinner/rótulo por cima continuarem legíveis.
        Box(modifier = Modifier.fillMaxSize().background(Color(0x26000000)))
    }
}

/** Progresso do download durante a espera: "12,3 MB de 1,4 GB · 850 KB/s" + barra + aviso. */
@Composable
internal fun LoadingProgress(progress: DownloadProgress?, hint: String?, hintAlign: TextAlign = TextAlign.Center) {
    if (progress != null && progress.downloadedBytes > 0L) {
        val parts = progress.parts
        val text = buildString {
            if (parts != null) append("Filme todo: ")
            append(formatBytes(progress.downloadedBytes))
            if (progress.expectedBytes > 0L) append(" de ").append(formatBytes(progress.expectedBytes))
            if (progress.bytesPerSecond > 0L) append(" · ").append(formatBytes(progress.bytesPerSecond)).append("/s")
        }
        // Filme dividido: primeiro em que parte está (é o que anda), depois o total do filme.
        if (parts != null) {
            Text(
                PartsTexts.currentPartLine(parts),
                color = Color(0xFFF5F5F7),
                style = com.ntv2.app.core.ui.NbrType.Caption
            )
        }
        Text(text, color = com.ntv2.app.core.ui.BrandColors.TextSecondary, style = com.ntv2.app.core.ui.NbrType.Caption)
        if (progress.expectedBytes > 0L) {
            val fraction = (progress.downloadedBytes.toFloat() / progress.expectedBytes).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(com.ntv2.app.core.ui.BrandColors.Accent)
                )
            }
        }
    }
    if (hint != null) {
        Text(
            hint,
            color = com.ntv2.app.core.ui.BrandColors.Warning,
            style = com.ntv2.app.core.ui.NbrType.Caption,
            textAlign = hintAlign,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}

/**
 * Falha ao carregar o vídeo: motivo em português + "Tentar novamente" (foco inicial, retoma o
 * download de onde parou) e "Voltar". Prende o foco do D-pad nos botões.
 */
@Composable
internal fun LoadErrorOverlay(error: PlayerLoadError, onRetry: () -> Unit, onBack: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(error) { runCatching { retryFocus.requestFocus() } }
    // Protótipo 24 (YICW7): cartão 400 de largura, surface@95%, contorno warning, raio 20, padding 20, espaço 12;
    // ícone 32 warning, título 17/700, texto 13/400 text-2 e botão secundário em pílula de 52.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(PlayerPanelBg)
                .border(1.dp, com.ntv2.app.core.ui.BrandColors.Warning, shape)
                .padding(20.dp)
                .trapFocus(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(NbrIcons.TriangleAlert, contentDescription = null, tint = com.ntv2.app.core.ui.BrandColors.Warning, modifier = Modifier.size(32.dp))
            Text(
                error.title,
                color = Color(0xFFF5F5F7),
                style = com.ntv2.app.core.ui.NbrType.Section,
                textAlign = TextAlign.Center
            )
            Text(
                error.message,
                color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                style = com.ntv2.app.core.ui.NbrType.Secondary,
                textAlign = TextAlign.Center
            )
            error.detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = com.ntv2.app.core.ui.BrandColors.TextMuted,
                    style = com.ntv2.app.core.ui.NbrType.Caption.copy(fontSize = 11.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // O protótipo só tem "Tentar de novo"; "Voltar" é do app e fica, no mesmo estilo, embaixo.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ErrorActionButton(
                    icon = NbrIcons.RefreshCw,
                    label = "Tentar novamente",
                    modifier = Modifier.fillMaxWidth().focusRequester(retryFocus),
                    onClick = onRetry
                )
                ErrorActionButton(
                    icon = NbrIcons.ChevronLeft,
                    label = "Voltar",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack
                )
            }
        }
    }
}

@Composable
internal fun ErrorActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PlayerSecondaryButton(label = label, onClick = onClick, icon = icon, modifier = modifier)
}

/** Rótulo do estado exibido junto do loading durante a reprodução (null = não mostrar). */
internal fun loadingLabel(playbackState: PlaybackState): String? = when (playbackState) {
    PlaybackState.Preparing -> "Preparando…"
    PlaybackState.Buffering -> "Armazenando em buffer…"
    else -> null
}
