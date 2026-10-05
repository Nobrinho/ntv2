package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.tmdbAtWidth
import com.ntv2.app.core.ui.imageTiming
import com.ntv2.app.core.ui.fadeInUpStaggered
import com.ntv2.app.core.ui.slideInFromRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.domain.MovieDetails
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.SearchFilter
import com.ntv2.app.feature.media.presentation.state.SearchFilterKind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding

// Tela de detalhes do filme (Continuar/Recomeçar/Voltar).

internal val BRAND_ACCENT = com.ntv2.app.core.ui.BrandColors.Accent

// Tela de Detalhes (estilo Netflix/Prime): responsiva (TV/paisagem lado a lado, celular/retrato
// empilhado). O conteúdo aparece imediatamente; imagens secundárias carregam sem bloquear a tela.
@OptIn(ExperimentalFoundationApi::class) // LocalBringIntoViewSpec (rolagem por foco na TV)
@Composable
internal fun MovieDetailsOverlay(
    media: MediaCardUi,
    details: MovieDetails?,
    showCastPhotos: Boolean,
    lowRamPlaybackWarnings: Boolean,
    animationsEnabled: Boolean,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    showCovers: Boolean = true,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    isTv: Boolean = true,
    onReport: (MediaReportReason) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    recommendations: List<MediaCardUi> = emptyList(),
    onRecommendationClick: (MediaCardUi) -> Unit = {},
    onFilterClick: (SearchFilter) -> Unit = {}
) {
    BackHandler(enabled = true) { onDismiss() }
    var reporting by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }

    // O fundo é o banner do filme (nunca o pôster): só cai para a capa do card se não houver banner.
    // Com "Capas" desligadas nas configurações, a tela não exibe nenhuma imagem de capa/banner.
    val backdrop = if (!showCovers) null else details?.backdropPath?.let { tmdbAtWidth(it, "w780") }
        ?: media.posterPath ?: media.thumbnailPath
    // Entrada nº 4 (slide da direita): a arte de fundo entra quando termina de carregar, em vez de
    // simplesmente aparecer de um quadro para o outro.
    var backdropLoaded by remember(backdrop) { mutableStateOf(false) }
    val backdropTiming = imageTiming("fundo", backdrop)
    // Registra o que a tela TEM no instante em que abre: se o endereço já existe aqui e a imagem só
    // aparece 30 s depois, o atraso está no carregamento; se vier "sem imagem", está nos metadados.
    LaunchedEffect(media.mediaId, backdrop, details) {
        android.util.Log.i(
            "NtvImg",
            "detalhes ${media.mediaId}: fundo=${backdrop ?: "sem imagem"} " +
                "elenco=${details?.cast?.count { it.photoUrl != null } ?: 0} fotos"
        )
    }
    // Rolagem única da coluna de conteúdo: começa sempre no TOPO. Focar o botão "Continuar" (que
    // fica abaixo da sinopse/elenco) arrastava a rolagem para o meio ao abrir; após posicionar o
    // foco, trazemos a rolagem de volta ao topo.
    val contentScroll = rememberScrollState()
    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
        withFrameNanos { }
        withFrameNanos { }
        runCatching { contentScroll.scrollTo(0) }
    }

    // trapFocus: a grade continua composta por trás — o foco não pode escapar para ela.
    FocusGlideScope(Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF050505)).trapFocus()) {
        val portrait = maxHeight > maxWidth
        val compactLandscape = !portrait && maxHeight < 520.dp
        // Capturado aqui porque dentro do Column o receiver implícito passa a ser o ColumnScope.
        val screenHeight = maxHeight
        if (portrait) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    if (backdrop != null) {
                        AsyncImage(
                            model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                            onState = { backdropTiming(it); if (it.isDone()) backdropLoaded = true },
                            modifier = Modifier.fillMaxSize()
                                .slideInFromRight(backdropLoaded, animationsEnabled)
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0x00050505), 0.7f to Color(0x99050505), 1f to Color(0xFF050505))
                        )
                    )
                }
                DetailsInfo(
                    media, details, showCastPhotos, lowRamPlaybackWarnings, animationsEnabled,
                    playFocus, onPlay, onDismiss,
                    actionsFirst = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    playLoading = playLoading,
                    playFailed = playFailed,
                    onRestart = onRestart,
                    fillActions = !isTv,
                    onReportClick = { reporting = true },
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    recommendations = recommendations,
                    showCovers = showCovers,
                    onRecommendationClick = onRecommendationClick,
                    onFilterClick = onFilterClick
                )
            }
        } else {
            // TV: herói (backdrop + infos à esquerda) e ABAIXO uma fileira de recomendações (gêneros
            // misturados, como no celular) na largura toda. O herói deixa o topo das capas aparecendo
            // no rodapé da tela, sinalizando que há mais conteúdo ao rolar.
            val showRecommendations = recommendations.isNotEmpty()
            val heroMinHeight = if (showRecommendations) screenHeight - RECOMMENDATIONS_PEEK else screenHeight
            val scope = rememberCoroutineScope()
            var heroHeightPx by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            // A rolagem por foco padrão da TV reposiciona a tela a cada movimento (mantém o item focado
            // num ponto fixo): andar de Assistir para o botão ao lado já rolava para baixo. Aqui só rola
            // quando o item focado não está inteiro na tela — na prática, ao descer para "Porque você viu".
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember(density) { ScrollOnlyIfHidden(with(density) { 16.dp.toPx() }) }) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = heroMinHeight)
                        .onSizeChanged { heroHeightPx = it.height }
                ) {
                    if (backdrop != null) {
                        AsyncImage(
                            model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                            onState = { backdropTiming(it); if (it.isDone()) backdropLoaded = true },
                            modifier = Modifier.matchParentSize()
                                .slideInFromRight(backdropLoaded, animationsEnabled)
                        )
                    }
                    Box(modifier = Modifier.matchParentSize().background(
                        // Escurece até onde vai a coluna de informações (80% da largura), para o texto
                        // continuar legível sobre o banner.
                        Brush.horizontalGradient(0f to Color(0xF2050505), 0.6f to Color(0xB3050505), 0.95f to Color(0x00050505))
                    ))
                    Box(modifier = Modifier.matchParentSize().background(
                        Brush.verticalGradient(0f to Color(0x00050505), 0.55f to Color(0x66050505), 1f to Color(0xF2050505))
                    ))
                    // Pista de que há mais abaixo: some quando a rolagem desce até as recomendações.
                    if (showRecommendations) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                                .graphicsLayer { alpha = (1f - contentScroll.value / 120f).coerceIn(0f, 1f) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFB7BBC4), modifier = Modifier.size(20.dp))
                            Text("Mais abaixo: recomendados", color = Color(0xFFB7BBC4), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DetailsInfo(
                        media, details, showCastPhotos, lowRamPlaybackWarnings, animationsEnabled,
                        playFocus, onPlay, onDismiss,
                        actionsFirst = compactLandscape,
                        // 80% da largura: com 62% os chips e os botões (Continuar + Recomeçar + Voltar)
                        // eram cortados à direita mesmo com espaço sobrando na tela.
                        modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.TopStart)
                            .padding(start = 48.dp, end = 24.dp, top = 32.dp, bottom = 44.dp),
                        playLoading = playLoading,
                        playFailed = playFailed,
                        onRestart = onRestart,
                        onReportClick = { reporting = true },
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        // As recomendações vão nas fileiras full-width abaixo (não inline na coluna).
                        showInlineRecommendations = false,
                        onRecommendationClick = onRecommendationClick,
                        onFilterClick = onFilterClick
                    )
                }
                if (showRecommendations) {
                    PosterTrackRow(
                        label = recommendationsHeading(details?.title ?: media.title),
                        items = recommendations,
                        showCovers = showCovers,
                        useTvLayout = true,
                        onCardClick = onRecommendationClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Saindo das recomendações (de volta ao herói): rola ao topo, se o herói cabe
                            // na tela; senão fica a rolagem mínima para mostrar o item focado. O grupo é
                            // SÓ desta fileira: um grupo no herói (tela toda) cobria o botão voltar e o
                            // D-pad não achava mais nada abaixo/à esquerda dele.
                            .onFocusChanged { focus ->
                                if (!focus.hasFocus && contentScroll.value > 0 && heroHeightPx <= contentScroll.viewportSize) {
                                    scope.launch { contentScroll.animateScrollTo(0) }
                                }
                            }
                            .focusGroup()
                            .background(Color(0xFF050505))
                            .padding(start = 48.dp, end = 32.dp, top = 6.dp, bottom = 44.dp)
                    )
                }
            }
            }
        }
        // TV: voltar só com ícone no canto superior direito (mesmo padrão dos detalhes de série),
        // fora da linha de ações. Focável ao subir com o D-pad; o foco inicial fica no Assistir.
        if (isTv) {
            BackChip(
                onDismiss,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
                    // Sai do canto direto para o Assistir (↓ ou ←), sem depender da busca de foco.
                    .focusProperties {
                        down = playFocus
                        left = playFocus
                    }
            )
        }
        // Celular: fechar pelo X no canto superior esquerdo (mesmo padrão do player), em vez do
        // botão "Voltar" na linha de ações — que não cabia ao lado de Continuar e Recomeçar.
        if (!isTv) {
            DetailsCloseButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(12.dp)
            )
        }
        if (reporting) {
            ReportMediaDialog(
                title = details?.title ?: media.title,
                onReport = onReport,
                onDismiss = {
                    reporting = false
                    runCatching { playFocus.requestFocus() }
                }
            )
        }
    }
    }
}

@Composable
internal fun DetailsCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x80000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

internal fun coil.compose.AsyncImagePainter.State.isDone(): Boolean =
    this is coil.compose.AsyncImagePainter.State.Success || this is coil.compose.AsyncImagePainter.State.Error

@Composable
internal fun DetailsInfo(
    media: MediaCardUi,
    details: MovieDetails?,
    showCastPhotos: Boolean,
    lowRamPlaybackWarnings: Boolean,
    animationsEnabled: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    actionsFirst: Boolean,
    modifier: Modifier = Modifier,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    fillActions: Boolean = false,
    onReportClick: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    recommendations: List<MediaCardUi> = emptyList(),
    onRecommendationClick: (MediaCardUi) -> Unit = {},
    showInlineRecommendations: Boolean = true,
    showCovers: Boolean = true,
    onFilterClick: (SearchFilter) -> Unit = {}
) {
    val title = details?.title ?: media.title
    val durationSecs = details?.durationSeconds?.takeIf { it > 0 } ?: media.durationSeconds
    val showPlaybackWarning = lowRamPlaybackWarnings && media.needsLowRamPlaybackWarning()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        details?.originalTitle?.takeIf { it.isNotBlank() && it != title }?.let {
            Text(it, color = Color(0xFFC9C9C9), style = MaterialTheme.typography.titleMedium)
        }
        DetailMetaRow(details, durationSecs, onFilterClick)
        if (showPlaybackWarning) {
            Text(
                text = "Este aparelho pode tocar apenas o som em vídeos 1080p. Prefira versão 720p quando disponível.",
                color = Color(0xFFFFD37A),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FFC857))
                    .border(1.dp, Color(0x66FFC857), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
        if (actionsFirst) {
            DetailsActionRow(
                media, showPlaybackWarning, playFocus, onPlay, onDismiss, playLoading, playFailed, onRestart,
                fillWidth = fillActions,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onReportClick = onReportClick
            )
            if (isFavorite) InListLabel()
        }
        details?.synopsis?.takeIf { it.isNotBlank() }?.let { SynopsisText(it) }
        details?.director?.takeIf { it.isNotBlank() }?.let {
            Text("Diretor: $it", color = Color(0xFFB6B6B6), style = MaterialTheme.typography.bodySmall)
        }

        val cast = details?.cast.orEmpty()
        if (cast.isNotEmpty()) {
            if (showCastPhotos) {
                Row(
                    // Padding evita cortar o aro de foco/zoom nas bordas; focusGroup mantém o D-pad
                    // dentro da fileira (←/→ andam entre os atores).
                    modifier = Modifier.focusGroup().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Entrada nº 11 (stagger): os rostos sobem em sequência, um pouco depois do
                    // anterior, em vez de a fileira inteira piscar junto.
                    var castVisible by remember(cast) { mutableStateOf(false) }
                    LaunchedEffect(cast) { castVisible = true }
                    cast.forEachIndexed { index, c ->
                        var actorFocused by remember { mutableStateOf(false) }
                        val gliding = glideActive()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // Tamanho único para todos: foto 64dp + nome em exatamente 2 linhas.
                            modifier = Modifier.width(92.dp)
                                .fadeInUpStaggered(castVisible, index, animationsEnabled)
                                .glideTarget(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .onFocusChanged { actorFocused = it.isFocused }
                                .clickable { onFilterClick(SearchFilter(SearchFilterKind.ACTOR, c.name)) }
                                .then(if (actorFocused && !gliding) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (c.photoUrl != null) {
                                val castTiming = imageTiming("elenco#$index", c.photoUrl)
                                AsyncImage(
                                    model = c.photoUrl, contentDescription = c.name, contentScale = ContentScale.Crop,
                                    onState = castTiming,
                                    modifier = Modifier.size(64.dp).clip(CircleShape).background(colorForTitle(c.name))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape).background(colorForTitle(c.name)),
                                    contentAlignment = Alignment.Center
                                ) { Text(initialFor(c.name), color = Color.White, style = MaterialTheme.typography.titleMedium) }
                            }
                            Text(
                                c.name,
                                color = if (actorFocused) Color.White else Color(0xFFDCDCDC),
                                style = MaterialTheme.typography.labelMedium,
                                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cast.forEach { c ->
                        MetaChip(c.name) { onFilterClick(SearchFilter(SearchFilterKind.ACTOR, c.name)) }
                    }
                }
            }
        }

        if (!actionsFirst) {
            DetailsActionRow(
                media, showPlaybackWarning, playFocus, onPlay, onDismiss, playLoading, playFailed, onRestart,
                fillWidth = fillActions,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onReportClick = onReportClick
            )
            if (isFavorite) InListLabel()
        }

        if (showInlineRecommendations && recommendations.isNotEmpty()) {
            RecommendationsRow(
                seedTitle = title,
                items = recommendations,
                showCovers = showCovers,
                onClick = onRecommendationClick
            )
        }
    }
}

/**
 * Rolagem por foco mínima: só rola se o item focado não estiver inteiro na área visível. A área do item
 * é ampliada por [marginPx] (afastamento + traço do anel de foco, que é desenhado FORA do item), para o
 * anel nunca ser cortado na borda da tela.
 */
@OptIn(ExperimentalFoundationApi::class)
private class ScrollOnlyIfHidden(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = offset - marginPx
        val end = offset + size + marginPx
        return when {
            start >= 0f && end <= containerSize -> 0f // já visível (com folga do anel)
            end - start > containerSize -> start // maior que a tela: alinha o topo
            start < 0f -> start // acima: sobe o necessário
            else -> end - containerSize // abaixo: desce o necessário
        }
    }
}

/** Quanto das recomendações aparece no rodapé da TV antes de rolar: o título e o topo das capas. */
private val RECOMMENDATIONS_PEEK = 170.dp

private fun recommendationsHeading(seedTitle: String) = buildAnnotatedString {
    append("Porque você viu ")
    withStyle(SpanStyle(color = BRAND_ACCENT)) { append(seedTitle) }
}

/** "Porque você viu <título>": trilha de pôsteres recomendados dentro dos Detalhes. */
@Composable
internal fun RecommendationsRow(
    seedTitle: String,
    items: List<MediaCardUi>,
    showCovers: Boolean,
    onClick: (MediaCardUi) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(recommendationsHeading(seedTitle), color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { rec ->
                RecommendationPoster(media = rec, showCovers = showCovers, onClick = { onClick(rec) })
            }
        }
    }
}

@Composable
private fun RecommendationPoster(media: MediaCardUi, showCovers: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val cover = if (showCovers) media.posterPath ?: media.thumbnailPath else null
    Box(
        modifier = Modifier
            .width(104.dp)
            .aspectRatio(2f / 3f)
            .glideTarget(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(Color(0xFF1C1C20))
            .then(if (focused && !gliding) Modifier.border(3.dp, BRAND_ACCENT, RoundedCornerShape(12.dp)) else Modifier)
    ) {
        if (cover != null) {
            AsyncImage(model = cover, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                media.title,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
    }
}

// Sinopse: 2 linhas; focável pelo D-pad e OK expande/recolhe o texto completo.
@Composable
internal fun SynopsisText(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Column(
        modifier = Modifier
            .glideTarget(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { expanded = !expanded }
            .background(if (focused) Color(0x1FFFFFFF) else Color.Transparent)
            .then(if (focused && !gliding) Modifier.border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text,
            color = Color(0xFFDCDCDC),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        // A dica ocupa a linha SEMPRE (só fica transparente sem foco): se ela entrasse e saísse da
        // composição, a altura do conteúdo mudava a cada foco e o fundo (Crop) dava zoom junto.
        Text(
            if (expanded) "OK para recolher" else "OK para ler tudo",
            color = if (focused) Color(0xFF9A9A9A) else Color.Transparent,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun DetailsActionRow(
    media: MediaCardUi,
    showPlaybackWarning: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    // Celular em pé: os botões dividem a largura toda (nunca cortam na lateral).
    fillWidth: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onReportClick: () -> Unit = {}
) {
    val playLabel = when {
        playLoading -> "Abrindo…"
        playFailed -> "Falhou — tentar de novo"
        showPlaybackWarning -> "Tentar assistir"
        media.progress > 0f -> continueLabel(media)
        else -> "Assistir"
    }
    // Vídeo parado no meio: "Recomeçar" zera o progresso e toca do início (útil também quando
    // o download a partir do ponto salvo não anda).
    val showRestart = media.progress > 0f && !playLoading

    // Celular em pé com Continuar + Recomeçar: não cabem os dois ao lado dos ícones sem quebrar o
    // texto letra a letra. O botão principal ocupa a linha de cima inteira; o resto vai embaixo.
    if (fillWidth && showRestart) {
        Column(
            modifier = Modifier.focusGroup().fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailButton(
                icon = Icons.Filled.PlayArrow,
                label = playLabel,
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(playFocus),
                loading = playLoading,
                onClick = onPlay
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailButton(icon = Icons.Filled.Replay, label = "Recomeçar", primary = false, onClick = onRestart, modifier = Modifier.weight(1f))
                FavoriteToggleButton(isFavorite = isFavorite, onClick = onToggleFavorite)
                DetailIconButton(icon = Icons.Filled.Flag, description = "Reportar problema", onClick = onReportClick)
            }
        }
        return
    }

    Row(
        modifier = Modifier.focusGroup().then(if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val share = if (fillWidth) Modifier.weight(1f) else Modifier
        DetailButton(
            icon = Icons.Filled.PlayArrow,
            label = playLabel,
            primary = true,
            modifier = share.focusRequester(playFocus),
            loading = playLoading,
            onClick = onPlay
        )
        if (showRestart) {
            DetailButton(icon = Icons.Filled.Replay, label = "Recomeçar", primary = false, onClick = onRestart, modifier = share)
        }
        // Coração e reportar: quadrados só-ícone ao lado das ações (como no protótipo).
        FavoriteToggleButton(isFavorite = isFavorite, onClick = onToggleFavorite)
        DetailIconButton(icon = Icons.Filled.Flag, description = "Reportar problema", onClick = onReportClick)
    }
}

/** "Continuar • 1h27" — o tempo já assistido, como no protótipo. */
private fun continueLabel(media: MediaCardUi): String {
    if (media.durationSeconds <= 0) return "Continuar"
    val watched = (media.durationSeconds * media.progress).toInt()
    if (watched <= 0) return "Continuar"
    return "Continuar • ${durationLabel(watched)}"
}

/** Metadados no estilo do protótipo: nota verde com estrela + chips com borda (idade/qualidade/gênero).
 *  Ano e gêneros são clicáveis: listam as mídias do canal com esse ano/gênero. */
@Composable
private fun DetailMetaRow(details: MovieDetails?, durationSecs: Int, onFilterClick: (SearchFilter) -> Unit) {
    // Linha 1: informativos (não clicáveis). Linha 2, exclusiva: ano e gêneros (clicáveis).
    val bullet = Regex("\\s*,\\s*")
    val genres = details?.genres?.split(',', ';', '/', '•')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            details?.rating?.let { rating ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = BRAND_ACCENT, modifier = Modifier.size(15.dp))
                    Text("%.1f".format(rating), color = BRAND_ACCENT, style = MaterialTheme.typography.titleSmall)
                }
            }
            if (durationSecs > 0) MetaText(durationLabel(durationSecs))
            details?.ageRating?.takeIf { it.isNotBlank() }?.let { MetaChip(it.trim().replace(bullet, " • ")) }
            details?.quality?.takeIf { it.isNotBlank() }?.let { MetaChip(it.trim().replace(bullet, " • ")) }
        }
        if (details?.year != null || genres.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                details?.year?.let { year ->
                    MetaText(year.toString()) { onFilterClick(SearchFilter(SearchFilterKind.YEAR, year.toString())) }
                }
                genres.forEach { genre ->
                    MetaChip(genre) { onFilterClick(SearchFilter(SearchFilterKind.GENRE, genre)) }
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        Text(text, color = Color(0xFF8A8A8A), style = MaterialTheme.typography.titleSmall)
        return
    }
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Text(
        text,
        color = if (focused) Color.White else Color(0xFF8A8A8A),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .glideTarget(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .then(if (focused && !gliding) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

/** Chip com borda; com [onClick] é focável (D-pad) e clicável (toque). */
@Composable
internal fun MetaChip(text: String, onClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val ring = focused && !gliding
    Text(
        text,
        color = if (focused) Color.White else Color(0xFFCFCFCF),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .then(if (onClick != null) Modifier.glideTarget(20.dp) else Modifier)
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick) else Modifier)
            .border(if (ring) 2.dp else 1.dp, if (ring) Color.White else Color(0xFF33343A), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Linha "Na sua lista" com check verde, exibida sob as ações quando favoritado. */
@Composable
internal fun InListLabel() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = BRAND_ACCENT, modifier = Modifier.size(15.dp))
        Text("Na sua lista", color = BRAND_ACCENT, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botão quadrado só-ícone da linha de ações (reportar), no mesmo estilo do favoritar. */
@Composable
internal fun DetailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Box(
        modifier = Modifier
            .size(48.dp)
            .glideTarget(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(Color(0x1FFFFFFF))
            .then(
                if (focused && !gliding) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (focused) Color.White else Color(0xFFB0B0B0), modifier = Modifier.size(22.dp))
    }
}

/** Botão circular de favoritar (coração preenchido = na lista). */
@Composable
internal fun FavoriteToggleButton(isFavorite: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val border = if (isFavorite) BRAND_ACCENT else Color(0x33FFFFFF)
    Box(
        modifier = Modifier
            .size(48.dp)
            .glideTarget(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (isFavorite) Color(0x24B8C8E0) else Color(0x1FFFFFFF))
            .border(
                width = if (focused && !gliding) 2.dp else 1.dp,
                color = if (focused && !gliding) Color.White else border,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "Remover da minha lista" else "Adicionar à minha lista",
            tint = if (isFavorite) BRAND_ACCENT else Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun DetailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val bg = when {
        primary -> com.ntv2.app.core.ui.BrandColors.Cta
        focused -> Color(0x33FFFFFF)
        else -> Color(0x1FFFFFFF)
    }
    val content = if (primary) com.ntv2.app.core.ui.BrandColors.OnCta else Color.White
    Row(
        modifier = modifier
            .glideTarget(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            // Enquanto carrega, ignora novos toques (evita disparos duplicados).
            .clickable(enabled = !loading, onClick = onClick)
            .background(bg)
            .then(if (focused && !gliding) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(label, color = content, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
