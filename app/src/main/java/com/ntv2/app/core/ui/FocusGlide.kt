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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

private val FOCUS_ACCENT = com.ntv2.app.core.ui.BrandColors.Accent

/** Valor do toggle "Animações" das Configurações, disponível para qualquer tela. */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * Anel de foco da tela atual (null = sem anel deslizante: cada item desenha o próprio destaque).
 * Quem provê é [FocusGlideScope] / a grade da biblioteca, só com "Animações" ligadas.
 */
internal val LocalFocusGlide = compositionLocalOf<FocusGlideState?> { null }

/** Duração do deslize do anel e do zoom dos cards (a mesma curva, para o aro acompanhar o card). */
const val FOCUS_ANIM_MS = 160

/** Quadros de tolerância entre perder o foco num item e ganhar no próximo antes de o anel sumir. */
private const val BLUR_GRACE_FRAMES = 2

/**
 * Anel de foco ÚNICO que desliza de um item para o outro (com "Animações" ligadas). Sem ele, o
 * destaque "piscava": sumia de um item e aparecia no próximo. Cada item informa seus limites ao
 * ganhar o foco (e enquanto rola); o anel interpola da posição EXIBIDA até o item atual.
 *
 * - Parte de onde o anel está na tela (não do destino anterior): cliques rápidos encadeiam o
 *   movimento em vez de saltar para trás.
 * - Perder o foco não apaga na hora: espera [BLUR_GRACE_FRAMES] quadros pelo próximo item.
 * - Item focado antes de ter posição (lista que acabou de rolar) completa o foco no primeiro layout.
 * - Se a lista rola durante o deslize, o ponto de partida rola junto.
 */
@Stable
internal class FocusGlideState {
    var focusedId by mutableStateOf<Any?>(null)
        private set
    private var live by mutableStateOf<Rect?>(null)
    private var liveRadius by mutableFloatStateOf(0f)
    private var liveScale by mutableFloatStateOf(1f)
    /** Início do deslize, já inflado pela escala (a posição exibida quando o foco mudou). */
    private var from by mutableStateOf<Rect?>(null)
    private var fromRadius by mutableFloatStateOf(0f)
    var t by mutableFloatStateOf(1f)
    var alpha by mutableFloatStateOf(0f)
    /** Alvo da transparência: true enquanto algum item tem (ou acabou de ter) o foco. */
    var visible by mutableStateOf(false)
        private set
    var epoch by mutableIntStateOf(0)
        private set
    var blurEpoch by mutableIntStateOf(0)
        private set
    var box: LayoutCoordinates? = null

    // Último quadro desenhado (não é estado: só serve de ponto de partida do próximo deslize).
    private var shown: Rect? = null
    private var shownRadius = 0f
    private var pendingId: Any? = null
    private var pendingRadius = 0f
    private var pendingScale = 1f

    fun boundsOf(c: LayoutCoordinates): Rect? {
        val b = box ?: return null
        return if (b.isAttached && c.isAttached) b.localBoundingBoxOf(c) else null
    }

    /** Retângulo e raio de canto atuais do anel (interpolados entre a partida e o item atual). */
    fun displayed(): Pair<Rect, Float>? {
        val l0 = live ?: return null
        val l = inflate(l0, liveScale)
        val lr = liveRadius * liveScale
        val f = from
        val result = if (f == null || t >= 1f) l to lr else lerp(f, l, t) to (fromRadius + (lr - fromRadius) * t)
        shown = result.first
        shownRadius = result.second
        return result
    }

    private fun inflate(r: Rect, s: Float): Rect {
        if (s == 1f) return r
        val dx = r.width * (s - 1f) / 2f
        val dy = r.height * (s - 1f) / 2f
        return Rect(r.left - dx, r.top - dy, r.right + dx, r.bottom + dy)
    }

    fun onFocus(id: Any, bounds: Rect, radiusPx: Float, scale: Float = 1f) {
        // Anel na tela (ou sumindo): desliza de onde está. Apagado: aparece direto no item.
        val start = if (visible || alpha > 0f) shown else null
        pendingId = null
        from = start
        fromRadius = shownRadius
        live = bounds
        liveRadius = radiusPx
        liveScale = scale
        t = if (start != null) 0f else 1f
        focusedId = id
        visible = true
        epoch++
    }

    /** Item ganhou foco mas ainda não tem posição: completa em [onPositioned]. */
    fun onFocusPending(id: Any, radiusPx: Float, scale: Float = 1f) {
        focusedId = id
        pendingId = id
        pendingRadius = radiusPx
        pendingScale = scale
    }

    /** Layout de um item: conclui um foco pendente ou acompanha o item focado (rolagem, tamanho). */
    fun onPositioned(id: Any, bounds: Rect) {
        if (pendingId == id) onFocus(id, bounds, pendingRadius, pendingScale)
        else if (focusedId == id) updateLive(bounds)
    }

    fun updateLive(bounds: Rect) {
        val old = live
        live = bounds
        val f = from ?: return
        if (old == null || t >= 1f) return
        val dx = bounds.left - old.left
        val dy = bounds.top - old.top
        if (dx == 0f && dy == 0f) return
        val size = box?.takeIf { it.isAttached }?.size
        val far = size != null && (abs(dx) > size.width || abs(dy) > size.height)
        // A lista rolou durante o deslize: a partida rola junto. Mais de uma tela: encerra no item.
        if (far) {
            from = null
            t = 1f
        } else {
            from = f.translate(dx, dy)
        }
    }

    fun onBlur(id: Any) {
        if (focusedId == id) {
            focusedId = null
            pendingId = null
            blurEpoch++
        }
    }

    /** Fim da tolerância: nenhum item pegou o foco, o anel some. */
    fun hideIfIdle() {
        if (focusedId == null) visible = false
    }
}

/** Cria o estado do anel deslizante (null com "Animações" desligadas) e roda suas animações. */
@Composable
internal fun rememberFocusGlide(enabled: Boolean): FocusGlideState? {
    if (!enabled) return null
    val state = remember { FocusGlideState() }
    LaunchedEffect(state.epoch) {
        if (state.t < 1f) {
            animate(0f, 1f, animationSpec = tween(FOCUS_ANIM_MS, easing = FastOutSlowInEasing)) { v, _ -> state.t = v }
        }
    }
    LaunchedEffect(state.blurEpoch) {
        if (state.blurEpoch == 0) return@LaunchedEffect
        repeat(BLUR_GRACE_FRAMES) { withFrameNanos { } }
        state.hideIfIdle()
    }
    LaunchedEffect(state.visible) {
        animate(
            initialValue = state.alpha,
            targetValue = if (state.visible) 1f else 0f,
            animationSpec = tween(if (state.visible) 120 else 160)
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
internal fun Modifier.focusGlideTarget(id: Any, glide: FocusGlideState?, radius: Dp = 6.dp, scale: Float = 1f): Modifier {
    if (glide == null) return this
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { c ->
            holder[0] = c
            if (glide.focusedId == id) glide.boundsOf(c)?.let { glide.onPositioned(id, it) }
        }
        .onFocusChanged { s ->
            if (s.isFocused) {
                val b = holder[0]?.let { glide.boundsOf(it) }
                if (b != null) glide.onFocus(id, b, radiusPx, scale) else glide.onFocusPending(id, radiusPx, scale)
            } else {
                glide.onBlur(id)
            }
        }
}

/** Atalho: participa do anel da tela (se houver), com id próprio. */
@Composable
fun Modifier.glideTarget(radius: Dp = 6.dp, scale: Float = 1f): Modifier =
    focusGlideTarget(remember { Any() }, LocalFocusGlide.current, radius, scale)

/** Escala de foco da TV (brand kit v2.1: ~1,07). */
const val BRAND_FOCUS_SCALE = 1.07f

/** true quando a tela tem o anel deslizante: o item não deve desenhar o próprio aro de foco. */
@Composable
fun glideActive(): Boolean = LocalFocusGlide.current != null

/** Camada por cima do conteúdo que desenha o anel deslizante. */
@Composable
internal fun FocusGlideRing(glide: FocusGlideState?) {
    if (glide == null) return
    // Brand kit v2.1: foco com contorno de 3 px (azul NBR) e glow contido; mantém o raio do componente.
    val strokeDp = 3.dp
    val gapDp = 2.dp
    Spacer(
        modifier = Modifier.fillMaxSize().graphicsLayer().drawBehind {
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
    // Dentro de outra tela com anel (overlay de detalhes sobre a biblioteca etc.): usa o MESMO anel.
    // Dois anéis empilhados apareciam juntos na troca (um sumindo, o outro surgindo).
    if (LocalFocusGlide.current != null) {
        androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
        return
    }
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
    // Glow contido: traço largo e translúcido por baixo do contorno.
    val glow = strokePx * 2.5f
    drawRoundRect(
        color = FOCUS_ACCENT.copy(alpha = 0.22f * alpha),
        topLeft = Offset(strokePx / 2 - glow / 2, strokePx / 2 - glow / 2),
        size = Size(size.width - strokePx + glow, size.height - strokePx + glow),
        cornerRadius = CornerRadius(radius + glow / 2),
        style = Stroke(glow)
    )
    drawRoundRect(
        color = FOCUS_ACCENT.copy(alpha = alpha),
        topLeft = Offset(strokePx / 2, strokePx / 2),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(radius),
        style = Stroke(strokePx)
    )
}

/**
 * Brilho de foco desenhado (traço largo e translúcido em volta do item). Substitui a sombra colorida
 * (`shadow` com elevação): a sombra é recalculada pela GPU a cada troca de foco e pesa em TV fraca.
 */
fun Modifier.focusGlow(focused: Boolean, cornerRadius: Dp, color: Color = FOCUS_ACCENT): Modifier =
    if (!focused) this else drawBehind {
        val glow = 6.dp.toPx()
        val radius = min(cornerRadius.toPx(), min(size.width, size.height) / 2f)
        drawRoundRect(
            color = color.copy(alpha = 0.22f),
            topLeft = Offset(-glow / 2, -glow / 2),
            size = Size(size.width + glow, size.height + glow),
            cornerRadius = CornerRadius(radius + glow / 2),
            style = Stroke(glow)
        )
    }

/** Aro de foco do app: verde com contorno escuro por dentro. A espessura acompanha o tamanho do item. */
internal fun DrawScope.drawFocusRing(size: Size, alpha: Float, cornerRadiusPx: Float = 6.dp.toPx()) {
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
