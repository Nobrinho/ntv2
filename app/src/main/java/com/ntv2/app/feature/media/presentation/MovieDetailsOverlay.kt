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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ntv2.app.feature.media.presentation.trailer.DetailsTrailerBackdrop
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.BrandShapes
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.feature.media.domain.MovieDetails
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.SearchFilter
import com.ntv2.app.feature.media.presentation.state.SearchFilterKind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import com.ntv2.app.core.ui.icons.NbrIcons

// Tela de detalhes do filme (Continuar/Recomeçar/Voltar), no visual do protótipo:
// celular 14 (detalhe), 15 (em andamento), 20 (abrindo e falha) e TV 09 (detalhe).

internal val BRAND_ACCENT = BrandColors.Accent

/** Altura da capa no celular (protótipo: Hero 390x330, atrás da barra de status). */
private val PHONE_HERO_HEIGHT = 330.dp

/** Texto do aviso de falha ao abrir (tela 20 do protótipo). */
private const val PLAY_FAILED_NOTICE = "Não foi possível abrir o vídeo. Verifique a internet e tente de novo."

// Tela de Detalhes: responsiva (TV/paisagem com informações à esquerda e elenco à direita; celular em
// pé com capa no topo e conteúdo empilhado). O conteúdo aparece imediatamente; imagens secundárias
// carregam sem bloquear a tela.
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
    playFailedText: String? = null,
    onRestart: () -> Unit = {},
    isTv: Boolean = true,
    onReport: ReportSender = { _, _, _ -> },
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    recommendations: List<MediaCardUi> = emptyList(),
    onRecommendationClick: (MediaCardUi) -> Unit = {},
    onFilterClick: (SearchFilter) -> Unit = {},
    // Configuração local + flag remota já combinadas (MediaLibraryUiState.trailerPreviewEnabled).
    trailerPreviewEnabled: Boolean = false
) {
    BackHandler(enabled = true) { onDismiss() }
    var reporting by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    // Botão "Trailer": tela cheia com som. Só aparece com link do YouTube reconhecido.
    val trailerVideoId = remember(details?.trailerUrl) {
        com.ntv2.app.feature.media.presentation.trailer.YouTubeIds.videoId(details?.trailerUrl)
    }
    var trailerOpen by remember(media.mediaId) { mutableStateOf(false) }
    // Ao fechar a tela cheia o foco volta ao botão principal. Pedido um quadro depois: enquanto a
    // tela cheia existe, o foco preso nela recusa o pedido e ia parar no "Voltar".
    var restoreFocusAfterTrailer by remember { mutableStateOf(false) }
    LaunchedEffect(restoreFocusAfterTrailer) {
        if (restoreFocusAfterTrailer) {
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
            restoreFocusAfterTrailer = false
        }
    }
    val onTrailerClick: (() -> Unit)? = trailerVideoId?.let { { trailerOpen = true } }

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
    // Rolagem única da coluna de conteúdo: começa sempre no TOPO. Focar o botão "Continuar" arrastava
    // a rolagem para o meio ao abrir; após posicionar o foco, trazemos a rolagem de volta ao topo.
    val contentScroll = rememberScrollState()
    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
        withFrameNanos { }
        withFrameNanos { }
        runCatching { contentScroll.scrollTo(0) }
    }

    // trapFocus: a grade continua composta por trás — o foco não pode escapar para ela.
    FocusGlideScope(Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BrandColors.Background).trapFocus()) {
        val portrait = maxHeight > maxWidth
        val compactLandscape = !portrait && maxHeight < 520.dp
        // Capturado aqui porque dentro do Column o receiver implícito passa a ser o ColumnScope.
        val screenHeight = maxHeight
        if (portrait) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(contentScroll)) {
                // Capa de 330 dp atrás da barra de status; o título fica sobre o degradê do rodapé dela.
                Box(modifier = Modifier.fillMaxWidth().height(PHONE_HERO_HEIGHT)) {
                    if (backdrop != null) {
                        AsyncImage(
                            model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                            onState = { backdropTiming(it); if (it.isDone()) backdropLoaded = true },
                            modifier = Modifier.fillMaxSize()
                                .slideInFromRight(backdropLoaded, animationsEnabled)
                        )
                        // Prévia do trailer por cima da imagem e por baixo do degradê; a imagem fica como reserva.
                        DetailsTrailerBackdrop(
                            trailerUrl = details?.trailerUrl,
                            // "Assistir" apertado ou trailer em tela cheia: sai a prévia (o WebView é destruído).
                            trailerPreviewEnabled = trailerPreviewEnabled && !playLoading && !trailerOpen,
                            showCovers = showCovers,
                            imageLoaded = backdropLoaded,
                            animationsEnabled = animationsEnabled,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(118.dp).background(
                            Brush.verticalGradient(0f to Color(0x00090B0F), 1f to BrandColors.Background)
                        )
                    )
                    DetailHeader(
                        media = media,
                        details = details,
                        tv = false,
                        onFilterClick = onFilterClick,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                    )
                }
                DetailsInfo(
                    media, details, showCastPhotos, lowRamPlaybackWarnings, animationsEnabled,
                    playFocus, onPlay, onDismiss,
                    actionsFirst = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                    playLoading = playLoading,
                    playFailed = playFailed,
                    playFailedText = playFailedText,
                    onRestart = onRestart,
                    tv = false,
                    headerInHero = true,
                    onReportClick = { reporting = true },
                    onTrailerClick = onTrailerClick,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    recommendations = recommendations,
                    showCovers = showCovers,
                    onRecommendationClick = onRecommendationClick,
                    onFilterClick = onFilterClick
                )
            }
        } else {
            // TV: herói (backdrop + infos à esquerda + elenco à direita) e ABAIXO uma fileira de
            // recomendações na largura toda. O herói deixa o topo das capas aparecendo no rodapé da tela,
            // sinalizando que há mais conteúdo ao rolar.
            val showRecommendations = recommendations.isNotEmpty()
            val heroMinHeight = if (showRecommendations) screenHeight - RECOMMENDATIONS_PEEK else screenHeight
            val scope = rememberCoroutineScope()
            var heroHeightPx by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val cast = details?.cast.orEmpty()
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
                        // Prévia do trailer por cima da imagem e por baixo do degradê; a imagem fica como reserva.
                        DetailsTrailerBackdrop(
                            trailerUrl = details?.trailerUrl,
                            // "Assistir" apertado ou trailer em tela cheia: sai a prévia (o WebView é destruído).
                            trailerPreviewEnabled = trailerPreviewEnabled && !playLoading && !trailerOpen,
                            showCovers = showCovers,
                            imageLoaded = backdropLoaded,
                            animationsEnabled = animationsEnabled,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    // Degradê do protótipo (da esquerda: 98% -> 80% em 45% -> 20%) e um escurecimento no
                    // rodapé para as recomendações. Um nó só, com os degradês em cache.
                    Box(modifier = Modifier.matchParentSize().drawWithCache {
                        val h = Brush.horizontalGradient(0f to Color(0xFA090B0F), 0.45f to Color(0xCC090B0F), 1f to Color(0x33090B0F))
                        val v = Brush.verticalGradient(0f to Color(0x00090B0F), 0.7f to Color(0x00090B0F), 1f to Color(0xE6090B0F))
                        onDrawBehind {
                            drawRect(h)
                            drawRect(v)
                        }
                    })
                    // Pista de que há mais abaixo: some quando a rolagem desce até as recomendações.
                    if (showRecommendations) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                                .graphicsLayer { alpha = (1f - contentScroll.value / 120f).coerceIn(0f, 1f) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(NbrIcons.ChevronDown, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(20.dp))
                            Text("Mais abaixo: recomendados", color = BrandColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // Protótipo 09: padding 72/96 px e 96 px entre a coluna de informações e o elenco.
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                            .padding(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(48.dp)
                    ) {
                        DetailsInfo(
                            media, details, showCastPhotos, lowRamPlaybackWarnings, animationsEnabled,
                            playFocus, onPlay, onDismiss,
                            actionsFirst = compactLandscape,
                            modifier = Modifier.weight(1f),
                            playLoading = playLoading,
                            playFailed = playFailed,
                            playFailedText = playFailedText,
                            onRestart = onRestart,
                            tv = true,
                            // O elenco vai na coluna da direita (protótipo 09).
                            showCast = false,
                            onReportClick = { reporting = true },
                            onTrailerClick = onTrailerClick,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            // As recomendações vão nas fileiras full-width abaixo (não inline na coluna).
                            showInlineRecommendations = false,
                            onRecommendationClick = onRecommendationClick,
                            onFilterClick = onFilterClick
                        )
                        if (cast.isNotEmpty()) {
                            MovieDetailCastColumn(
                                cast = cast,
                                showPhotos = showCastPhotos,
                                animationsEnabled = animationsEnabled,
                                onFilterClick = onFilterClick,
                                modifier = Modifier.width(260.dp).heightIn(max = (screenHeight - 120.dp).coerceAtLeast(160.dp))
                            )
                        }
                    }
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
                            .background(BrandColors.Background)
                            .padding(start = 48.dp, end = 32.dp, top = 6.dp, bottom = 44.dp)
                    )
                }
            }
            }
        }
        // Celular: voltar redondo de 48 dp no canto superior esquerdo, sobre a capa (protótipo 14).
        if (portrait) {
            MovieDetailRoundButton(
                icon = NbrIcons.ChevronLeft,
                description = "Voltar",
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(start = 16.dp, top = 4.dp)
            )
        }
        if (trailerOpen && trailerVideoId != null) {
            com.ntv2.app.feature.media.presentation.trailer.TrailerFullscreen(
                videoId = trailerVideoId,
                onClose = {
                    trailerOpen = false
                    restoreFocusAfterTrailer = true
                }
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

internal fun coil.compose.AsyncImagePainter.State.isDone(): Boolean =
    this is coil.compose.AsyncImagePainter.State.Success || this is coil.compose.AsyncImagePainter.State.Error

/** Título (ou logo), título original e a linha "ano · gêneros · duração". */
@Composable
private fun DetailHeader(
    media: MediaCardUi,
    details: MovieDetails?,
    tv: Boolean,
    onFilterClick: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = details?.title ?: media.title
    val durationSecs = details?.durationSeconds?.takeIf { it > 0 } ?: media.durationSeconds
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (tv) 10.dp else 6.dp)) {
        DetailTitle(title, details?.logoPath, tv)
        details?.originalTitle?.takeIf { it.isNotBlank() && it != title }?.let {
            Text(it, color = BrandColors.TextSecondary, style = NbrType.BodyLarge)
        }
        if (tv) {
            // TV: a linha de texto e os chips na mesma fileira (protótipo 09).
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailMetaLine(details, durationSecs, tv = true, onFilterClick = onFilterClick)
                DetailChips(details, media.partCount, tv = true)
            }
        } else {
            DetailMetaLine(details, durationSecs, tv = false, onFilterClick = onFilterClick)
        }
    }
}

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
    playFailedText: String? = null,
    onRestart: () -> Unit = {},
    tv: Boolean = true,
    /** Celular: título e linha de metadados já estão sobre a capa; aqui ficam só os chips. */
    headerInHero: Boolean = false,
    showCast: Boolean = true,
    onReportClick: () -> Unit = {},
    onTrailerClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    recommendations: List<MediaCardUi> = emptyList(),
    onRecommendationClick: (MediaCardUi) -> Unit = {},
    showInlineRecommendations: Boolean = true,
    showCovers: Boolean = true,
    onFilterClick: (SearchFilter) -> Unit = {}
) {
    val title = details?.title ?: media.title
    val showPlaybackWarning = lowRamPlaybackWarnings && media.needsLowRamPlaybackWarning()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (tv) 14.dp else 18.dp)) {
        if (tv) {
            // "‹ Voltar" no topo da coluna (protótipo 09); ↓ vai direto para o Assistir.
            MovieDetailBackLink(onDismiss, Modifier.focusProperties { down = playFocus })
        }
        if (!headerInHero) {
            DetailHeader(media, details, tv, onFilterClick)
        } else if (hasChips(details, media.partCount)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { DetailChips(details, media.partCount, tv = false) }
        }
        if (showPlaybackWarning) {
            Text(
                text = "Este aparelho pode tocar apenas o som em vídeos 1080p. Prefira versão 720p quando disponível.",
                color = BrandColors.Warning,
                style = NbrType.Caption,
                modifier = Modifier
                    .clip(BrandShapes.Md)
                    .background(BrandColors.Warning.copy(alpha = 0.10f))
                    .border(1.dp, BrandColors.Warning.copy(alpha = 0.4f), BrandShapes.Md)
                    .padding(12.dp)
            )
        }
        val actions: @Composable () -> Unit = {
            DetailsActionRow(
                media, showPlaybackWarning, playFocus, onPlay, playLoading, playFailed, onRestart,
                tv = tv,
                playFailedText = playFailedText,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onReportClick = onReportClick,
                onTrailerClick = onTrailerClick
            )
            if (isFavorite) InListLabel()
        }
        if (actionsFirst) actions()
        details?.synopsis?.takeIf { it.isNotBlank() }?.let { SynopsisText(it, tv) }
        details?.director?.takeIf { it.isNotBlank() }?.let {
            Text("Diretor: $it", color = BrandColors.TextMuted, style = if (tv) NbrType.Caption else NbrType.Secondary)
        }

        val cast = details?.cast.orEmpty()
        if (showCast && cast.isNotEmpty()) {
            Text("Elenco", color = MovieDetailTextPrimary, style = NbrType.Section.copy(fontSize = 18.sp))
            if (showCastPhotos) {
                Row(
                    // focusGroup mantém o D-pad dentro da fileira (←/→ andam entre os atores).
                    modifier = Modifier.focusGroup().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                            // Protótipo: coluna de 64 dp, foto redonda de 56 e nome embaixo (6 dp).
                            modifier = Modifier.width(64.dp)
                                .fadeInUpStaggered(castVisible, index, animationsEnabled)
                                .glideTarget(8.dp)
                                .clip(BrandShapes.Sm)
                                .onFocusChanged { actorFocused = it.isFocused }
                                .clickable { onFilterClick(SearchFilter(SearchFilterKind.ACTOR, c.name)) }
                                .then(if (actorFocused && !gliding) Modifier.border(3.dp, BrandColors.Accent, BrandShapes.Sm) else Modifier),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CastPhoto(c.name, c.photoUrl, index, 56.dp)
                            Text(
                                c.name,
                                color = if (actorFocused) Color.White else BrandColors.TextSecondary,
                                style = NbrType.Caption.copy(fontSize = 11.sp),
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
                        MovieDetailChip(c.name, tv = tv) { onFilterClick(SearchFilter(SearchFilterKind.ACTOR, c.name)) }
                    }
                }
            }
        }

        if (!actionsFirst) actions()

        // Tela 20: aviso de falha sob o conteúdo, além do botão vermelho.
        if (playFailed && !playLoading) {
            MovieDetailFailureNotice(PLAY_FAILED_NOTICE, Modifier.fillMaxWidth())
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

/** Foto redonda do ator (ou a inicial sobre a cor do nome, quando não há foto). */
@Composable
private fun CastPhoto(name: String, photoUrl: String?, index: Int, size: androidx.compose.ui.unit.Dp) {
    if (photoUrl != null) {
        val castTiming = imageTiming("elenco#$index", photoUrl)
        AsyncImage(
            model = tmdbAtWidth(photoUrl, "w185"), contentDescription = name, contentScale = ContentScale.Crop,
            onState = castTiming,
            modifier = Modifier.size(size).clip(CircleShape).background(colorForTitle(name))
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(colorForTitle(name)),
            contentAlignment = Alignment.Center
        ) { Text(initialFor(name), color = Color.White, style = NbrType.ItemTitle) }
    }
}

/** TV (protótipo 09): "Elenco" e a lista vertical de atores (foto de 40 dp + nome) à direita. */
@Composable
private fun MovieDetailCastColumn(
    cast: List<com.ntv2.app.core.telegram.media.CastMemberMeta>,
    showPhotos: Boolean,
    animationsEnabled: Boolean,
    onFilterClick: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Elenco", color = MovieDetailTextPrimary, style = NbrType.Section.copy(fontSize = 16.sp))
        var castVisible by remember(cast) { mutableStateOf(false) }
        LaunchedEffect(cast) { castVisible = true }
        Column(
            modifier = Modifier.focusGroup().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cast.forEachIndexed { index, c ->
                var focused by remember { mutableStateOf(false) }
                val ring = focused && !glideActive()
                Row(
                    modifier = Modifier
                        .fadeInUpStaggered(castVisible, index, animationsEnabled)
                        .glideTarget(8.dp)
                        .clip(BrandShapes.Sm)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onFilterClick(SearchFilter(SearchFilterKind.ACTOR, c.name)) }
                        .then(if (ring) Modifier.border(3.dp, BrandColors.Accent, BrandShapes.Sm) else Modifier)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPhotos) CastPhoto(c.name, c.photoUrl, index, 40.dp)
                    Text(
                        c.name,
                        color = if (focused) Color.White else BrandColors.TextSecondary,
                        style = NbrType.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
        Text(recommendationsHeading(seedTitle), color = MovieDetailTextPrimary, style = NbrType.Section)
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
    val cover = if (showCovers) (media.posterPath ?: media.thumbnailPath)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null
    Box(
        modifier = Modifier
            .width(104.dp)
            .aspectRatio(2f / 3f)
            .glideTarget(12.dp)
            .clip(BrandShapes.Md)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(BrandColors.SurfaceAlt)
            .then(if (focused && !gliding) Modifier.border(3.dp, BRAND_ACCENT, BrandShapes.Md) else Modifier)
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

/**
 * Título do filme: quando o índice/legenda traz o logo (PNG transparente do TMDB), mostra a arte; sem
 * logo — ou se a imagem falhar — cai no título em texto (NbrType.Hero; na TV, 80 px / 2 = 40 sp).
 * A altura é fixa em dp porque os logos variam muito de proporção: travar a altura mantém todos com o
 * mesmo peso visual, e o espaço já fica reservado enquanto a imagem carrega.
 */
@Composable
private fun DetailTitle(title: String, logo: String?, tv: Boolean) {
    val url = logo?.takeIf { it.isNotBlank() }?.let { tmdbAtWidth(it, "w500") }
    var failed by remember(url) { mutableStateOf(false) }
    if (url == null || failed) {
        val style = if (tv) NbrType.Hero.copy(fontSize = 40.sp, lineHeight = 48.sp) else NbrType.Hero
        Text(title, color = MovieDetailTextPrimary, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }
    AsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        onState = { if (it is coil.compose.AsyncImagePainter.State.Error) failed = true },
        modifier = Modifier.height(if (tv) 76.dp else 64.dp).widthIn(max = 320.dp)
    )
}

/**
 * Sinopse (NbrType.Paragraph) com "OK para ler tudo" em accent embaixo, como no protótipo. Toque ou OK
 * expande/recolhe o texto completo; na TV o bloco é focável pelo D-pad.
 */
@Composable
internal fun SynopsisText(text: String, tv: Boolean = true) {
    var expanded by remember(text) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val ring = focused && !glideActive()
    Column(
        modifier = Modifier
            .glideTarget(10.dp)
            .clip(BrandShapes.Sm)
            .onFocusChanged { focused = it.isFocused }
            .clickable { expanded = !expanded }
            .then(if (ring) Modifier.border(2.dp, BrandColors.Accent, BrandShapes.Sm) else Modifier)
            .then(if (tv) Modifier.padding(horizontal = 6.dp, vertical = 4.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (tv) 14.dp else 18.dp)
    ) {
        Text(
            text,
            color = BrandColors.TextSecondary,
            style = if (tv) NbrType.Paragraph.copy(lineHeight = 21.sp) else NbrType.Paragraph.copy(lineHeight = 21.sp),
            maxLines = if (expanded) Int.MAX_VALUE else if (tv) 3 else 4,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (expanded) "OK para recolher" else "OK para ler tudo",
            color = BrandColors.Accent,
            style = NbrType.Caption.copy(fontWeight = FontWeight.SemiBold, fontSize = if (tv) 11.sp else 12.sp)
        )
    }
}

@Composable
internal fun DetailsActionRow(
    media: MediaCardUi,
    showPlaybackWarning: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    playLoading: Boolean = false,
    playFailed: Boolean = false,
    onRestart: () -> Unit = {},
    tv: Boolean = true,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onReportClick: () -> Unit = {},
    playFailedText: String? = null,
    /** Trailer em tela cheia; nulo = título sem trailer (o botão não aparece). */
    onTrailerClick: (() -> Unit)? = null
) {
    val playLabel = when {
        playLoading -> "Abrindo…"
        playFailed -> playFailedText ?: "Falhou — tentar de novo"
        showPlaybackWarning -> "Tentar assistir"
        media.progress > 0f -> continueLabel(media)
        else -> "Assistir"
    }
    // Vídeo parado no meio: "Recomeçar" zera o progresso e toca do início (útil também quando
    // o download a partir do ponto salvo não anda).
    val showRestart = media.progress > 0f && !playLoading
    val favoriteDescription = if (isFavorite) "Remover da minha lista" else "Adicionar à minha lista"
    val favoriteIcon = if (isFavorite) NbrIcons.HeartFilled else NbrIcons.Heart
    val favoriteTint = if (isFavorite) BrandColors.Rose else MovieDetailTextPrimary

    if (tv) {
        // TV (protótipo 09): pílulas com ícone e rótulo, 36 dp de altura e 10 dp entre elas.
        Row(
            modifier = Modifier.focusGroup().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MovieDetailPlayButton(
                label = playLabel,
                tv = true,
                onClick = onPlay,
                modifier = Modifier.widthIn(min = 160.dp).focusRequester(playFocus),
                loading = playLoading,
                failed = playFailed && !playLoading
            )
            if (showRestart) MovieDetailPillButton(NbrIcons.RotateCcw, "Recomeçar", onRestart)
            if (onTrailerClick != null) MovieDetailPillButton(NbrIcons.Clapperboard, "Trailer", onTrailerClick)
            MovieDetailPillButton(favoriteIcon, favoriteDescription, onToggleFavorite, tint = favoriteTint)
            MovieDetailPillButton(NbrIcons.Flag, "Reportar", onReportClick)
        }
        return
    }

    // Celular (protótipos 14/15/20): Assistir em pílula ocupando o resto da linha e botões redondos
    // de 48 dp ao lado; no "Continuar", a barra de progresso fica sob o botão.
    Row(
        modifier = Modifier.focusGroup().fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MovieDetailPlayButton(
                label = playLabel,
                tv = false,
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().focusRequester(playFocus),
                loading = playLoading,
                failed = playFailed && !playLoading
            )
            if (media.progress > 0f && !playFailed) {
                MovieDetailProgress(media.progress, Modifier.fillMaxWidth())
            }
        }
        val roundTop = Modifier.padding(top = 2.dp)
        if (showRestart) MovieDetailRoundButton(NbrIcons.RotateCcw, "Recomeçar", onRestart, roundTop)
        if (onTrailerClick != null) MovieDetailRoundButton(NbrIcons.Clapperboard, "Ver trailer", onTrailerClick, roundTop)
        MovieDetailRoundButton(
            favoriteIcon, favoriteDescription, onToggleFavorite, roundTop,
            tint = favoriteTint,
            fill = if (isFavorite) BrandColors.Rose.copy(alpha = 0x22 / 255f) else Color(0x1AFFFFFF)
        )
        MovieDetailRoundButton(NbrIcons.Flag, "Reportar problema", onReportClick, roundTop)
    }
}

/** "Continuar • 1h27" — o tempo já assistido, como no protótipo. */
private fun continueLabel(media: MediaCardUi): String {
    if (media.durationSeconds <= 0) return "Continuar"
    val watched = (media.durationSeconds * media.progress).toInt()
    // Menos de 1 minuto assistido: sem o tempo (antes aparecia "Continuar • 0min").
    if (watched < 60) return "Continuar"
    return "Continuar • ${durationLabel(watched)}"
}

/**
 * Linha "nota · ano · gêneros · duração" do protótipo (13 sp, texto secundário). Ano e gêneros são
 * clicáveis: listam as mídias do canal com esse ano/gênero.
 */
@Composable
private fun DetailMetaLine(
    details: MovieDetails?,
    durationSecs: Int,
    tv: Boolean,
    onFilterClick: (SearchFilter) -> Unit
) {
    val style = NbrType.Secondary
    val genres = details?.genres?.split(',', ';', '/', '•')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val parts = mutableListOf<@Composable () -> Unit>()
    details?.rating?.let { rating ->
        parts += {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(NbrIcons.StarFilled, contentDescription = null, tint = BRAND_ACCENT, modifier = Modifier.size(13.dp))
                Text("%.1f".format(rating), color = BRAND_ACCENT, style = style.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
    details?.year?.let { year ->
        parts += { MetaText(year.toString(), style) { onFilterClick(SearchFilter(SearchFilterKind.YEAR, year.toString())) } }
    }
    genres.forEach { genre ->
        parts += { MetaText(genre, style) { onFilterClick(SearchFilter(SearchFilterKind.GENRE, genre)) } }
    }
    if (durationSecs > 0) parts += { MetaText(durationLabel(durationSecs), style) }
    if (parts.isEmpty()) return
    Row(
        modifier = if (tv) Modifier else Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        parts.forEachIndexed { i, part ->
            if (i > 0) Text("·", color = BrandColors.TextSecondary, style = style)
            part()
        }
    }
}

private fun hasChips(details: MovieDetails?, partCount: Int): Boolean =
    !details?.ageRating.isNullOrBlank() || !details?.quality.isNullOrBlank() || partCount >= 2

/** Chips em pílula: classificação, qualidade (SD/HD/FHD/4K) e quantidade de partes. */
@Composable
private fun DetailChips(details: MovieDetails?, partCount: Int, tv: Boolean) {
    details?.ageRating?.takeIf { it.isNotBlank() }?.let { MovieDetailChip(it.trim().replace(RX_COMMA_SEP, " • "), tv) }
    // Mesmo selo da grade (SD/HD/FHD/4K); só cai no texto do canal quando ele não diz a resolução.
    details?.quality?.takeIf { it.isNotBlank() }?.let { q ->
        val badge = com.ntv2.app.core.player.VideoResolution.label(
            height = com.ntv2.app.feature.media.domain.QualityText.heightOf(q)
        )
        MovieDetailChip(badge ?: q.trim().replace(RX_COMMA_SEP, " • "), tv)
    }
    // Filme dividido em partes no Telegram (>= 2): chip com a quantidade.
    if (partCount >= 2) MovieDetailChip("$partCount partes", tv)
}

@Composable
private fun MetaText(text: String, style: TextStyle, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        Text(text, color = BrandColors.TextSecondary, style = style, modifier = Modifier.padding(horizontal = 2.dp))
        return
    }
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Text(
        text,
        color = if (focused) Color.White else BrandColors.TextSecondary,
        style = style,
        modifier = Modifier
            .glideTarget(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .then(if (focused && !gliding) Modifier.border(2.dp, BrandColors.Accent, RoundedCornerShape(4.dp)) else Modifier)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    )
}

/** Chip com borda; com [onClick] é focável (D-pad) e clicável (toque). Usado também nos detalhes da série. */
@Composable
internal fun MetaChip(text: String, onClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val ring = focused && !gliding
    Text(
        text,
        color = if (focused) Color.White else BrandColors.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .then(if (onClick != null) Modifier.glideTarget(4.dp) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick) else Modifier)
            .border(if (ring) 2.dp else 1.dp, if (ring) Color.White else Color(0xFF33343A), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/** Linha "Na sua lista" com check, exibida sob as ações quando favoritado. */
@Composable
internal fun InListLabel() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NbrIcons.Check, contentDescription = null, tint = BRAND_ACCENT, modifier = Modifier.size(15.dp))
        Text("Na sua lista", color = BRAND_ACCENT, style = NbrType.Caption.copy(fontWeight = FontWeight.SemiBold))
    }
}

// Regex compilados uma vez (criar a cada chamada custava CPU no Fire TV, em caminhos por item/página).
private val RX_COMMA_SEP = Regex("\\s*,\\s*")
