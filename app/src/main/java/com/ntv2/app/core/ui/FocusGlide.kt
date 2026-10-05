package com.ntv2.app.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

private val FOCUS_ACCENT = com.ntv2.app.core.ui.BrandColors.Accent

/** Valor do toggle "Animações" das Configurações, disponível para qualquer tela. */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * Anel de foco da tela atual (null = sem anel deslizante: cada item desenha o próprio destaque).
 * Quem provê é [FocusGlideScope] / a grade da biblioteca, só com "Animações" ligadas.
 */
internal val LocalFocusGlide = compositionLocalOf<FocusGlideState?> { null }

/**
 * Anel de foco ÚNICO que desliza de um item para o outro (com "Animações" ligadas). Sem ele, o
 * destaque "piscava": sumia de um item e aparecia no próximo. Cada item informa seus limites ao
 * ganhar o foco (e enquanto rola); o anel interpola da posição anterior até o item atual.
 */
@Stable
internal class FocusGlideState {
    var focusedId by mutableStateOf<Any?>(null)
    private var live by mutableStateOf<Rect?>(null)
    private var liveRadius by mutableFloatStateOf(0f)
    private var prev by mutableStateOf<Rect?>(null)
    private var prevRadius by mutableFloatStateOf(0f)
    var t by mutableFloatStateOf(1f)
    var alpha by mutableFloatStateOf(0f)
    var epoch by mutableIntStateOf(0)
    var box: LayoutCoordinates? = null

    fun boundsOf(c: LayoutCoordinates): Rect? {
        val b = box ?: return null
        return if (b.isAttached && c.isAttached) b.localBoundingBoxOf(c) else null
    }

    /** Retângulo e raio de canto atuais do anel (interpolados entre o item anterior e o atual). */
    fun displayed(): Pair<Rect, Float>? {
        val l = live ?: return null
        val p = prev ?: return l to liveRadius
        return lerp(p, l, t) to (prevRadius + (liveRadius - prevRadius) * t)
    }

    fun updateLive(bounds: Rect) {
        live = bounds
    }

    fun onFocus(id: Any, bounds: Rect, radiusPx: Float) {
        val start = if (live != null && alpha > 0.3f) displayed() else null
        prev = start?.first
        prevRadius = start?.second ?: radiusPx
        live = bounds
        liveRadius = radiusPx
        t = if (start != null) 0f else 1f
        focusedId = id
        epoch++
    }

    fun onBlur(id: Any) {
        if (focusedId == id) focusedId = null
    }
}

/** Cria o estado do anel deslizante (null com "Animações" desligadas) e roda suas animações. */
@Composable
internal fun rememberFocusGlide(enabled: Boolean): FocusGlideState? {
    if (!enabled) return null
    val state = remember { FocusGlideState() }
    LaunchedEffect(state.epoch) {
        if (state.t < 1f) {
            animate(0f, 1f, animationSpec = tween(200, easing = FastOutSlowInEasing)) { v, _ -> state.t = v }
        }
    }
    val active = state.focusedId != null
    LaunchedEffect(active) {
        animate(
            initialValue = state.alpha,
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(if (active) 120 else 160)
        ) { v, _ -> state.alpha = v }
    }
    return state
}

/** Marca o contêiner como referência das coordenadas do anel. */
internal fun Modifier.focusGlideHost(glide: FocusGlideState?): Modifier =
    if (glide == null) this else onGloballyPositioned { glide.box = it }

/**
 * Faz o item participar do anel deslizante: informa os limites ao focar e enquanto rola. Deve vir
 * ANTES (acima) do `focusRequester`/`clickable` na cadeia, como qualquer `onFocusChanged`.
 */
@Composable
internal fun Modifier.focusGlideTarget(id: Any, glide: FocusGlideState?, radius: Dp = 12.dp): Modifier {
    if (glide == null) return this
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { c ->
            holder[0] = c
            if (glide.focusedId == id) glide.boundsOf(c)?.let { glide.updateLive(it) }
        }
        .onFocusChanged { s ->
            if (s.isFocused) holder[0]?.let { c -> glide.boundsOf(c)?.let { glide.onFocus(id, it, radiusPx) } }
            else glide.onBlur(id)
        }
}

/** Atalho: participa do anel da tela (se houver), com id próprio. */
@Composable
fun Modifier.glideTarget(radius: Dp = 12.dp): Modifier =
    focusGlideTarget(remember { Any() }, LocalFocusGlide.current, radius)

/** true quando a tela tem o anel deslizante: o item não deve desenhar o próprio aro de foco. */
@Composable
fun glideActive(): Boolean = LocalFocusGlide.current != null

/** Camada por cima do conteúdo que desenha o anel deslizante. */
@Composable
internal fun FocusGlideRing(glide: FocusGlideState?) {
    if (glide == null) return
    // Manual da marca: anel de 3 px + afastamento de 4 px; na TV, 4 px + 6 px.
    val tv = rememberAdaptiveLayoutInfo().isTv
    val strokeDp = if (tv) 4.dp else 3.dp
    val gapDp = if (tv) 6.dp else 4.dp
    Spacer(
        modifier = Modifier.fillMaxSize().drawBehind {
            val (r, radius) = glide.displayed() ?: return@drawBehind
            val a = glide.alpha
            if (a <= 0f) return@drawBehind
            val gap = gapDp.toPx()
            translate(r.left - gap, r.top - gap) {
                drawSpacedFocusRing(Size(r.width + 2 * gap, r.height + 2 * gap), a, radius + gap, strokeDp.toPx())
            }
        }
    )
}

/**
 * Raiz de uma tela com anel deslizante: provê o anel aos itens (via [glideTarget]) e o desenha por
 * cima do conteúdo. Com "Animações" desligadas é só um contêiner e os itens desenham o aro na hora.
 */
@Composable
fun FocusGlideScope(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glide = rememberFocusGlide(LocalAnimationsEnabled.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalFocusGlide provides glide) {
        androidx.compose.foundation.layout.Box(modifier = modifier.focusGlideHost(glide)) {
            content()
            FocusGlideRing(glide)
        }
    }
}

/** Anel do manual da marca: um traço único no acento, afastado do item (o chamador infla a área pelo afastamento). */
internal fun DrawScope.drawSpacedFocusRing(size: Size, alpha: Float, cornerRadiusPx: Float, strokePx: Float) {
    val radius = min(cornerRadiusPx, min(size.width, size.height) / 2f)
    drawRoundRect(
        color = FOCUS_ACCENT.copy(alpha = alpha),
        topLeft = Offset(strokePx / 2, strokePx / 2),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(radius),
        style = Stroke(strokePx)
    )
}

/** Aro de foco do app: verde com contorno escuro por dentro. A espessura acompanha o tamanho do item. */
internal fun DrawScope.drawFocusRing(size: Size, alpha: Float, cornerRadiusPx: Float = 12.dp.toPx()) {
    val outer = (min(size.width, size.height) * 0.07f).coerceIn(2.dp.toPx(), 4.dp.toPx())
    val inner = outer / 2f
    val radius = min(cornerRadiusPx, min(size.width, size.height) / 2f)
    drawRoundRect(
        color = FOCUS_ACCENT.copy(alpha = alpha),
        topLeft = Offset(outer / 2, outer / 2),
        size = Size(size.width - outer, size.height - outer),
        cornerRadius = CornerRadius(radius),
        style = Stroke(outer)
    )
    drawRoundRect(
        color = Color(0xE6000000).copy(alpha = 0xE6 / 255f * alpha),
        topLeft = Offset(outer + inner / 2, outer + inner / 2),
        size = Size(size.width - 2 * outer - inner, size.height - 2 * outer - inner),
        cornerRadius = CornerRadius((radius - outer).coerceAtLeast(0f)),
        style = Stroke(inner)
    )
}
