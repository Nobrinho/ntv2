package com.ntv2.app.feature.media.presentation

import com.ntv2.app.core.ui.brandBackdrop
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.ntv2.app.core.ui.trapFocus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.SearchFilterOptions
import com.ntv2.app.feature.media.presentation.state.SearchFilters
import com.ntv2.app.feature.media.presentation.state.SearchTypeFilter

// Busca da biblioteca: teclado de TV, busca por toque e lista de resultados.

internal val KEYBOARD_ROWS = listOf(
    "1234567890",
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm"
)

@Composable
internal fun SearchStatusText(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchInProgress && query.isNotBlank()) {
            CircularProgressIndicator(
                color = BRAND_ACCENT,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = when {
                query.isBlank() -> "Digite algo para buscar"
                searchInProgress -> "Pesquisando…"
                else -> "$resultCount resultado(s)"
            },
            color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Busca da TV (estilo YouTube/Google TV): teclado próprio à esquerda, resultados ao vivo à
 * direita. Sem IME do sistema — funciona igual em qualquer Fire TV e não perde o foco.
 *
 * Regras de D-pad:
 * - → na última tecla de cada linha entra nos resultados (último resultado focado, senão o 1º);
 * - ← (ou BACK) num resultado volta para a última tecla usada;
 * - ↑ no 1º / ↓ no último resultado não saem da lista (↓ perto do fim pagina);
 * - resultados trocados (nova consulta) com foco na lista → foco volta ao teclado;
 * - BACK no teclado fecha a busca.
 */
@Composable
internal fun TvSearchOverlay(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    results: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    restoreFocusMediaId: String?,
    onRestoreConsumed: () -> Unit,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    onClose: () -> Unit,
    series: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    onSeriesSelect: (com.ntv2.app.feature.media.domain.SeriesSummary) -> Unit = {},
    showCovers: Boolean = true,
    // false na busca por elenco: teclado e campo ficam esmaecidos (o screen ignora as teclas).
    textEnabled: Boolean = true,
    filters: SearchFilters = SearchFilters(),
    filterOptions: SearchFilterOptions = SearchFilterOptions(),
    onFiltersChange: (SearchFilters) -> Unit = {},
    actorCount: Int = 0
) {
    val scope = rememberCoroutineScope()
    val backRequester = remember { FocusRequester() }
    var overlayHasFocus by remember { mutableStateOf(true) }
    // Busca por elenco: sem teclado, resultados em tela cheia numa grade de 6 colunas.
    val gridColumns = if (textEnabled) 3 else 6
    var showFilters by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val keyRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val resultRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun keyRequester(id: String) = keyRequesters.getOrPut(id) { FocusRequester() }
    fun resultRequester(id: String) = resultRequesters.getOrPut(id) { FocusRequester() }

    val firstKeyId = KEYBOARD_ROWS.first().first().toString()
    var lastKeyId by remember { mutableStateOf(firstKeyId) }
    var lastResultIndex by remember { mutableStateOf(0) }
    // Tecla do teclado virtual: lembra a última focada (voltar dos resultados cai nela).
    fun keyMod(id: String) = Modifier
        .focusRequester(keyRequester(id))
        .onFocusChanged { if (it.isFocused) lastKeyId = id }
    var focusInResults by remember { mutableStateOf(false) }
    // "Buscar" pressionado: leva o foco ao 1º resultado assim que a busca terminar.
    var focusResultsWhenReady by remember { mutableStateOf(false) }
    // Células da grade na ordem de exibição: séries primeiro, depois filmes (id = chave do foco).
    val cellIds = series.map { "series_${it.tmdbId}" } + results.map { it.mediaId }
    val currentCells by androidx.compose.runtime.rememberUpdatedState(cellIds)

    fun focusKeyboard() {
        runCatching { keyRequester(lastKeyId).requestFocus() }
            .onFailure { runCatching { keyRequester(firstKeyId).requestFocus() } }
    }

    fun focusResult(index: Int) {
        val list = currentCells
        if (list.isEmpty()) return
        val i = index.coerceIn(0, list.lastIndex)
        scope.launch {
            // O item pode não estar composto (lazy): rola até ele antes de pedir foco.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == i }) {
                listState.scrollToItem(i)
                withFrameNanos { }
            }
            runCatching { resultRequester(list[i]).requestFocus() }
        }
    }

    // Foco inicial: volta ao resultado que abriu os Detalhes; senão, 1ª tecla.
    LaunchedEffect(Unit) {
        val index = restoreFocusMediaId?.let { id -> cellIds.indexOf(id) } ?: -1
        onRestoreConsumed()
        if (index >= 0) {
            lastResultIndex = index
            listState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
            runCatching { resultRequester(cellIds[index]).requestFocus() }
                .onFailure { focusKeyboard() }
        } else if (textEnabled) {
            runCatching { keyRequester(firstKeyId).requestFocus() }
        } else {
            // Modo elenco: o foco já nasce no Voltar (único alvo enquanto a busca roda); o
            // 1º resultado o recebe quando a busca termina.
            withFrameNanos { }
            runCatching { backRequester.requestFocus() }
        }
    }

    // Nova consulta com o foco na lista: o item focado deixa de existir — devolve ao teclado.
    LaunchedEffect(query) {
        lastResultIndex = 0
        if (focusInResults) focusKeyboard()
    }

    LaunchedEffect(focusResultsWhenReady, searchInProgress, results, series) {
        if (!focusResultsWhenReady || searchInProgress) return@LaunchedEffect
        focusResultsWhenReady = false
        if (currentCells.isNotEmpty()) {
            listState.scrollToItem(0)
            withFrameNanos { }
            focusResult(0)
        }
    }

    // Modo elenco: garante o foco no 1º resultado. Outra tela (ex.: biblioteca restaurando o foco ao
    // fechar os Detalhes) pode roubá-lo logo após o pedido; tenta de novo até o foco ficar na grade.
    val hasCells = cellIds.isNotEmpty()
    LaunchedEffect(textEnabled, hasCells, searchInProgress) {
        if (textEnabled || !hasCells || searchInProgress) return@LaunchedEffect
        repeat(15) {
            if (focusInResults) return@LaunchedEffect
            withFrameNanos { }
            focusResult(lastResultIndex)
            kotlinx.coroutines.delay(120)
        }
    }
    // Se o foco sair do overlay (ex.: o item focado foi removido da composição), traz de volta.
    LaunchedEffect(overlayHasFocus, textEnabled, hasCells, searchInProgress, showFilters) {
        if (overlayHasFocus || showFilters) return@LaunchedEffect
        withFrameNanos { }
        when {
            textEnabled -> focusKeyboard()
            hasCells && !searchInProgress -> focusResult(lastResultIndex)
            else -> runCatching { backRequester.requestFocus() }
        }
    }

    fun typeChar(c: Char) {
        onKey(c)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(com.ntv2.app.core.ui.BrandColors.Background)
            .brandBackdrop(com.ntv2.app.core.ui.BrandBackdropKind.Gradient)
            // A biblioteca continua composta por trás: nenhuma direção pode levar o foco para ela.
            .trapFocus()
            .onFocusChanged { overlayHasFocus = it.hasFocus }
            .onPreviewKeyEvent { e ->
                when {
                    // Painel de filtros aberto: ele trata BACK e as teclas sozinho.
                    showFilters -> false
                    // BACK em camadas: resultados → teclado; teclado → fecha a busca.
                    e.key == Key.Back || e.key == Key.Escape -> {
                        if (e.type == KeyEventType.KeyUp) {
                            if (focusInResults && textEnabled) focusKeyboard() else onClose()
                        }
                        true
                    }
                    // Modo elenco: não há campo de texto, as teclas seguem para a grade.
                    !textEnabled -> false
                    e.type != KeyEventType.KeyDown -> false
                    // Teclado físico / teclas numéricas do controle digitam direto.
                    e.key == Key.Backspace -> { onBackspace(); true }
                    e.key == Key.Spacebar -> { typeChar(' '); true }
                    else -> {
                        val ch = e.nativeKeyEvent.unicodeChar.toChar()
                        if (ch.isLetterOrDigit()) { typeChar(ch.lowercaseChar()); true } else false
                    }
                }
            }
    ) {
        val compact = maxWidth < 900.dp
        val outerPadding = if (compact) 24.dp else 40.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 32.dp)
        ) {
            // ── Coluna esquerda: campo + teclado (fora do modo elenco) ──
            if (textEnabled) BoxWithConstraints(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            ) {
                val gap = 6.dp
                val columns = KEYBOARD_ROWS.maxOf { it.length }
                val keySize = ((maxWidth - gap * (columns - 1)) / columns).coerceIn(28.dp, 52.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
                ) {
                    Text("Buscar", style = MaterialTheme.typography.headlineMedium, color = Color.White)

                    // Campo mostrando o que já foi digitado.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = when {
                                !textEnabled -> "Elenco: $query"
                                query.isEmpty() -> "Digite para buscar por título, canal ou arquivo"
                                else -> "$query|"
                            },
                            color = if (query.isEmpty() || !textEnabled) Color(0xFF8E98A8) else Color.White,
                            style = if (query.isEmpty() || !textEnabled) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SearchStatusText(
                        query = query,
                        resultCount = resultCount,
                        searchInProgress = searchInProgress
                    )

                    // Última tecla de cada linha: → entra nos resultados.
                    val enterResultsOnRight = Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                            if (currentCells.isNotEmpty()) focusResult(lastResultIndex)
                            true
                        } else false
                    }

                    Column(
                        modifier = Modifier.alpha(if (textEnabled) 1f else 0.35f),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        KEYBOARD_ROWS.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEachIndexed { colIndex, c ->
                                    val id = c.toString()
                                    KeyButton(
                                        label = id,
                                        modifier = Modifier
                                            .then(if (colIndex == row.lastIndex) enterResultsOnRight else Modifier)
                                            .then(keyMod(id))
                                            .size(keySize),
                                        onClick = { lastKeyId = id; typeChar(c) }
                                    )
                                }
                            }
                        }

                        // Ações: espaço/apagar; buscar/limpar/fechar.
                        val actionHeight = keySize.coerceAtLeast(40.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            KeyButton(
                                label = "Espaço",
                                modifier = Modifier
                                    .then(keyMod("space"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = { lastKeyId = "space"; typeChar(' ') }
                            )
                            KeyButton(
                                label = "⌫ Apagar",
                                modifier = enterResultsOnRight
                                    // OK segurado: apaga em sequência se o controle repetir a tecla.
                                    .onPreviewKeyEvent { e ->
                                        val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter ||
                                            e.key == Key.NumPadEnter
                                        if (isOk && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0) {
                                            onBackspace(); true
                                        } else false
                                    }
                                    .then(keyMod("backspace"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = { lastKeyId = "backspace"; onBackspace() }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            KeyButton(
                                label = "Buscar",
                                modifier = Modifier
                                    .then(keyMod("submit"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = {
                                    lastKeyId = "submit"
                                    if (query.isNotBlank()) {
                                        focusResultsWhenReady = true
                                        onSubmit()
                                    }
                                }
                            )
                            KeyButton(
                                label = "Limpar",
                                modifier = Modifier
                                    .then(keyMod("clear"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = { lastKeyId = "clear"; onClear() }
                            )
                            if (filterOptions.isAvailable) {
                                val activeCount = filters.genres.size + filters.years.size +
                                    (if (filters.type != SearchTypeFilter.ALL) 1 else 0)
                                KeyButton(
                                    label = if (activeCount > 0) "Filtros ($activeCount)" else "Filtros",
                                    modifier = Modifier
                                        .then(keyMod("filters"))
                                        .weight(1f)
                                        .height(actionHeight),
                                    onClick = { lastKeyId = "filters"; showFilters = true }
                                )
                            }
                            KeyButton(
                                label = "Fechar",
                                modifier = enterResultsOnRight
                                    .then(keyMod("close"))
                                    .weight(1f)
                                    .height(actionHeight),
                                onClick = onClose
                            )
                        }
                    }
                }
            }

            // ── Coluna direita: filtros ativos + resultados ao vivo ──
            Column(
                modifier = Modifier
                    .weight(if (textEnabled) 0.58f else 1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            if (textEnabled) {
                TvActiveFilters(filters)
            } else {
                val name = filters.actor.orEmpty()
                val count = if (actorCount == 1) "1 filme" else "$actorCount filmes"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (actorCount > 0) "$name • $count" else name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Mesmo Voltar dos Detalhes: só o ícone, no canto superior direito.
                    BackChip(
                        onClose = onClose,
                        modifier = Modifier
                            .focusRequester(backRequester)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionDown -> { if (hasCells) focusResult(lastResultIndex); true }
                                    // Não deixa o foco escapar para a tela por trás.
                                    Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                                    else -> false
                                }
                            }
                    )
                }
            }
            SearchResultsList(
                query = query,
                searchInProgress = searchInProgress,
                results = results,
                hasMore = hasMore,
                loadingMore = loadingMore,
                listState = listState,
                onLoadMore = onLoadMore,
                onSelect = onSelect,
                series = series,
                onSeriesSelect = onSeriesSelect,
                showCovers = showCovers,
                columns = gridColumns,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x0DFFFFFF))
                    .focusGroup()
                    .onFocusChanged { focusInResults = it.hasFocus },
                cellModifier = { index, id ->
                    Modifier
                        .focusRequester(resultRequester(id))
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastResultIndex = index
                                // Paginação pelo foco: perto do fim, pede a próxima página.
                                if (index >= currentCells.size - 6 && hasMore && !loadingMore) onLoadMore()
                            }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                // Coluna da esquerda volta ao teclado; as outras andam na grade.
                                Key.DirectionLeft -> if (index % gridColumns == 0) { if (textEnabled) focusKeyboard(); true } else false
                                // Não sai da grade pela 1ª nem pela última linha.
                                // Modo elenco: a grade ocupa a tela toda; a direita não sai dela.
                                Key.DirectionRight ->
                                    !textEnabled && (index % gridColumns == gridColumns - 1 || index == currentCells.lastIndex)
                                Key.DirectionUp -> if (index < gridColumns) {
                                    if (!textEnabled) runCatching { backRequester.requestFocus() }
                                    true
                                } else false
                                Key.DirectionDown -> index / gridColumns == currentCells.lastIndex / gridColumns
                                else -> false
                            }
                        }
                }
            )
            }
        }

        if (showFilters) {
            TvFilterPanel(
                filters = filters,
                options = filterOptions,
                onChange = onFiltersChange,
                onDismiss = {
                    showFilters = false
                    scope.launch { withFrameNanos { }; focusKeyboard() }
                }
            )
        }
    }
}

/** Filtros ativos (tipo/ator/gênero/ano) acima dos resultados da TV; só exibição — edita-se no painel. */
@Composable
private fun TvActiveFilters(filters: SearchFilters) {
    if (!filters.isActive) return
    val labels = buildList {
        if (filters.type != SearchTypeFilter.ALL) add(filters.type.label)
        filters.actor?.let { add(it) }
        addAll(filters.genres)
        addAll(filters.years.sortedDescending().map { it.toString() })
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { l ->
            Text(
                l,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x3366D9FF))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Painel de filtros da TV (tipo, gênero, ano), igual ao do celular: chips alternáveis que aplicam na
 * hora. O foco fica preso no painel; BACK ou "Concluir" fecha e devolve o foco ao botão "Filtros".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TvFilterPanel(
    filters: SearchFilters,
    options: SearchFilterOptions,
    onChange: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { initialFocus.requestFocus() } }
    // Foco inicial: 1º gênero (ou o chip de tipo atual, se não houver gêneros).
    val firstGenre = options.genres.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .onPreviewKeyEvent { e ->
                if ((e.key == Key.Back || e.key == Key.Escape)) {
                    if (e.type == KeyEventType.KeyUp) onDismiss()
                    true
                } else false
            }
            .trapFocus(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtros", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
                if (filters.isActive) {
                    KeyButton("Limpar", Modifier.width(120.dp).height(44.dp)) { onChange(SearchFilters()) }
                }
                KeyButton("Concluir", Modifier.width(120.dp).height(44.dp), onDismiss)
            }
            TvFilterSection("Tipo") {
                SearchTypeFilter.entries.forEach { t ->
                    TvFilterChip(
                        text = t.label,
                        selected = filters.type == t,
                        modifier = if (firstGenre == null && filters.type == t) Modifier.focusRequester(initialFocus) else Modifier
                    ) { onChange(filters.copy(type = t)) }
                }
            }
            if (options.genres.isNotEmpty()) {
                TvFilterSection("Gênero") {
                    options.genres.forEach { g ->
                        TvFilterChip(
                            text = g,
                            selected = g in filters.genres,
                            modifier = if (g == firstGenre) Modifier.focusRequester(initialFocus) else Modifier
                        ) {
                            onChange(filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g))
                        }
                    }
                }
            }
            if (options.years.isNotEmpty()) {
                TvFilterSection("Ano") {
                    options.years.forEach { y ->
                        TvFilterChip(text = y.toString(), selected = y in filters.years) {
                            onChange(filters.copy(years = if (y in filters.years) filters.years - y else filters.years + y))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TvFilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Color(0xFF8E98A8), style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

@Composable
private fun TvFilterChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        color = if (focused || selected) Color.Black else Color(0xFFE0E0E0),
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                when {
                    focused -> Color.White
                    selected -> BRAND_ACCENT
                    else -> Color(0xFF2A2A2A)
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else if (selected) BRAND_ACCENT else Color(0xFF3A3A3A),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
internal fun TouchSearchOverlay(
    query: String,
    searchInProgress: Boolean,
    suggestions: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    onSubmit: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    series: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    onSeriesSelect: (com.ntv2.app.feature.media.domain.SeriesSummary) -> Unit = {},
    showCovers: Boolean = true,
    cardLoadingStyle: com.ntv2.app.core.ui.CardLoadingStyle = com.ntv2.app.core.ui.CardLoadingStyle.DEFAULT,
    animationsEnabled: Boolean = true,
    filters: SearchFilters = SearchFilters(),
    filterOptions: SearchFilterOptions = SearchFilterOptions(),
    onFiltersChange: (SearchFilters) -> Unit = {},
    actorCount: Int = 0
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Busca por elenco (toque num ator): o texto fica desabilitado, o filtro é o ator. Com qualquer
    // filtro de gênero/ano/ator vindo de um chip o teclado não abre sozinho.
    val actorMode = filters.actor != null
    LaunchedEffect(Unit) {
        if (!filters.hasAttribute) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }
    BackHandler(enabled = true) { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.ntv2.app.core.ui.BrandColors.Background)
            .brandBackdrop(com.ntv2.app.core.ui.BrandBackdropKind.Gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF252525))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = !actorMode,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(fieldFocus),
                    singleLine = true,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSubmit() }),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                androidx.compose.material3.Text(
                                    if (actorMode) "Elenco: ${filters.actor}" else "Pesquisar",
                                    color = Color(0xFF8E98A8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Close,
                            contentDescription = "Limpar",
                            tint = Color(0xFFD8D8D8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            if (filterOptions.isAvailable) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (filters.isActive) BRAND_ACCENT else Color(0xFF252525))
                        .clickable { keyboard?.hide(); showFilterSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.FilterList,
                        contentDescription = "Filtros",
                        tint = if (filters.isActive) Color.Black else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        ActiveFilterChips(filters, actorCount, onFiltersChange)

        TouchSearchResultsGrid(
            query = query,
            searchInProgress = searchInProgress,
            results = suggestions,
            hasMore = hasMore,
            loadingMore = loadingMore,
            gridState = gridState,
            onLoadMore = onLoadMore,
            onSelect = onSelect,
            series = series,
            onSeriesSelect = onSeriesSelect,
            showCovers = showCovers,
            cardLoadingStyle = cardLoadingStyle,
            animationsEnabled = animationsEnabled,
            filtersActive = filters.hasAttribute,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    if (showFilterSheet) {
        SearchFilterSheet(
            filters = filters,
            options = filterOptions,
            onChange = onFiltersChange,
            onDismiss = { showFilterSheet = false }
        )
    }
}

/** Chips dos filtros ativos (gênero/ano/tipo), cada um com "x" para remover. */
@Composable
private fun ActiveFilterChips(filters: SearchFilters, actorCount: Int, onChange: (SearchFilters) -> Unit) {
    if (!filters.isActive) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filters.type != SearchTypeFilter.ALL) {
            RemovableChip(filters.type.label) { onChange(filters.copy(type = SearchTypeFilter.ALL)) }
        }
        filters.actor?.let { a ->
            val count = if (actorCount == 1) "1 filme" else "$actorCount filmes"
            RemovableChip(if (actorCount > 0) "$a \u2022 $count" else a) { onChange(filters.copy(actor = null)) }
        }
        filters.genres.forEach { g -> RemovableChip(g) { onChange(filters.copy(genres = filters.genres - g)) } }
        filters.years.sortedDescending().forEach { y -> RemovableChip(y.toString()) { onChange(filters.copy(years = filters.years - y)) } }
    }
}

@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x3366D9FF))
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            text,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
        )
        androidx.compose.material3.Icon(
            Icons.Filled.Close,
            contentDescription = "Remover filtro",
            tint = Color(0xFFD8D8D8),
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Painel de filtros: tipo (todos/filmes/séries) e vários gêneros/anos; aplica ao tocar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterSheet(
    filters: SearchFilters,
    options: SearchFilterOptions,
    onChange: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181818)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    "Filtros",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
                if (filters.isActive) {
                    androidx.compose.material3.Text(
                        "Limpar",
                        color = BRAND_ACCENT,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onChange(SearchFilters()) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            FilterSection("Tipo") {
                SearchTypeFilter.entries.forEach { t ->
                    SelectableChip(t.label, filters.type == t) { onChange(filters.copy(type = t)) }
                }
            }
            if (options.genres.isNotEmpty()) {
                FilterSection("Gênero") {
                    options.genres.forEach { g ->
                        SelectableChip(g, g in filters.genres) {
                            onChange(filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g))
                        }
                    }
                }
            }
            if (options.years.isNotEmpty()) {
                FilterSection("Ano") {
                    options.years.forEach { y ->
                        SelectableChip(y.toString(), y in filters.years) {
                            onChange(filters.copy(years = if (y in filters.years) filters.years - y else filters.years + y))
                        }
                    }
                }
            }
            Box(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Text(
            title,
            color = Color(0xFF8E98A8),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Text(
        text,
        color = if (selected) Color.Black else Color(0xFFE0E0E0),
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) BRAND_ACCENT else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** Resultados da busca no celular: grade de 3 colunas só com as capas (séries primeiro). */
@Composable
internal fun TouchSearchResultsGrid(
    query: String,
    searchInProgress: Boolean,
    results: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onLoadMore: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier,
    series: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    onSeriesSelect: (com.ntv2.app.feature.media.domain.SeriesSummary) -> Unit = {},
    showCovers: Boolean = true,
    cardLoadingStyle: com.ntv2.app.core.ui.CardLoadingStyle = com.ntv2.app.core.ui.CardLoadingStyle.DEFAULT,
    animationsEnabled: Boolean = true,
    // Gênero/ano ativos listam mídias mesmo sem texto digitado.
    filtersActive: Boolean = false
) {
    val hidden = (query.isBlank() && !filtersActive) || searchInProgress
    val visibleResults = remember(hidden, results) { if (hidden) emptyList() else results }
    val visibleSeries = remember(hidden, series) { if (hidden) emptyList() else series }
    val totalCovers = visibleSeries.size + visibleResults.size
    val shouldLoadMore by remember(totalCovers) {
        androidx.compose.runtime.derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalCovers > 0 && last >= totalCovers - 6
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) onLoadMore()
    }

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
        state = gridState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(visibleSeries, key = { "series_${it.tmdbId}" }) { s ->
            SearchCoverCell(
                cover = if (showCovers) (s.posterUrl ?: s.backdropUrl)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                description = s.title,
                cardLoadingStyle = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                onClick = { onSeriesSelect(s) }
            )
        }
        items(visibleResults, key = { it.mediaId }) { media ->
            SearchCoverCell(
                cover = if (showCovers) (media.posterPath ?: media.thumbnailPath)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                description = media.title,
                cardLoadingStyle = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                onClick = { onSelect(media) }
            )
        }
        if (totalCovers > 0) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(
                            color = BRAND_ACCENT,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (!hasMore) {
                        androidx.compose.material3.Text(
                            "Fim da lista",
                            color = Color(0xFF6E6E6E),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        if (searchInProgress && (query.isNotBlank() || filtersActive)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = BRAND_ACCENT,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        androidx.compose.material3.Text(
                            "Pesquisando…",
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else if (totalCovers == 0 && (query.isNotBlank() || filtersActive)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "Nenhum resultado encontrado",
                        color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchCoverCell(
    cover: String?,
    description: String,
    cardLoadingStyle: com.ntv2.app.core.ui.CardLoadingStyle,
    animationsEnabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .clickable(onClick = onClick)
    ) {
        if (cover != null) {
            com.ntv2.app.core.ui.CoverWithLoading(
                cover = cover,
                contentDescription = description,
                style = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Sem capa (opção desligada ou sem arte): mostra o nome, como na grade da TV.
            Text(
                description,
                color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp)
            )
        }
    }
}

/**
 * Resultados da busca da TV: grade de 3 colunas só com as capas (séries primeiro), igual ao celular.
 * Mesmas regras de exibição (esconde durante a busca, "Pesquisando…", "Nenhum resultado encontrado",
 * "Fim da lista") e paginação infinita ao aproximar do fim. [cellModifier] recebe o índice da célula
 * na grade (séries + filmes) e o id dela, para a TV anexar foco/D-pad.
 */
@Composable
internal fun SearchResultsList(
    query: String,
    searchInProgress: Boolean,
    results: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    listState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onLoadMore: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier,
    cellModifier: (index: Int, id: String) -> Modifier = { _, _ -> Modifier },
    series: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    onSeriesSelect: (com.ntv2.app.feature.media.domain.SeriesSummary) -> Unit = {},
    showCovers: Boolean = true,
    columns: Int = 3
) {
    val visibleResults = remember(query, searchInProgress, results) {
        if (query.isBlank() || searchInProgress) emptyList() else results
    }
    val visibleSeries = remember(query, searchInProgress, series) {
        if (query.isBlank() || searchInProgress) emptyList() else series
    }
    val totalCovers = visibleSeries.size + visibleResults.size
    // Paginação infinita: dispara ao aproximar do fim da grade.
    val shouldLoadMore by remember(totalCovers) {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalCovers > 0 && last >= totalCovers - 6
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) onLoadMore()
    }

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(visibleSeries, key = { _, s -> "series_${s.tmdbId}" }) { i, s ->
            TvCoverCell(
                cover = if (showCovers) (s.posterUrl ?: s.backdropUrl)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                title = s.title,
                modifier = cellModifier(i, "series_${s.tmdbId}"),
                onClick = { onSeriesSelect(s) }
            )
        }
        itemsIndexed(visibleResults, key = { _, m -> m.mediaId }) { j, media ->
            TvCoverCell(
                cover = if (showCovers) (media.posterPath ?: media.thumbnailPath)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                title = media.title,
                modifier = cellModifier(visibleSeries.size + j, media.mediaId),
                onClick = { onSelect(media) }
            )
        }
        if (totalCovers > 0 && (loadingMore || !hasMore)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(color = BRAND_ACCENT, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    } else {
                        androidx.compose.material3.Text(
                            "Fim da lista",
                            color = Color(0xFF6E6E6E),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        if (searchInProgress && query.isNotBlank()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = BRAND_ACCENT, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        androidx.compose.material3.Text(
                            "Pesquisando…",
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else if (totalCovers == 0 && query.isNotBlank()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "Nenhum resultado encontrado",
                        color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/** Capa focável da grade da TV: borda branca e leve zoom no foco; sem capa, mostra o título. */
@Composable
private fun TvCoverCell(
    cover: String?,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer { val s = if (focused) com.ntv2.app.core.ui.BRAND_FOCUS_SCALE else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(Color(0xFF222222))
            .then(if (focused) Modifier.border(3.dp, BRAND_ACCENT, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            com.ntv2.app.core.ui.CoverWithLoading(
                cover = cover,
                contentDescription = title,
                style = com.ntv2.app.core.ui.CardLoadingStyle.DEFAULT,
                animationsEnabled = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                title,
                color = com.ntv2.app.core.ui.BrandColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
internal fun KeyButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White else Color(0x22FFFFFF))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x44FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            maxLines = 1
        )
    }
}
