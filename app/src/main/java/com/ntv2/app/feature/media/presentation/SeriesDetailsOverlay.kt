package com.ntv2.app.feature.media.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ntv2.app.core.ui.FocusGlideScope
import com.ntv2.app.core.ui.glideActive
import com.ntv2.app.core.ui.glideTarget
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.SeasonSummary
import com.ntv2.app.feature.media.domain.SeriesSummary
import com.ntv2.app.feature.media.presentation.state.SearchFilter
import com.ntv2.app.feature.media.presentation.state.SearchFilterKind

/**
 * Detalhes de uma série: arte + seletor de temporada + lista de episódios em ordem. Cada linha
 * reproduz seu próprio vídeo. O botão principal toca o próximo episódio ainda não iniciado.
 * Layout adaptativo: celular = coluna única rolável; TV = duas colunas (info à esquerda, lista de
 * episódios focável à direita) com foco inicial no botão Assistir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeriesDetailsOverlay(
    series: SeriesSummary,
    episodeProgress: Map<String, Float>,
    showCovers: Boolean,
    // (episódio, próximo da série ou null) — o próximo alimenta o autoplay no player.
    onPlayEpisode: (MediaItemSummary, MediaItemSummary?) -> Unit,
    onClose: () -> Unit,
    onFilterClick: (SearchFilter) -> Unit = {}
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
    fun successorOf(ep: MediaItemSummary): MediaItemSummary? {
        val i = ordered.indexOfFirst { it.mediaId == ep.mediaId }
        return if (i in 0 until ordered.lastIndex) ordered[i + 1] else null
    }

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

    FocusGlideScope(Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (isTv) {
            // ── TV: duas colunas com handshake de foco entre painéis (→ entra na lista, ← volta) ──
            val listFocus = remember { FocusRequester() }
            val episodes = current?.episodes.orEmpty()
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SeriesHero(series, showCovers)
                    Text(series.title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    SeriesFilterChips(series, onFilterClick)
                    nextEpisode?.let { ep ->
                        PlayButton(
                            label = "Assistir ${ep.episodeCode()}",
                            focusRequester = playFocus,
                            // ↓ cai nos chips de temporada (quando há); → entra na lista de episódios.
                            modifier = Modifier.focusProperties {
                                if (hasSeasonRow) down = seasonsFocus
                                if (episodes.isNotEmpty()) right = listFocus
                            },
                            onClick = { onPlayEpisode(ep, successorOf(ep)) }
                        )
                    }
                    SeasonSelector(
                        seasons = seasons,
                        selected = current?.number,
                        firstChipFocus = seasonsFocus,
                        upTarget = playFocus,
                        // → no último chip entra na lista; entre chips, ←/→ alterna as temporadas.
                        rightTargetOnLast = if (episodes.isNotEmpty()) listFocus else null,
                        onSelect = { selectedSeason = it }
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 20.dp)
                        // Qualquer ← na lista volta ao botão Assistir (painel esquerdo).
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                                runCatching { playFocus.requestFocus() }; true
                            } else false
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(episodes, key = { _, ep -> ep.mediaId }) { index, ep ->
                        EpisodeRow(
                            episode = ep,
                            progress = episodeProgress[ep.mediaId] ?: 0f,
                            showCovers = showCovers,
                            // O 1º item ganha o alvo de foco para o → do painel esquerdo cair aqui.
                            modifier = if (index == 0) Modifier.focusRequester(listFocus) else Modifier,
                            onClick = { onPlayEpisode(ep, successorOf(ep)) }
                        )
                    }
                }
            }
            // Voltar (canto): focável, mas o foco inicial fica no Assistir.
            BackChip(onClose, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
        } else {
            // ── Celular: coluna única rolável ──
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Text(
                                series.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SeriesHero(series, showCovers)
                        SeriesFilterChips(
                            series, onFilterClick,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        nextEpisode?.let { ep ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                PlayButton(
                                    label = "Assistir ${ep.episodeCode()}",
                                    focusRequester = playFocus,
                                    onClick = { onPlayEpisode(ep, successorOf(ep)) }
                                )
                            }
                        }
                        SeasonSelector(seasons, current?.number) { selectedSeason = it }
                    }
                }
                items(current?.episodes.orEmpty(), key = { it.mediaId }) { ep ->
                    EpisodeRow(ep, episodeProgress[ep.mediaId] ?: 0f, showCovers) {
                        onPlayEpisode(ep, successorOf(ep))
                    }
                }
            }
        }
    }
    }
}

/**
 * Chips da série: linha de cima com os informativos (temporadas, episódios, qualidade; não
 * clicáveis) e, numa linha exclusiva logo abaixo, ano e gêneros (clicáveis: listam as mídias do canal).
 */
@Composable
private fun SeriesFilterChips(
    series: SeriesSummary,
    onFilterClick: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val seasons = series.seasons.count { it.episodes.isNotEmpty() }
    val episodes = series.seasons.sumOf { it.episodes.size }
    val quality = series.seasons.asSequence().flatMap { it.episodes.asSequence() }
        .firstNotNullOfOrNull { it.quality?.takeIf { q -> q.isNotBlank() } }
        ?.trim()?.replace(Regex("\\s*,\\s*"), " • ")
    val genres = series.genres.map { it.trim() }.filter { it.isNotEmpty() }
    if (seasons == 0 && quality == null && series.year == null && genres.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (seasons > 0 || quality != null) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (seasons > 0) MetaChip(if (seasons == 1) "1 temporada" else "$seasons temporadas")
                if (episodes > 0) MetaChip(if (episodes == 1) "1 episódio" else "$episodes episódios")
                quality?.let { MetaChip(it) }
            }
        }
        if (series.year != null || genres.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                series.year?.let { y -> MetaChip(y.toString()) { onFilterClick(SearchFilter(SearchFilterKind.YEAR, y.toString())) } }
                genres.forEach { g -> MetaChip(g) { onFilterClick(SearchFilter(SearchFilterKind.GENRE, g)) } }
            }
        }
    }
}

@Composable
private fun SeriesHero(series: SeriesSummary, showCovers: Boolean) {
    val hero = series.backdropUrl ?: series.posterUrl
    if (showCovers && hero != null) {
        AsyncImage(
            model = hero,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
        )
    }
}

@Composable
private fun PlayButton(
    label: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Row(
        modifier = modifier
            .glideTarget(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(BRAND_ACCENT)
            .then(if (focused && !gliding) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
        Text(label, color = Color.Black, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonSelector(
    seasons: List<SeasonSummary>,
    selected: Int?,
    firstChipFocus: FocusRequester? = null,
    upTarget: FocusRequester? = null,
    rightTargetOnLast: FocusRequester? = null,
    onSelect: (Int) -> Unit
) {
    if (seasons.size <= 1) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        seasons.forEachIndexed { index, s ->
            val isSel = s.number == selected
            var focused by remember { mutableStateOf(false) }
            val gliding = glideActive()
            val isLast = index == seasons.lastIndex
            // 1º chip: alvo do ↓ do Assistir + ↑ volta ao Assistir. Último chip: → entra na lista.
            val chipMod = Modifier
                .then(if (index == 0 && firstChipFocus != null) Modifier.focusRequester(firstChipFocus) else Modifier)
                .focusProperties {
                    if (index == 0 && upTarget != null) up = upTarget
                    if (isLast && rightTargetOnLast != null) right = rightTargetOnLast
                }
            Text(
                "Temp. ${s.number}",
                color = if (isSel) Color.Black else Color(0xFFCFCFCF),
                style = MaterialTheme.typography.labelLarge,
                modifier = chipMod
                    .glideTarget(20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(s.number) }
                    .background(if (isSel) BRAND_ACCENT else Color(0x22FFFFFF))
                    .then(if (focused && !isSel && !gliding) Modifier.border(2.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: MediaItemSummary,
    progress: Float,
    showCovers: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .glideTarget(10.dp)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x26FFFFFF) else Color.Transparent)
            .then(if (focused && !gliding) Modifier.border(2.dp, BRAND_ACCENT, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumb = episode.backdropPath ?: episode.posterPath
        if (showCovers && thumb != null) {
            Box {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(132.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                )
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomStart)
                            .background(Color(0x55000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                .height(3.dp)
                                .background(BRAND_ACCENT)
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildString {
                    append(episode.episodeCode())
                    episode.title?.takeIf { it.isNotBlank() }?.let { append("  ").append(it) }
                },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.durationSeconds > 0) {
                Text(
                    durationLabel(episode.durationSeconds),
                    color = Color(0xFF8A8A8A),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            episode.synopsis?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = Color(0xFFB0B0B0),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Voltar da TV (detalhes de série e de filme): só o ícone, no canto superior direito. */
@Composable
internal fun BackChip(onClose: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val gliding = glideActive()
    Box(
        modifier = modifier
            .size(44.dp)
            .glideTarget(22.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClose)
            .background(if (focused) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
            .then(if (focused && !gliding) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** "S01E02" a partir dos números; cai para o título quando faltam. */
private fun MediaItemSummary.episodeCode(): String {
    val s = seasonNumber
    val e = episodeNumber
    return if (s != null && e != null) "S%02dE%02d".format(s, e) else (title.ifBlank { "Episódio" })
}
