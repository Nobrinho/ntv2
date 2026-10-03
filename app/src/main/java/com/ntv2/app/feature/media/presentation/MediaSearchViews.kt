package com.ntv2.app.feature.media.presentation

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
                color = BRAND_GREEN,
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
            color = Color(0xFFB0B0B0),
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
    // false na busca por elenco: teclado e campo ficam esmaecidos (o screen ignora as teclas).
    textEnabled: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
    val currentResults by androidx.compose.runtime.rememberUpdatedState(results)

    fun focusKeyboard() {
        runCatching { keyRequester(lastKeyId).requestFocus() }
            .onFailure { runCatching { keyRequester(firstKeyId).requestFocus() } }
    }

    fun focusResult(index: Int) {
        val list = currentResults
        if (list.isEmpty()) return
        val i = index.coerceIn(0, list.lastIndex)
        scope.launch {
            // O item pode não estar composto (lazy): rola até ele antes de pedir foco.
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == i }) {
                listState.scrollToItem(i)
                withFrameNanos { }
            }
            runCatching { resultRequester(list[i].mediaId).requestFocus() }
        }
    }

    // Foco inicial: volta ao resultado que abriu os Detalhes; senão, 1ª tecla.
    LaunchedEffect(Unit) {
        val index = restoreFocusMediaId?.let { id -> results.indexOfFirst { it.mediaId == id } } ?: -1
        onRestoreConsumed()
        if (index >= 0) {
            lastResultIndex = index
            listState.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
            runCatching { resultRequester(results[index].mediaId).requestFocus() }
                .onFailure { focusKeyboard() }
        } else {
            runCatching { keyRequester(firstKeyId).requestFocus() }
        }
    }

    // Nova consulta com o foco na lista: o item focado deixa de existir — devolve ao teclado.
    LaunchedEffect(query) {
        lastResultIndex = 0
        if (focusInResults) focusKeyboard()
    }

    LaunchedEffect(focusResultsWhenReady, searchInProgress, results) {
        if (!focusResultsWhenReady || searchInProgress) return@LaunchedEffect
        focusResultsWhenReady = false
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
            withFrameNanos { }
            focusResult(0)
        }
    }

    fun typeChar(c: Char) {
        onKey(c)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .onPreviewKeyEvent { e ->
                when {
                    // BACK em camadas: resultados → teclado; teclado → fecha a busca.
                    e.key == Key.Back || e.key == Key.Escape -> {
                        if (e.type == KeyEventType.KeyUp) {
                            if (focusInResults) focusKeyboard() else onClose()
                        }
                        true
                    }
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
            // ── Coluna esquerda: campo + teclado ──
            BoxWithConstraints(
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
                            color = if (query.isEmpty() || !textEnabled) Color(0xFF9A9A9A) else Color.White,
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
                            if (currentResults.isNotEmpty()) focusResult(lastResultIndex)
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

            // ── Coluna direita: resultados ao vivo ──
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
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF))
                    .focusGroup()
                    .onFocusChanged { focusInResults = it.hasFocus },
                rowModifier = { index, media ->
                    Modifier
                        .focusRequester(resultRequester(media.mediaId))
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastResultIndex = index
                                // Paginação pelo foco: perto do fim, pede a próxima página.
                                if (index >= currentResults.size - 3 && hasMore && !loadingMore) onLoadMore()
                            }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionLeft -> { focusKeyboard(); true }
                                Key.DirectionUp -> index == 0
                                Key.DirectionDown -> index == currentResults.lastIndex
                                else -> false
                            }
                        }
                }
            )
        }
    }
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

    // Busca por elenco (toque num ator): o texto fica desabilitado, o filtro é o ator.
    val actorMode = filters.actor != null
    LaunchedEffect(Unit) {
        if (!actorMode) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }
    BackHandler(enabled = true) { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
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
                    .clip(RoundedCornerShape(25.dp))
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
                                    color = Color(0xFF9A9A9A),
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
                        .background(if (filters.isActive) BRAND_GREEN else Color(0xFF252525))
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
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x332BEE34))
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
                        color = BRAND_GREEN,
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
            color = Color(0xFF8A8A8A),
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
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) BRAND_GREEN else Color(0xFF2A2A2A))
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
                cover = s.posterUrl ?: s.backdropUrl,
                description = s.title,
                cardLoadingStyle = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                onClick = { onSeriesSelect(s) }
            )
        }
        items(visibleResults, key = { it.mediaId }) { media ->
            SearchCoverCell(
                cover = media.posterPath ?: media.thumbnailPath,
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
                            color = BRAND_GREEN,
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
                            color = BRAND_GREEN,
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
                        color = Color(0xFFB0B0B0),
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
        }
    }
}

/**
 * Lista de resultados compartilhada por TV e mobile: mesmas regras de exibição (esconde durante a
 * busca, "Pesquisando…", "Nenhum resultado encontrado", "Fim da lista") e paginação infinita ao
 * aproximar do fim. [rowModifier] permite à TV anexar foco/D-pad a cada linha.
 */
@Composable
internal fun SearchResultsList(
    query: String,
    searchInProgress: Boolean,
    results: List<MediaCardUi>,
    hasMore: Boolean,
    loadingMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit,
    onSelect: (MediaCardUi) -> Unit,
    modifier: Modifier = Modifier,
    rowModifier: (index: Int, media: MediaCardUi) -> Modifier = { _, _ -> Modifier },
    series: List<com.ntv2.app.feature.media.domain.SeriesSummary> = emptyList(),
    onSeriesSelect: (com.ntv2.app.feature.media.domain.SeriesSummary) -> Unit = {}
) {
    val visibleResults = remember(query, searchInProgress, results) {
        if (query.isBlank() || searchInProgress) emptyList() else results
    }
    val visibleSeries = remember(query, searchInProgress, series) {
        if (query.isBlank() || searchInProgress) emptyList() else series
    }
    // Paginação infinita: dispara ao aproximar do fim da lista.
    val shouldLoadMore by remember(visibleResults.size) {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            visibleResults.isNotEmpty() && last >= visibleResults.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) onLoadMore()
    }

    // Feedback de busca fica só no centro (spinner "Pesquisando…" / "Nenhum resultado"),
    // sem duplicar um status no topo. Lista com paginação infinita.
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        if (visibleSeries.isNotEmpty()) {
            item(key = "series_header") {
                androidx.compose.material3.Text(
                    "Séries",
                    color = Color(0xFF8A8A8A),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            visibleSeries.forEach { s ->
                item(key = "series_${s.tmdbId}") {
                    SeriesResultRow(series = s, onClick = { onSeriesSelect(s) })
                }
            }
        }
        visibleResults.forEachIndexed { index, media ->
            item(key = media.mediaId) {
                SearchResultRow(
                    media = media,
                    modifier = rowModifier(index, media),
                    onClick = { onSelect(media) }
                )
            }
        }
        // Rodapé: spinner ao carregar mais; "Fim da lista" discreto quando acabou.
        if (visibleResults.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(
                            color = BRAND_GREEN,
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
        if (searchInProgress && query.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = BRAND_GREEN,
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
        } else if (visibleResults.isEmpty() && visibleSeries.isEmpty() && query.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "Nenhum resultado encontrado",
                        color = Color(0xFFB0B0B0),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun SeriesResultRow(
    series: com.ntv2.app.feature.media.domain.SeriesSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val episodeCount = series.seasons.sumOf { it.episodes.size }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x26FFFFFF) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                series.title,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            androidx.compose.material3.Text(
                "Série • ${series.seasons.size} temp. • $episodeCount ep.",
                color = Color(0xFF8A8A8A),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
            )
        }
        val thumb = series.posterUrl ?: series.backdropUrl
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(58.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222222))
            )
        }
    }
}

@Composable
internal fun SearchResultRow(
    media: MediaCardUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Destaque de foco só aparece com D-pad (no toque o clickable não recebe foco).
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Recuo + cantos arredondados: o destaque acompanha o raio da lista (antes era
            // quadrado por dentro e cortado pelo arredondado de fora).
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color(0x26FFFFFF) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            media.title,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val thumb = media.posterPath ?: media.thumbnailPath
        if (thumb != null) {
            // Mostra o pôster na proporção original: paisagem (H) fica largo, retrato (V) fica em pé.
            AsyncImage(
                model = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(58.dp)
                    .aspectRatio(media.gridAspectRatio(true))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222222))
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
