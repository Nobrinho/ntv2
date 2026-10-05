package com.ntv2.app.feature.media.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ntv2.app.core.ui.FocusGlideRing
import com.ntv2.app.core.ui.LocalFocusGlide
import com.ntv2.app.core.ui.drawFocusRing
import com.ntv2.app.core.ui.focusGlideHost
import com.ntv2.app.core.ui.focusGlideTarget
import com.ntv2.app.core.ui.rememberFocusGlide
import com.ntv2.app.core.ui.CardLoadingStyle
import com.ntv2.app.core.ui.CardLoadingPlaceholder
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import kotlin.math.roundToInt

// Grade da biblioteca: cards, esqueleto de carregamento e "Carregar mais".

// Botão único, largo e centralizado no fim da grade. Feedback de foco (verde) e de carregamento
// (spinner). A visibilidade/fade é controlada por quem o exibe (AnimatedVisibility).
@Composable
internal fun LoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val accent = com.ntv2.app.core.ui.BrandColors.Accent
    val content = if (focused) Color(0xFF0E0E0E) else Color.White
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .widthIn(min = 320.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !loading, onClick = onClick)
            .background(if (focused) accent else Color(0x1FFFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) accent else Color(0x44FFFFFF),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
            Text("Carregando…", color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Text("Carregar mais", color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Layout masonry (empacotamento por altura) otimizado para TV.
// 5 colunas virtuais: card retrato (9:16) ocupa 1 coluna; card horizontal (16:9) ocupa 2
// colunas adjacentes. A proporção vem do post (pôster → retrato; só frame → horizontal).
// 6 colunas: card horizontal (2 col) cabe 3 por linha; retrato (1 col) até 6 por linha.
// Colunas pela largura DISPONÍVEL da grade (dp): ~130dp por card. Cards menores no celular
// (2 no retrato, ~5 na paisagem) e ~6 na TV (teto). Evita card gigante em telas largas.
internal const val GRID_TARGET_COL_DP = 130f
internal fun columnsForWidthDp(widthDp: Float): Int =
    (widthDp / GRID_TARGET_COL_DP).toInt().coerceIn(2, 6)
internal val GRID_GAP = 12.dp
// Quantos cards antes do fim o foco dispara o carregamento da próxima página.
internal const val AUTO_LOAD_THRESHOLD = 10
internal val CARD_TITLE_H = 56.dp

@Composable
internal fun LazyMediaGrid(
    items: List<MediaCardUi>,
    showCovers: Boolean,
    cardLoadingStyle: CardLoadingStyle,
    animationsEnabled: Boolean,
    lowRamPlaybackWarnings: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    state: LazyStaggeredGridState,
    focusRequesterFor: (String) -> FocusRequester,
    onCardFocused: (String) -> Unit,
    onCardClick: (MediaCardUi) -> Unit,
    loadMoreFocus: FocusRequester,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    // Conteúdo fixo no topo da grade (trilhas de personalização), rolando junto com os cards.
    headerContent: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val hasHeader = headerContent != null
    // Grade de alturas mistas: a busca espacial padrão do ↑/↓ às vezes pula de coluna.
    // Aqui ↑/↓ seguem a mesma coluna (lane); se o alvo não está visível, rola um card e tenta de novo.
    fun moveInLane(mediaId: String, down: Boolean): Boolean {
        val cur = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == mediaId } ?: return false
        fun target() = state.layoutInfo.visibleItemsInfo
            .filter { it.lane == cur.lane && it.key != "load-more" && it.key != "library-header" }
            .filter { if (down) it.index > cur.index else it.index < cur.index }
            .let { l -> if (down) l.minByOrNull { it.index } else l.maxByOrNull { it.index } }
        fun focusLoadMore(): Boolean = down && hasMore &&
            runCatching { loadMoreFocus.requestFocus() }.isSuccess
        target()?.let { t ->
            runCatching { focusRequesterFor(t.key as String).requestFocus() }
            return true
        }
        // Subindo sem card acima na coluna (1ª linha): com cabeçalho (trilhas), deixa a busca de foco
        // padrão levar às trilhas — ela rola sozinha. Sem isto, ficava rolando preso na 1ª linha.
        if (!down && hasHeader) return false
        if (state.layoutInfo.visibleItemsInfo.any { it.key == "load-more" } && focusLoadMore()) return true
        val canScroll = if (down) state.canScrollForward else state.canScrollBackward
        if (!canScroll) return false
        scope.launch {
            val step = (cur.size.height + GRID_GAP.value * 2).toFloat()
            state.scrollBy(if (down) step else -step)
            withFrameNanos { }
            val t = target()
            if (t != null) runCatching { focusRequesterFor(t.key as String).requestFocus() } else focusLoadMore()
        }
        return true
    }
    // Com o anel da tela (rail + grade juntos) usa o dela; senão, um próprio só da grade.
    val screenGlide = LocalFocusGlide.current
    val ownGlide = rememberFocusGlide(animationsEnabled && screenGlide == null)
    val glide = screenGlide ?: ownGlide
    BoxWithConstraints(modifier = modifier.focusGlideHost(ownGlide)) {
        val columns = columnsForWidthDp(maxWidth.value)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            // Folga nas bordas: o card focado cresce 5% e ganha o aro verde; sem isso as colunas
            // e linhas das pontas cortavam o destaque.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalItemSpacing = GRID_GAP
        ) {
            if (headerContent != null) {
                item(key = "library-header", span = StaggeredGridItemSpan.FullLine) {
                    headerContent()
                }
            }
            items(
                items = items,
                key = { it.mediaId }
            ) { media ->
                val requester = focusRequesterFor(media.mediaId)
                MediaCard(
                    media = media,
                    showCover = showCovers,
                    cardLoadingStyle = cardLoadingStyle,
                    animationsEnabled = animationsEnabled,
                    showPlaybackWarning = lowRamPlaybackWarnings && media.needsLowRamPlaybackWarning(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGlideTarget(media.mediaId, glide)
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) onCardFocused(media.mediaId) }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> moveInLane(media.mediaId, down = true)
                                Key.DirectionUp -> moveInLane(media.mediaId, down = false)
                                else -> false
                            }
                        },
                    onClick = { onCardClick(media) }
                )
            }

            if (hasMore) {
                item(
                    key = "load-more",
                    span = StaggeredGridItemSpan.FullLine
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadMoreButton(
                            loading = loadingMore,
                            focusRequester = loadMoreFocus,
                            onClick = onLoadMore
                        )
                    }
                }
            }
        }
        FocusGlideRing(ownGlide)
    }
}

internal fun MediaCardUi.gridAspectRatio(showCovers: Boolean): Float {
    val hasPoster = posterPath != null
    val aspect = when {
        !showCovers -> 2f / 3f
        coverAspectRatio > 0f -> coverAspectRatio
        hasPoster -> 2f / 3f
        else -> 16f / 9f
    }
    return aspect.coerceIn(0.45f, 2.2f)
}

// Skeleton do grid: um mosaico de placeholders com pulse suave, usando o MESMO empacotamento
// do masonry — dá a sensação de que a grade está preenchendo (melhor que um "Carregando…").
@Composable
internal fun MediaGridSkeleton(
    showCovers: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )
    val shimmer = Color.White.copy(alpha = alpha)
    // Mosaico determinístico: mistura paisagem (16:9) e retrato (2:3); capas OFF → tudo retrato.
    val aspects = remember(showCovers) {
        if (!showCovers) List(24) { 2f / 3f }
        else listOf(
            16f / 9f, 2f / 3f, 2f / 3f, 16f / 9f, 2f / 3f, 16f / 9f,
            2f / 3f, 16f / 9f, 2f / 3f, 2f / 3f, 16f / 9f, 2f / 3f,
            16f / 9f, 2f / 3f, 2f / 3f, 16f / 9f, 2f / 3f, 2f / 3f
        )
    }

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            aspects.forEach { _ ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(shimmer)
                    )
                    if (!showCovers) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmer)
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val gapPx = GRID_GAP.roundToPx()
        val titlePx = if (showCovers) 0 else CARD_TITLE_H.roundToPx()
        val totalW = constraints.maxWidth
        val cols = columnsForWidthDp(totalW / density)
        val colW = ((totalW - gapPx * (cols - 1)) / cols).coerceAtLeast(1)
        val colHeights = IntArray(cols)
        val placed = ArrayList<Triple<Placeable, Int, Int>>(measurables.size)

        measurables.forEachIndexed { i, measurable ->
            val aspect = aspects[i].coerceIn(0.45f, 2.2f)
            val landscape = showCovers && aspect > 1.15f && cols >= 2
            val span = if (landscape) 2 else 1
            val wPx = if (span == 2) colW * 2 + gapPx else colW
            val hPx = (wPx / aspect).roundToInt() + titlePx

            val startCol = if (span == 1) {
                (0 until cols).minByOrNull { colHeights[it] } ?: 0
            } else {
                (0 until cols - 1).minByOrNull { maxOf(colHeights[it], colHeights[it + 1]) } ?: 0
            }
            val y = if (span == 1) colHeights[startCol]
                    else maxOf(colHeights[startCol], colHeights[startCol + 1])
            val x = startCol * (colW + gapPx)

            placed.add(Triple(measurable.measure(Constraints.fixed(wPx, hPx)), x, y))

            val bottom = y + hPx + gapPx
            if (span == 1) {
                colHeights[startCol] = bottom
            } else {
                colHeights[startCol] = bottom
                colHeights[startCol + 1] = bottom
            }
        }

        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else (colHeights.maxOrNull() ?: 0)
        val totalH = (colHeights.maxOrNull() ?: 0).coerceAtMost(maxH)
        layout(totalW, totalH) {
            placed.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}

private val FOCUS_ACCENT = com.ntv2.app.core.ui.BrandColors.Accent

/** Borda neutra do card sem foco (some conforme o foco chega). */
private fun DrawScope.drawIdleBorder(fraction: Float) {
    if (fraction >= 1f) return
    val line = 1.dp.toPx()
    drawRoundRect(
        color = Color(0x33FFFFFF).copy(alpha = 0x33 / 255f * (1f - fraction)),
        topLeft = androidx.compose.ui.geometry.Offset(line / 2, line / 2),
        size = androidx.compose.ui.geometry.Size(size.width - line, size.height - line),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx() - line / 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(line)
    )
}

@Composable
internal fun MediaCard(
    media: MediaCardUi,
    showCover: Boolean,
    cardLoadingStyle: CardLoadingStyle,
    animationsEnabled: Boolean,
    showPlaybackWarning: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Com pôster → capa retrato (9:16). Sem pôster (capas ON) → frame 16:9. Capas OFF → placeholder.
    val cover = if (showCover) (media.posterPath ?: media.thumbnailPath) else null
    val showTitle = !showCover || media.posterPath == null
    // Foco: aro verde (cor de destaque do app) com contorno escuro por dentro, desenhado POR CIMA
    // da capa. Só a borda branca sumia em pôsteres claros e o usuário se perdia.
    // Tudo que muda com o foco (aro, fundo) segue UMA fração animada 0..1. Antes o aro e o
    // fundo trocavam de uma vez: ao passar de um card para o outro havia
    // um instante com nenhum card em destaque (e o zIndex caía antes da hora),
    // o que dava a impressão de a grade tremer.
    val focusFraction by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) 140 else 0),
        label = "card-focus"
    )
    Box(
        modifier = modifier
            // Contínuo: sem "salto" de camada ao trocar o foco.
            .zIndex(focusFraction)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(androidx.compose.ui.graphics.lerp(Color(0x0FFFFFFF), Color(0x22FFFFFF), focusFraction))
            .drawWithContent {
                drawContent()
                drawIdleBorder(focusFraction)
                // Com animações ligadas o aro é o anel único que desliza pela grade (FocusGlideRing);
                // desligadas, cada card desenha o seu, na hora.
                if (!animationsEnabled && focusFraction > 0f) drawFocusRing(size, focusFraction)
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Capa (proporção da imagem) com barra de progresso e alerta opcional de reprodução.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(media.gridAspectRatio(showCover))
                    .background(Color(0xFF1C1C1C))
            ) {
                if (cover != null) {
                    com.ntv2.app.core.ui.CoverWithLoading(
                        cover = cover,
                        contentDescription = media.title,
                        style = cardLoadingStyle,
                        animationsEnabled = animationsEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Selos de resolução/duração removidos para não cobrir a arte das capas.

                if (media.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(media.progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                if (showPlaybackWarning) {
                    // Ícone neutro de alerta (aparelho pode não decodificar vídeo em alta resolução).
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC000000))
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Pode não reproduzir vídeo neste aparelho",
                            tint = Color(0xFFFFC857),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (showTitle) {
                Text(
                    media.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_TITLE_H)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Seletor de abas Filmes/Séries (segmented). Mostrado só quando o canal tem séries. */
@Composable
internal fun LibraryTabSelector(
    selected: com.ntv2.app.feature.media.presentation.state.LibraryTab,
    onSelect: (com.ntv2.app.feature.media.presentation.state.LibraryTab) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryTabChip(
            label = "Filmes",
            active = selected == com.ntv2.app.feature.media.presentation.state.LibraryTab.MOVIES,
            onClick = { onSelect(com.ntv2.app.feature.media.presentation.state.LibraryTab.MOVIES) }
        )
        LibraryTabChip(
            label = "Séries",
            active = selected == com.ntv2.app.feature.media.presentation.state.LibraryTab.SERIES,
            onClick = { onSelect(com.ntv2.app.feature.media.presentation.state.LibraryTab.SERIES) }
        )
    }
}

@Composable
private fun LibraryTabChip(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (active) FOCUS_ACCENT else Color(0x22FFFFFF))
            .then(if (focused && !active) Modifier.border(2.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) Color.Black else Color(0xFFDDDDDD),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Grade de séries (pôster 2:3). Lista pequena, carregada de uma vez do índice (sem paginação). */
@Composable
internal fun LazySeriesGrid(
    series: List<com.ntv2.app.feature.media.domain.SeriesSummary>,
    showCovers: Boolean,
    state: LazyStaggeredGridState,
    focusRequesterFor: (String) -> FocusRequester,
    onSeriesClick: (com.ntv2.app.feature.media.domain.SeriesSummary) -> Unit,
    onCardFocused: (String) -> Unit = {},
    cardLoadingStyle: CardLoadingStyle = CardLoadingStyle.DEFAULT,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Com o anel da tela (rail + grade juntos) usa o dela; senão, um próprio só da grade.
    val screenGlide = LocalFocusGlide.current
    val ownGlide = rememberFocusGlide(animationsEnabled && screenGlide == null)
    val glide = screenGlide ?: ownGlide
    BoxWithConstraints(modifier = modifier.focusGlideHost(ownGlide)) {
        val columns = columnsForWidthDp(maxWidth.value)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalItemSpacing = GRID_GAP
        ) {
            items(items = series, key = { "series_${it.tmdbId}" }) { s ->
                val id = "series_${s.tmdbId}"
                SeriesGridCard(
                    series = s,
                    showCover = showCovers,
                    cardLoadingStyle = cardLoadingStyle,
                    animationsEnabled = animationsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGlideTarget(id, glide)
                        .focusRequester(focusRequesterFor(id))
                        .onFocusChanged { if (it.isFocused) onCardFocused(id) },
                    onClick = { onSeriesClick(s) }
                )
            }
        }
        FocusGlideRing(ownGlide)
    }
}

@Composable
private fun SeriesGridCard(
    series: com.ntv2.app.feature.media.domain.SeriesSummary,
    showCover: Boolean,
    cardLoadingStyle: CardLoadingStyle,
    animationsEnabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val cover = if (showCover) series.posterUrl else null
    val focusFraction by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) 140 else 0),
        label = "series-card-focus"
    )
    Box(
        modifier = modifier
            .zIndex(focusFraction)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(Color(0x0FFFFFFF))
            .drawWithContent {
                drawContent()
                drawIdleBorder(focusFraction)
                // Animações ligadas: o aro é o anel único que desliza (FocusGlideRing).
                if (!animationsEnabled && focusFraction > 0f) drawFocusRing(size, focusFraction)
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color(0xFF1C1C1C))
            ) {
                if (cover != null) {
                    com.ntv2.app.core.ui.CoverWithLoading(
                        cover = cover,
                        contentDescription = series.title,
                        style = cardLoadingStyle,
                        animationsEnabled = animationsEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            // Sem capa (opção desligada ou série sem pôster), o nome aparece sob o placeholder,
            // igual ao card de filmes.
            if (series.posterUrl == null || !showCover) {
                Text(
                    series.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_TITLE_H)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}
