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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import com.ntv2.app.core.ui.NbrType
import coil.compose.AsyncImage
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ntv2.app.feature.media.presentation.state.MediaCardUi
import com.ntv2.app.feature.media.presentation.state.SearchFilterOptions
import com.ntv2.app.feature.media.presentation.state.SearchFilters
import com.ntv2.app.feature.media.presentation.state.SearchTypeFilter
import com.ntv2.app.core.ui.icons.NbrIcons

// Busca da biblioteca: teclado de TV, busca por toque e lista de resultados.

// Teclado da TV do protótipo (TV 08): letras em ordem alfabética, 6 colunas, números no fim.
internal val KEYBOARD_ROWS = listOf(
    "abcdef",
    "ghijkl",
    "mnopqr",
    "stuvwx",
    "yz1234",
    "567890"
)

/** Contagem/estado da busca: 11/400 muted ao lado dos chips da TV ("4 resultados"). */
@Composable
internal fun SearchStatusText(
    query: String,
    resultCount: Int,
    searchInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchInProgress && query.isNotBlank()) {
            CircularProgressIndicator(
                color = BRAND_ACCENT,
                strokeWidth = 2.dp,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = when {
                query.isBlank() -> "Digite algo para buscar"
                searchInProgress -> "Pesquisando…"
                else -> resultCountLabel(resultCount)
            },
            color = com.ntv2.app.core.ui.BrandColors.TextMuted,
            style = NbrType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp),
            maxLines = 1
        )
    }
}

internal fun resultCountLabel(count: Int): String = if (count == 1) "1 resultado" else "$count resultados"

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
    // Pôsteres de ~100 dp como no protótipo (TV 08): 5 colunas ao lado do teclado, 8 em tela cheia.
    val gridColumns = if (textEnabled) 5 else 8
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

    // Voltou dos Detalhes: resultado a refocar. Fica pendente até ele estar na grade (a busca pode
    // ainda estar recarregando ao reabrir); antes, o foco caía no teclado e era preciso voltar à lista.
    var pendingRestore by remember { mutableStateOf(restoreFocusMediaId) }
    LaunchedEffect(pendingRestore, cellIds.size, searchInProgress) {
        val id = pendingRestore ?: return@LaunchedEffect
        val index = currentCells.indexOf(id)
        if (index < 0) {
            // Busca terminou e o item não voltou nos resultados: desiste (vai para o teclado).
            if (!searchInProgress && currentCells.isNotEmpty()) {
                pendingRestore = null
                focusKeyboard()
            }
            return@LaunchedEffect
        }
        lastResultIndex = index
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        // O card pode levar alguns quadros para compor/anexar o FocusRequester: tenta até pegar.
        repeat(12) {
            withFrameNanos { }
            if (focusInResults) {
                pendingRestore = null
                return@LaunchedEffect
            }
            runCatching { resultRequester(id).requestFocus() }
        }
        pendingRestore = null
        if (!focusInResults) focusKeyboard()
    }

    // Foco inicial: com resultado a restaurar, espera por ele (acima); senão, 1ª tecla.
    LaunchedEffect(Unit) {
        onRestoreConsumed()
        if (pendingRestore != null) {
            // Rede lenta: não deixa a tela sem foco para sempre.
            kotlinx.coroutines.delay(2500)
            if (pendingRestore != null && !focusInResults) {
                pendingRestore = null
                focusKeyboard()
            }
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
    val firstQuery = remember { query }
    var queryChanged by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        // A 1ª execução (abrir/reabrir a busca) não é consulta nova: não mexe no foco restaurado.
        if (!queryChanged && query == firstQuery) return@LaunchedEffect
        queryChanged = true
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
        if (overlayHasFocus || showFilters || pendingRestore != null) return@LaunchedEffect
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ── Coluna esquerda (320): título, campo e teclado (fora do modo elenco) ──
            if (textEnabled) Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .focusGroup()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val gap = 6.dp
                val keyWidth = 44.dp
                val keyHeight = 38.dp
                Text("Buscar", style = TvSearchTitleStyle, color = TEXT_PRIMARY)

                // Campo mostrando o que já foi digitado.
                TvSearchField(
                    text = when {
                        !textEnabled -> "Elenco: $query"
                        query.isEmpty() -> "Digite para buscar por título, canal ou arquivo"
                        else -> "$query|"
                    },
                    placeholder = query.isEmpty() || !textEnabled
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
                                TvKey(
                                    label = id.uppercase(),
                                    modifier = Modifier
                                        .then(if (colIndex == row.lastIndex) enterResultsOnRight else Modifier)
                                        .then(keyMod(id))
                                        .width(keyWidth)
                                        .height(keyHeight),
                                    onClick = { lastKeyId = id; typeChar(c) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(gap))

                    // Ações (protótipo): Espaço · ⌫ Apagar · Buscar (accent); depois Limpar · Filtros · Fechar.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        TvKey(
                            label = "Espaço",
                            small = true,
                            modifier = Modifier
                                .then(keyMod("space"))
                                .weight(1f)
                                .height(keyHeight),
                            onClick = { lastKeyId = "space"; typeChar(' ') }
                        )
                        TvKey(
                            label = "⌫ Apagar",
                            small = true,
                            modifier = Modifier
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
                                .height(keyHeight),
                            onClick = { lastKeyId = "backspace"; onBackspace() }
                        )
                        TvKey(
                            label = "Buscar",
                            small = true,
                            primary = true,
                            modifier = enterResultsOnRight
                                .then(keyMod("submit"))
                                .weight(1f)
                                .height(keyHeight),
                            onClick = {
                                lastKeyId = "submit"
                                if (query.isNotBlank()) {
                                    focusResultsWhenReady = true
                                    onSubmit()
                                }
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        TvKey(
                            label = "Limpar",
                            small = true,
                            modifier = Modifier
                                .then(keyMod("clear"))
                                .weight(1f)
                                .height(keyHeight),
                            onClick = { lastKeyId = "clear"; onClear() }
                        )
                        if (filterOptions.isAvailable) {
                            val activeCount = filters.genres.size + filters.years.size +
                                (if (filters.type != SearchTypeFilter.ALL) 1 else 0)
                            TvKey(
                                label = if (activeCount > 0) "Filtros ($activeCount)" else "Filtros",
                                small = true,
                                modifier = Modifier
                                    .then(keyMod("filters"))
                                    .weight(1f)
                                    .height(keyHeight),
                                onClick = { lastKeyId = "filters"; showFilters = true }
                            )
                        }
                        TvKey(
                            label = "Fechar",
                            small = true,
                            modifier = enterResultsOnRight
                                .then(keyMod("close"))
                                .weight(1f)
                                .height(keyHeight),
                            onClick = onClose
                        )
                    }
                }
            }
            // ── Coluna direita: filtros ativos + resultados ao vivo ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            if (textEnabled) {
                TvFilterSummary(
                    filters = filters,
                    options = filterOptions,
                    query = query,
                    resultCount = resultCount,
                    searchInProgress = searchInProgress
                )
            } else {
                val name = filters.actor.orEmpty()
                val count = if (actorCount == 1) "1 filme" else "$actorCount filmes"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (actorCount > 0) "$name · $count" else name,
                        style = TvSearchTitleStyle.copy(fontSize = 24.sp, lineHeight = 29.sp),
                        color = TEXT_PRIMARY,
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

/**
 * Linha acima dos resultados da TV (TV 08): chips Tipo/Gênero/Ano (accent quando há filtro) e a contagem.
 * Só exibição — os filtros são editados no painel aberto pela tecla "Filtros".
 */
@Composable
private fun TvFilterSummary(
    filters: SearchFilters,
    options: SearchFilterOptions,
    query: String,
    resultCount: Int,
    searchInProgress: Boolean
) {
    val showCategories = options.isAvailable
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filters.type != SearchTypeFilter.ALL) SearchChip(filters.type.label, active = true, tv = true)
        else if (showCategories) SearchChip("Tipo", active = false, tv = true)
        filters.actor?.let { SearchChip(it, active = true, tv = true) }
        filters.director?.let { SearchChip("Direção: $it", active = true, tv = true) }
        filters.minImdbLabel?.let { SearchChip(it, active = true, tv = true) }
        filters.minRtLabel?.let { SearchChip(it, active = true, tv = true) }
        if (filters.genres.isNotEmpty()) filters.genres.forEach { SearchChip(it, active = true, tv = true) }
        else if (showCategories && options.genres.isNotEmpty()) SearchChip("Gênero", active = false, tv = true)
        if (filters.years.isNotEmpty()) filters.years.sortedDescending().forEach { SearchChip(it.toString(), active = true, tv = true) }
        else if (showCategories && options.years.isNotEmpty()) SearchChip("Ano", active = false, tv = true)
        SearchStatusText(
            query = query,
            resultCount = resultCount,
            searchInProgress = searchInProgress,
            modifier = Modifier.padding(start = 6.dp)
        )
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
            .background(Color(0xF2090B0F))
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
                .padding(horizontal = 36.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtros", style = TvSearchTitleStyle, color = TEXT_PRIMARY, modifier = Modifier.weight(1f))
                if (filters.isActive) {
                    TvKey("Limpar", Modifier.width(110.dp).height(38.dp), small = true) { onChange(SearchFilters()) }
                }
                TvKey("Concluir", Modifier.width(110.dp).height(38.dp), primary = true, small = true, onClick = onDismiss)
            }
            TvFilterSection("Tipo") {
                SearchTypeFilter.entries.forEach { t ->
                    SearchFilterOptionChip(
                        text = t.label,
                        selected = filters.type == t,
                        tv = true,
                        modifier = if (firstGenre == null && filters.type == t) Modifier.focusRequester(initialFocus) else Modifier
                    ) { onChange(filters.copy(type = t)) }
                }
            }
            if (options.genres.isNotEmpty()) {
                TvFilterSection("Gênero") {
                    options.genres.forEach { g ->
                        SearchFilterOptionChip(
                            text = g,
                            selected = g in filters.genres,
                            tv = true,
                            modifier = if (g == firstGenre) Modifier.focusRequester(initialFocus) else Modifier
                        ) {
                            onChange(filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g))
                        }
                    }
                }
            }
            if (options.years.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ANO", color = com.ntv2.app.core.ui.BrandColors.TextMuted, style = NbrType.Overline)
                    YearAutocompleteFilter(options.years, filters.years.toSet(), tv = true) { y ->
                        onChange(filters.copy(years = if (y in filters.years) filters.years - y else filters.years + y))
                    }
                }
            }
            RatingFilterChips(filters, options, tv = true, onChange = onChange)
        }
    }
}

/** Seção do painel de filtros: rótulo em maiúsculas 11/700 muted + chips. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TvFilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), color = com.ntv2.app.core.ui.BrandColors.TextMuted, style = NbrType.Overline)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

/**
 * Busca do celular (12/13 do protótipo): título "Buscar", campo em pílula, chips Tipo/Gênero/Ano
 * (ativos em accent com "X"), contagem e lista de resultados em cartões.
 */
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

    val totalResults = series.size + suggestions.size
    val showCount = !searchInProgress && totalResults > 0 && (query.isNotBlank() || filters.hasAttribute)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.ntv2.app.core.ui.BrandColors.Background)
            .brandBackdrop(com.ntv2.app.core.ui.BrandBackdropKind.Gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Título "Buscar" 28/700; o voltar fica à esquerda dele.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    NbrIcons.ChevronLeft,
                    contentDescription = "Voltar",
                    tint = TEXT_PRIMARY,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text("Buscar", style = NbrType.ScreenTitle, color = TEXT_PRIMARY)
        }

        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = if (actorMode) "Elenco: ${filters.actor}" else "Digite para buscar por título, canal ou arquivo",
            onSearch = { keyboard?.hide(); onSubmit() },
            onClear = onClear,
            enabled = !actorMode,
            focusRequester = fieldFocus,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        SearchFilterChipsRow(
            filters = filters,
            options = filterOptions,
            actorCount = actorCount,
            onChange = onFiltersChange,
            onOpenFilters = { keyboard?.hide(); showFilterSheet = true }
        )

        if (showCount) {
            Text(
                resultCountLabel(totalResults),
                style = NbrType.Secondary,
                color = com.ntv2.app.core.ui.BrandColors.TextMuted,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

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
                .weight(1f)
                .fillMaxWidth()
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

/**
 * Chips do protótipo (12/13): Tipo, Gênero e Ano abrem o painel de filtros; com filtro aplicado o chip
 * vira o valor em accent com "X" (toque remove). O ator da busca por elenco também aparece aqui.
 */
@Composable
private fun SearchFilterChipsRow(
    filters: SearchFilters,
    options: SearchFilterOptions,
    actorCount: Int,
    onChange: (SearchFilters) -> Unit,
    onOpenFilters: () -> Unit
) {
    val showCategories = options.isAvailable
    if (!showCategories && !filters.isActive) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filters.type != SearchTypeFilter.ALL) {
            SearchChip(filters.type.label, active = true, removable = true) { onChange(filters.copy(type = SearchTypeFilter.ALL)) }
        } else if (showCategories) {
            SearchChip("Tipo", active = false, onClick = onOpenFilters)
        }
        filters.actor?.let { a ->
            val count = if (actorCount == 1) "1 filme" else "$actorCount filmes"
            SearchChip(if (actorCount > 0) "$a · $count" else a, active = true, removable = true) { onChange(filters.copy(actor = null)) }
        }
        filters.director?.let { d ->
            SearchChip("Direção: $d", active = true, removable = true) { onChange(filters.copy(director = null)) }
        }
        filters.minImdbLabel?.let { SearchChip(it, active = true, removable = true) { onChange(filters.copy(minImdb = null)) } }
        filters.minRtLabel?.let { SearchChip(it, active = true, removable = true) { onChange(filters.copy(minRt = null)) } }
        if (filters.genres.isNotEmpty()) {
            filters.genres.forEach { g ->
                SearchChip(g, active = true, removable = true) { onChange(filters.copy(genres = filters.genres - g)) }
            }
        } else if (showCategories && options.genres.isNotEmpty()) {
            SearchChip("Gênero", active = false, onClick = onOpenFilters)
        }
        if (filters.years.isNotEmpty()) {
            filters.years.sortedDescending().forEach { y ->
                SearchChip(y.toString(), active = true, removable = true) { onChange(filters.copy(years = filters.years - y)) }
            }
        } else if (showCategories && options.years.isNotEmpty()) {
            SearchChip("Ano", active = false, onClick = onOpenFilters)
        }
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
        containerColor = com.ntv2.app.core.ui.BrandColors.Surface,
        shape = com.ntv2.app.core.ui.BrandShapes.Sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtros", style = NbrType.SheetTitle, color = TEXT_PRIMARY)
                if (filters.isActive) {
                    Text(
                        "Limpar",
                        color = BRAND_ACCENT,
                        style = NbrType.Chip,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onChange(SearchFilters()) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            FilterSection("Tipo") {
                SearchTypeFilter.entries.forEach { t ->
                    SearchFilterOptionChip(t.label, filters.type == t) { onChange(filters.copy(type = t)) }
                }
            }
            if (options.genres.isNotEmpty()) {
                FilterSection("Gênero") {
                    options.genres.forEach { g ->
                        SearchFilterOptionChip(g, g in filters.genres) {
                            onChange(filters.copy(genres = if (g in filters.genres) filters.genres - g else filters.genres + g))
                        }
                    }
                }
            }
            if (options.years.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ANO", color = com.ntv2.app.core.ui.BrandColors.TextMuted, style = NbrType.Overline)
                    YearAutocompleteFilter(options.years, filters.years.toSet(), tv = false) { y ->
                        onChange(filters.copy(years = if (y in filters.years) filters.years - y else filters.years + y))
                    }
                }
            }
            RatingFilterChips(filters, options, tv = false, onChange = onChange)
            Box(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), color = com.ntv2.app.core.ui.BrandColors.TextMuted, style = NbrType.Overline)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

/** Meta do resultado (filme): canal · duração · partes. */
private fun MediaCardUi.searchMeta(): String = listOfNotNull(
    channelName.takeIf { it.isNotBlank() },
    durationSeconds.takeIf { it > 0 }?.let { durationLabel(it) },
    partCount.takeIf { it > 1 }?.let { "$it partes" }
).joinToString(" · ")

/** Meta do resultado (série): ano · 1º gênero · temporadas. */
private fun com.ntv2.app.feature.media.domain.SeriesSummary.searchMeta(): String = listOfNotNull(
    year?.toString(),
    genres.firstOrNull(),
    seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 temporada" else "$it temporadas" }
).joinToString(" · ")

/** Resultados da busca no celular (13): lista de cartões (séries primeiro). */
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
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(1),
        state = gridState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(visibleSeries, key = { "series_${it.tmdbId}" }) { s ->
            SearchResultRow(
                title = s.title,
                meta = s.searchMeta(),
                badge = "SÉRIE",
                cover = if (showCovers) (s.posterUrl ?: s.backdropUrl)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                cardLoadingStyle = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                onClick = { onSeriesSelect(s) }
            )
        }
        items(visibleResults, key = { it.mediaId }) { media ->
            SearchResultRow(
                title = media.title,
                meta = media.searchMeta(),
                badge = com.ntv2.app.core.player.VideoResolution.label(height = media.videoHeight),
                cover = if (showCovers) (media.posterPath ?: media.thumbnailPath)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                cardLoadingStyle = cardLoadingStyle,
                animationsEnabled = animationsEnabled,
                onClick = { onSelect(media) }
            )
        }
        if (totalCovers > 0) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SearchListFooter(loadingMore = loadingMore, hasMore = hasMore)
            }
        }
        if (searchInProgress && (query.isNotBlank() || filtersActive)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) { SearchingIndicator() }
            }
        } else if (totalCovers == 0 && (query.isNotBlank() || filtersActive)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SearchEmptyPanel(query = query)
            }
        }
    }
}

/**
 * Resultados da busca da TV (TV 08): grade de pôsteres com o título embaixo (séries primeiro).
 * Mesmas regras de exibição (esconde durante a busca, "Pesquisando…", sem resultado, "Fim da lista")
 * e paginação infinita ao aproximar do fim. [cellModifier] recebe o índice da célula na grade
 * (séries + filmes) e o id dela, para a TV anexar foco/D-pad.
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
        // Folga para o zoom e o brilho do foco não serem cortados nas bordas.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(visibleSeries, key = { _, s -> "series_${s.tmdbId}" }) { i, s ->
            TvSearchPoster(
                cover = if (showCovers) (s.posterUrl ?: s.backdropUrl)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                title = s.title,
                modifier = cellModifier(i, "series_${s.tmdbId}"),
                onClick = { onSeriesSelect(s) }
            )
        }
        itemsIndexed(visibleResults, key = { _, m -> m.mediaId }) { j, media ->
            TvSearchPoster(
                cover = if (showCovers) (media.posterPath ?: media.thumbnailPath)?.let { com.ntv2.app.core.ui.gridCoverUrl(it) } else null,
                title = media.title,
                modifier = cellModifier(visibleSeries.size + j, media.mediaId),
                onClick = { onSelect(media) }
            )
        }
        if (totalCovers > 0 && (loadingMore || !hasMore)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SearchListFooter(loadingMore = loadingMore, hasMore = hasMore)
            }
        }
        if (searchInProgress && query.isNotBlank()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    contentAlignment = Alignment.Center
                ) { SearchingIndicator(tv = true) }
            }
        } else if (totalCovers == 0 && query.isNotBlank()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SearchEmptyPanel(query = query, tv = true)
            }
        }
    }
}

/** Tecla genérica do teclado da TV (mantida para quem já a usa): mesmo visual de [TvKey]. */
@Composable
internal fun KeyButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    TvKey(label = label, modifier = modifier, onClick = onClick)
}
