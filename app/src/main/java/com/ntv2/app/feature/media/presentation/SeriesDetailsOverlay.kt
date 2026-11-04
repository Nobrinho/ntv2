package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.icons.NbrIcons
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.core.ui.trapFocus
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.SeasonSummary
import com.ntv2.app.feature.media.domain.SeriesSummary
import com.ntv2.app.feature.media.presentation.state.SearchFilter
import com.ntv2.app.feature.media.presentation.state.SearchFilterKind

/**
 * Detalhes de uma série, no visual do protótipo (celular 16/17/18, TV 10): capa atrás da barra de
 * status, botão "Assistir T_ · E_", seletor de temporada, lista de episódios com miniatura, progresso
 * e estado assistido. Cada linha reproduz seu próprio vídeo; o botão principal toca o próximo
 * episódio ainda não iniciado.
 * Celular = coluna única rolável (temporada no sheet; toque longo no episódio abre as opções).
 * TV = info à esquerda e episódios à direita, foco inicial no botão Assistir.
 *
 * [onRestartEpisode] e [episodeReportSender] são opcionais: quando a tela que abre a série os
 * passar, as opções do episódio ganham "Recomeçar episódio" e "Reportar problema".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeriesDetailsOverlay(
    series: SeriesSummary,
    episodeProgress: Map<String, Float>,
    showCovers: Boolean,
    // (episódio, seguintes da série em ordem) — alimentam o autoplay encadeado no player.
    onPlayEpisode: (MediaItemSummary, List<MediaItemSummary>) -> Unit,
    onClose: () -> Unit,
    onFilterClick: (SearchFilter) -> Unit = {},
    onRestartEpisode: ((MediaItemSummary, List<MediaItemSummary>) -> Unit)? = null,
    episodeReportSender: ((MediaItemSummary) -> ReportSender)? = null
) {
    BackHandler(enabled = true) { onClose() }
    val isTv = rememberAdaptiveLayoutInfo().useTvLayout

    val seasons = series.seasons.filter { it.episodes.isNotEmpty() }
    var selectedSeason by remember(series.tmdbId) { mutableStateOf(seasons.firstOrNull()?.number ?: 0) }
    val current: SeasonSummary? = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()

    val ordered = remember(series.tmdbId) {
        seasons.sortedBy { it.number }.flatMap { s -> s.episodes.sortedBy { it.episodeNumber } }
    }
    val nextEpisode = ordered.firstOrNull { (episodeProgress[it.mediaId] ?: 0f) <= 0f } ?: ordered.firstOrNull()
    fun followingOf(ep: MediaItemSummary): List<MediaItemSummary> {
        val i = ordered.indexOfFirst { it.mediaId == ep.mediaId }
        return if (i >= 0) ordered.drop(i + 1) else emptyList()
    }
    fun watchedIn(season: SeasonSummary?): Int =
        season?.episodes?.count { (episodeProgress[it.mediaId] ?: 0f) >= SERIES_WATCHED_FRACTION } ?: 0

    val playFocus = remember { FocusRequester() }
    val seasonsFocus = remember { FocusRequester() }
    val hasSeasonRow = seasons.size > 1
    // TV: o botão pode não estar anexado no 1º frame; tenta por alguns frames até pegar.
    LaunchedEffect(series.tmdbId) {
        repeat(10) {
            if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    // Sheets do celular (17 = temporadas, 18 = opções do episódio) e o diálogo de reporte.
    var seasonSheet by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<MediaItemSummary?>(null) }
    var reportFor by remember { mutableStateOf<MediaItemSummary?>(null) }

    FocusGlideScope(Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Background)
            // A biblioteca continua composta por trás: o foco não pode escapar para ela.
            .trapFocus()
    ) {
        if (isTv) {
            // Fundo: capa em tela cheia com degradê horizontal bg@98% → bg@85% (45%) → bg@60%.
            SeriesBackdrop(series, showCovers, Modifier.fillMaxSize())
            // ── TV: duas colunas com handshake de foco entre painéis (→ entra na lista, ← volta) ──
            val listFocus = remember { FocusRequester() }
            val backFocus = remember { FocusRequester() }
            val episodes = current?.episodes.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(350.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                ) {
                    TvBackLink(
                        onClose,
                        Modifier
                            .focusRequester(backFocus)
                            .focusProperties { down = playFocus }
                    )
                    Text(
                        series.title,
                        color = SeriesText,
                        style = NbrType.Hero.copy(fontSize = 36.sp, lineHeight = 37.8.sp, fontWeight = FontWeight.ExtraBold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    SeriesMeta(series, tv = true, onFilterClick = onFilterClick)
                    nextEpisode?.let { ep ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            BrandButton(
                                onClick = { onPlayEpisode(ep, followingOf(ep)) },
                                // ↑ volta ao Voltar; → entra na lista (ou nos chips de temporada).
                                modifier = Modifier
                                    .width(260.dp)
                                    .focusRequester(playFocus)
                                    .focusProperties {
                                        up = backFocus
                                        if (episodes.isNotEmpty()) right = listFocus
                                        else if (hasSeasonRow) right = seasonsFocus
                                    }
                            ) {
                                Icon(NbrIcons.PlayFilled, contentDescription = null, tint = BrandColors.OnCta, modifier = Modifier.size(15.dp))
                                BrandButtonLabel(playLabel(ep))
                            }
                            Text(
                                ctaHint(ep, episodeProgress[ep.mediaId] ?: 0f, seasons),
                                style = NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp),
                                color = BrandColors.Accent
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Qualquer ← nos episódios volta ao botão Assistir (coluna da esquerda).
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                                runCatching { playFocus.requestFocus() }; true
                            } else false
                        },
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    if (hasSeasonRow) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            seasons.forEachIndexed { index, s ->
                                SeriesTvSeasonChip(
                                    label = "Temporada ${s.number}",
                                    selected = s.number == current?.number,
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(seasonsFocus) else Modifier)
                                        .focusProperties { if (episodes.isNotEmpty()) down = listFocus },
                                    onClick = { selectedSeason = s.number }
                                )
                            }
                        }
                    }
                    SeriesSeasonProgress(watchedIn(current), episodes.size, tv = true)
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        itemsIndexed(episodes, key = { _, ep -> ep.mediaId }) { index, ep ->
                            SeriesEpisodeCard(
                                episode = ep,
                                progress = episodeProgress[ep.mediaId] ?: 0f,
                                showCovers = showCovers,
                                tv = true,
                                // O 1º item ganha o alvo de foco para o → da coluna esquerda cair aqui.
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(listFocus)
                                        .focusProperties { if (hasSeasonRow) up = seasonsFocus }
                                } else Modifier,
                                onClick = { onPlayEpisode(ep, followingOf(ep)) }
                            )
                        }
                    }
                }
            }
        } else {
            // ── Celular: coluna única rolável (16) ──
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = navBottom + 24.dp)
            ) {
                item(key = "hero") {
                    SeriesHero(series, showCovers, onClose, onFilterClick)
                }
                item(key = "content") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        SeriesCountChips(series, tv = false)
                        nextEpisode?.let { ep ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BrandButton(
                                    onClick = { onPlayEpisode(ep, followingOf(ep)) },
                                    modifier = Modifier.fillMaxWidth().focusRequester(playFocus)
                                ) {
                                    Icon(NbrIcons.PlayFilled, contentDescription = null, tint = BrandColors.OnCta, modifier = Modifier.size(20.dp))
                                    BrandButtonLabel(playLabel(ep))
                                }
                                Text(
                                    ctaHint(ep, episodeProgress[ep.mediaId] ?: 0f, seasons),
                                    style = NbrType.Caption,
                                    color = BrandColors.Accent,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Episódios",
                                    style = NbrType.SheetTitle.copy(fontSize = 20.sp, lineHeight = 24.sp),
                                    color = SeriesText
                                )
                                if (hasSeasonRow && current != null) {
                                    SeriesSeasonSelectorChip("Temporada ${current.number}") { seasonSheet = true }
                                }
                            }
                            SeriesSeasonProgress(watchedIn(current), current?.episodes?.size ?: 0, tv = false)
                        }
                    }
                }
                items(current?.episodes.orEmpty(), key = { it.mediaId }) { ep ->
                    SeriesEpisodeCard(
                        episode = ep,
                        progress = episodeProgress[ep.mediaId] ?: 0f,
                        showCovers = showCovers,
                        tv = false,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
                        onLongClick = { optionsFor = ep },
                        onClick = { onPlayEpisode(ep, followingOf(ep)) }
                    )
                }
            }

            // 17 — escolher temporada.
            SeriesSheet(visible = seasonSheet, title = "Temporadas", onDismiss = { seasonSheet = false }) {
                SeriesSeasonOptions {
                    seasons.forEach { s ->
                        val total = s.episodes.size
                        SeriesSeasonOption(
                            label = "Temporada ${s.number}",
                            subtitle = "${if (total == 1) "1 episódio" else "$total episódios"} · ${watchedIn(s)} assistidos",
                            selected = s.number == current?.number,
                            onClick = { selectedSeason = s.number; seasonSheet = false }
                        )
                    }
                }
            }

            // 18 — opções do episódio (toque longo no cartão).
            val opt = optionsFor
            SeriesSheet(
                visible = opt != null,
                title = opt?.seriesEpisodeTitle().orEmpty(),
                onDismiss = { optionsFor = null }
            ) {
                if (opt != null) {
                    Column {
                        SeriesSheetAction(NbrIcons.PlayFilled, "Assistir episódio") {
                            optionsFor = null
                            onPlayEpisode(opt, followingOf(opt))
                        }
                        if (onRestartEpisode != null && (episodeProgress[opt.mediaId] ?: 0f) > 0f) {
                            SeriesSheetAction(NbrIcons.RotateCcw, "Recomeçar episódio") {
                                optionsFor = null
                                onRestartEpisode(opt, followingOf(opt))
                            }
                        }
                        if (episodeReportSender != null) {
                            SeriesSheetAction(NbrIcons.Flag, "Reportar problema", color = BrandColors.Warning) {
                                optionsFor = null
                                reportFor = opt
                            }
                        }
                    }
                }
            }
        }
    }
    }

    val rep = reportFor
    if (rep != null && episodeReportSender != null) {
        ReportMediaDialog(
            title = rep.seriesEpisodeTitle(),
            onReport = episodeReportSender(rep),
            onDismiss = { reportFor = null }
        )
    }
}

/** Hero 2.16 do celular (300): capa atrás da barra de status, voltar e o bloco do título no fundo. */
@Composable
private fun SeriesHero(
    series: SeriesSummary,
    showCovers: Boolean,
    onClose: () -> Unit,
    onFilterClick: (SearchFilter) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
        SeriesBackdrop(series, showCovers, Modifier.matchParentSize(), portrait = true)
        // Ações no topo e título no fundo; título longo empurra o hero para baixo em vez de subir.
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, BrandColors.Border, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(NbrIcons.ChevronLeft, "Voltar", tint = SeriesText, modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 40.dp, end = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    series.title,
                    color = SeriesText,
                    style = NbrType.Hero,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                SeriesMetaLine(series, tv = false, onFilterClick = onFilterClick)
            }
        }
    }
}

/**
 * Meta "2022 · Drama · Fantasia" (13/400 text-2). Ano e gêneros continuam clicáveis (listam as
 * mídias do canal com aquele filtro); na TV cada parte é focável com o anel da tela.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesMetaLine(series: SeriesSummary, tv: Boolean, onFilterClick: (SearchFilter) -> Unit) {
    val parts = buildList {
        series.year?.let { y -> add(y.toString() to SearchFilter(SearchFilterKind.YEAR, y.toString())) }
        series.genres.map { it.trim() }.filter { it.isNotEmpty() }.forEach { g -> add(g to SearchFilter(SearchFilterKind.GENRE, g)) }
    }
    if (parts.isEmpty()) return
    val style = if (tv) NbrType.Caption else NbrType.Secondary
    FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, (label, filter) ->
            if (i > 0) Text(" · ", style = style, color = BrandColors.TextSecondary)
            MetaLink(label, style, tv) { onFilterClick(filter) }
        }
    }
}

@Composable
private fun MetaLink(label: String, style: androidx.compose.ui.text.TextStyle, tv: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Text(
        label,
        style = style,
        color = if (tv && focused) SeriesText else BrandColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .then(if (tv) Modifier.glideTarget(4.dp) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .then(if (tv && focused && !gliding) Modifier.border(2.dp, BrandColors.Accent, RoundedCornerShape(4.dp)) else Modifier)
            .padding(vertical = 2.dp)
    )
}

/** TV: meta (ano · gêneros) + chips de temporadas/episódios/qualidade numa só faixa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesMeta(series: SeriesSummary, tv: Boolean, onFilterClick: (SearchFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SeriesMetaLine(series, tv, onFilterClick)
        SeriesCountChips(series, tv)
    }
}

/** Chips informativos (não clicáveis): temporadas, episódios e qualidade. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesCountChips(series: SeriesSummary, tv: Boolean) {
    val seasons = series.seasons.count { it.episodes.isNotEmpty() }
    val episodes = series.seasons.sumOf { it.episodes.size }
    val quality = series.seasons.asSequence().flatMap { it.episodes.asSequence() }
        .firstNotNullOfOrNull { it.quality?.takeIf { q -> q.isNotBlank() } }
        ?.trim()?.replace(RX_COMMA_SEP, " • ")
    if (seasons == 0 && episodes == 0 && quality == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (tv) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (tv) 6.dp else 8.dp)
    ) {
        if (seasons > 0) SeriesChip(if (seasons == 1) "1 temporada" else "$seasons temporadas", tv)
        if (episodes > 0) SeriesChip(if (episodes == 1) "1 episódio" else "$episodes episódios", tv)
        quality?.let { SeriesChip(it, tv) }
    }
}

/** "‹ Voltar" no topo da coluna de info da TV (12/400), focável com o anel da tela. */
@Composable
private fun TvBackLink(onClose: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .height(26.dp)
            .glideTarget(13.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClose)
            .background(if (focused) BrandColors.Fill else Color.Transparent)
            .then(if (focused && !gliding) Modifier.border(2.dp, BrandColors.Accent, shape) else Modifier)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(NbrIcons.ChevronLeft, contentDescription = null, tint = BrandColors.TextSecondary, modifier = Modifier.size(14.dp))
        Text("Voltar", style = NbrType.Caption, color = if (focused) SeriesText else BrandColors.TextSecondary)
    }
}

/**
 * Capa da série. TV: degradê horizontal bg@98% → bg@85% (45%) → bg@60%. Celular (hero 2.16):
 * degradê vertical bg@33% → bg@13% (40%) → bg@93%, e o bloco do título escurece até o fundo.
 */
@Composable
private fun SeriesBackdrop(series: SeriesSummary, showCovers: Boolean, modifier: Modifier, portrait: Boolean = false) {
    val hero = (series.backdropUrl ?: series.posterUrl)?.let { com.ntv2.app.core.ui.tmdbAtWidth(it, if (portrait) "w780" else "w1280") }
    Box(modifier = modifier) {
        if (showCovers && hero != null) {
            AsyncImage(
                model = hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(modifier = Modifier.fillMaxSize().drawWithCache {
            val bg = BrandColors.Background
            val v = Brush.verticalGradient(
                0f to bg.copy(alpha = 0.33f),
                0.4f to bg.copy(alpha = 0.13f),
                0.75f to bg.copy(alpha = 0.6f),
                1f to bg.copy(alpha = 0.93f)
            )
            val h = Brush.horizontalGradient(
                0f to bg.copy(alpha = 0.98f),
                0.45f to bg.copy(alpha = 0.85f),
                1f to bg.copy(alpha = 0.6f)
            )
            onDrawBehind { drawRect(if (portrait) v else h) }
        })
    }
}

/** "Assistir T1 · E3" (sem números: "Assistir"). */
private fun playLabel(ep: MediaItemSummary): String {
    val s = ep.seasonNumber
    val e = ep.episodeNumber
    return if (s != null && e != null) "Assistir T$s · E$e" else "Assistir"
}

/** "Faltam 22min · Episódio 3 de 10" (o "Faltam" só quando já começou e a duração é conhecida). */
private fun ctaHint(ep: MediaItemSummary, progress: Float, seasons: List<SeasonSummary>): String {
    val season = seasons.firstOrNull { s -> s.episodes.any { it.mediaId == ep.mediaId } }
    val total = season?.episodes?.size ?: 0
    val pos = season?.episodes?.sortedBy { it.episodeNumber }?.indexOfFirst { it.mediaId == ep.mediaId }?.plus(1) ?: 0
    val parts = buildList {
        if (progress > 0f && ep.durationSeconds > 0) {
            val left = (ep.durationSeconds * (1f - progress)).toInt().coerceAtLeast(60)
            add("Faltam ${durationLabel(left)}")
        }
        if (pos > 0 && total > 0) add("Episódio ${ep.episodeNumber ?: pos} de $total")
    }
    return parts.joinToString(" · ")
}

/** Voltar da TV (detalhes do filme): só o ícone, no canto superior direito. */
@Composable
internal fun BackChip(onClose: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Box(
        modifier = modifier
            .size(48.dp)
            .glideTarget(22.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClose)
            .background(if (focused) BrandColors.Border else Color(0x1AFFFFFF))
            .then(if (focused && !gliding) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(NbrIcons.ChevronLeft, "Voltar", tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

// Regex compilado uma vez (criar a cada chamada custava CPU no Fire TV, em caminhos por item/página).
private val RX_COMMA_SEP = Regex("\\s*,\\s*")
