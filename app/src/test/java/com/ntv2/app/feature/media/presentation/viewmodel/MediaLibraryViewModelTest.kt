package com.ntv2.app.feature.media.presentation.viewmodel

import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelSummary
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.MediaPage
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.feature.media.domain.MediaRepository
import com.ntv2.app.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaLibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val channelsFlow = MutableStateFlow<List<ChannelSummary>>(emptyList())
    private val minDurationFlow = MutableStateFlow(0)
    private val activeChannelFlow = MutableStateFlow(0L)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeChannelRepo(
        private val channels: Flow<List<ChannelSummary>>
    ) : ChannelRepository {
        override suspend fun fetchEligibleChannels(): List<ChannelSummary> = emptyList()
        override fun observeSelectedChannelIds(): Flow<Set<Long>> = MutableStateFlow(emptySet())
        override fun observeSelectedChannels(): Flow<List<ChannelSummary>> = channels
        override suspend fun persistSelectedChannels(selectedChannels: List<ChannelSummary>) = Unit
        override suspend fun clearSelectedChannels() = Unit
    }

    private class FakeSettingsRepo(
        override val minDurationMinutes: Flow<Int>,
        override val activeChannelId: Flow<Long> = MutableStateFlow(0L),
        override val trailerPreview: Flow<Boolean> = MutableStateFlow(true)
    ) : SettingsRepository {
        override suspend fun updateMinDurationMinutes(value: Int) = Unit
        override suspend fun updateActiveChannelId(value: Long) = Unit
        override val showCovers: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun updateShowCovers(value: Boolean) = Unit
        override val animationsEnabled: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun updateAnimationsEnabled(value: Boolean) = Unit
        override val castPhotos: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun updateCastPhotos(value: Boolean) = Unit
    }

    private class FakeMediaRepo : MediaRepository {
        var listPages: suspend (channelId: Long, cursor: Long) -> MediaPage = { _, _ -> MediaPage(emptyList(), 0L) }
        var searchPages: (channelId: Long, query: String, cursor: Long) -> MediaPage = { _, _, _ -> MediaPage(emptyList(), 0L) }

        override suspend fun fetchChannelVideos(channelId: Long, channelTitle: String, fromMessageId: Long, limit: Int): MediaPage =
            listPages(channelId, fromMessageId)

        override suspend fun searchChannelVideos(channelId: Long, channelTitle: String, query: String, fromMessageId: Long, limit: Int): MediaPage =
            searchPages(channelId, query, fromMessageId)

        var newerPages: (channelId: Long, anchor: Long, limit: Int) -> MediaPage = { _, _, _ -> MediaPage(emptyList(), 0L) }

        override suspend fun fetchNewerChannelVideos(
            channelId: Long,
            channelTitle: String,
            newerThanMessageId: Long,
            limit: Int
        ): MediaPage = newerPages(channelId, newerThanMessageId, limit)

        override suspend fun getVideoByMessage(channelId: Long, channelTitle: String, messageId: Long): MediaItemSummary? = null
    }

    private fun item(id: String, channelId: Long, durationSeconds: Int, fileId: Int) = MediaItemSummary(
        mediaId = id,
        channelId = channelId,
        channelTitle = "C$channelId",
        title = "t$id",
        caption = null,
        fileName = null,
        durationSeconds = durationSeconds,
        thumbnailPath = null,
        fileId = fileId
    )

    private class FakeProgressStore(private val positions: Map<String, Long> = emptyMap()) : PlaybackProgressStore {
        override suspend fun resumePositionMs(mediaId: String, durationMs: Long): Long = 0L
        override suspend fun onProgress(mediaId: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clear(mediaId: String) = Unit
        override suspend fun savedPositions(mediaIds: List<String>): Map<String, Long> =
            positions.filterKeys { it in mediaIds }
    }

    private fun buildViewModel(
        media: MediaRepository,
        progressStore: PlaybackProgressStore = FakeProgressStore(),
        maxRetainedItems: Int = 2_000,
        trailerPreviewSetting: Flow<Boolean> = MutableStateFlow(true),
        trailerPreviewRemote: () -> Boolean = { true }
    ) = MediaLibraryViewModel(
        mediaRepository = media,
        channelRepository = FakeChannelRepo(channelsFlow),
        settingsRepository = FakeSettingsRepo(minDurationFlow, activeChannelFlow, trailerPreviewSetting),
        progressStore = progressStore,
        mediaDetailsCache = com.ntv2.app.feature.media.domain.MediaDetailsCache(),
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher,
        maxRetainedItems = maxRetainedItems,
        trailerPreviewRemote = trailerPreviewRemote
    )

    /** Canal com mensagens 1000 (mais nova) .. 1 (mais antiga), paginado como o TDLib. */
    private fun channelOf(channelId: Long, newest: Long = 1000L) = FakeMediaRepo().apply {
        listPages = { id, cursor ->
            val start = if (cursor == 0L) newest else cursor - 1
            val ids = (start downTo 1L).take(24)
            MediaPage(ids.map { item("${id}_$it", id, 600, it.toInt()) }, nextCursor = if (ids.last() > 1L) ids.last() else 0L)
        }
        newerPages = { id, anchor, limit ->
            val ids = ((anchor + 1)..newest).take(limit).reversed()
            MediaPage(ids.map { item("${id}_$it", id, 600, it.toInt()) }, nextCursor = 0L)
        }
    }

    @Test
    fun `previa de trailer desligada na configuracao desliga o estado`() = runTest(dispatcher) {
        val vm = buildViewModel(FakeMediaRepo(), trailerPreviewSetting = MutableStateFlow(false))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.trailerPreviewEnabled)
    }

    @Test
    fun `previa de trailer com flag remota desligada fica desligada`() = runTest(dispatcher) {
        val vm = buildViewModel(FakeMediaRepo(), trailerPreviewRemote = { false })
        advanceUntilIdle()
        assertFalse(vm.uiState.value.trailerPreviewEnabled)
    }

    @Test
    fun `previa de trailer com configuracao e flag ligadas fica ligada`() = runTest(dispatcher) {
        val vm = buildViewModel(FakeMediaRepo())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.trailerPreviewEnabled)
    }

    @Test
    fun `flag remota virada com a sessao aberta vale na proxima abertura dos detalhes`() = runTest(dispatcher) {
        var remote = true
        val vm = buildViewModel(FakeMediaRepo(), trailerPreviewRemote = { remote })
        advanceUntilIdle()
        assertTrue(vm.uiState.value.trailerPreviewEnabled)
        remote = false
        vm.onAction(MediaLibraryAction.DetailsOpened(
            com.ntv2.app.feature.media.presentation.state.MediaCardUi(
                mediaId = "m", channelId = 1L, channelName = "C", title = "t", caption = null,
                fileName = null, durationSeconds = 600, thumbnailPath = null, fileId = 0
            )
        ))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.trailerPreviewEnabled)
    }

    @Test
    fun `passar do teto descarta o topo e subir busca a pagina de cima de volta`() = runTest(dispatcher) {
        val vm = buildViewModel(channelOf(1L), maxRetainedItems = 48)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()
        vm.onAction(MediaLibraryAction.LoadMore)
        advanceUntilIdle()
        vm.onAction(MediaLibraryAction.LoadMore)
        advanceUntilIdle()

        // 72 carregados, teto 48: o topo (1000..977) foi descartado.
        var state = vm.uiState.value
        assertEquals(48, state.items.size)
        assertEquals("1_976", state.items.first().mediaId)
        assertTrue(state.hasPrevious)

        vm.onAction(MediaLibraryAction.LoadPrevious)
        advanceUntilIdle()
        state = vm.uiState.value
        assertEquals("1_1000", state.items.first().mediaId)
        assertEquals(48, state.items.size)
        // Descartou o fim: "carregar mais" continua do último card mantido.
        assertEquals("1_953", state.items.last().mediaId)

        // Já no início real do canal: nada mais novo, para de pedir a página de cima.
        vm.onAction(MediaLibraryAction.LoadPrevious)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasPrevious)

        vm.onAction(MediaLibraryAction.LoadMore)
        advanceUntilIdle()
        assertEquals("1_976", vm.uiState.value.items.first().mediaId)
    }

    @Test
    fun `trocar de canal comeca da primeira pagina e descarta o carregar mais atrasado do anterior`() = runTest(dispatcher) {
        val slowPage = kotlinx.coroutines.CompletableDeferred<MediaPage>()
        val media = FakeMediaRepo().apply {
            listPages = { channelId, cursor ->
                when {
                    cursor == 0L -> MediaPage((1..24).map { item("${channelId}_$it", channelId, 600, it) }, nextCursor = 1L)
                    channelId == 1L -> slowPage.await() // "carregar mais" do canal 1 fica pendurado
                    else -> MediaPage(emptyList(), 0L)
                }
            }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null), ChannelSummary(2, "C2", null))
        activeChannelFlow.value = 1L
        advanceUntilIdle()
        vm.onAction(MediaLibraryAction.LoadMore)
        advanceUntilIdle()

        activeChannelFlow.value = 2L
        advanceUntilIdle()
        val nonceAfterSwitch = vm.uiState.value.loadMoreNonce
        // A página atrasada do canal 1 chega depois da troca: não pode aparecer nem virar erro.
        slowPage.complete(MediaPage(listOf(item("1_999", 1L, 600, 999)), nextCursor = 0L))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2L, state.activeChannelId)
        assertEquals(24, state.items.size)
        assertTrue(state.items.all { it.mediaId.startsWith("2_") })
        assertEquals("2_1", state.items.first().mediaId)
        assertEquals(null, state.errorMessage)
        // O "terminou de carregar" do canal antigo não chega à tela (ele movia o foco).
        assertEquals(nonceAfterSwitch, state.loadMoreNonce)
    }

    @Test
    fun `abaixo do teto nada e descartado nem ha pagina de cima`() = runTest(dispatcher) {
        val vm = buildViewModel(channelOf(1L))
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()
        repeat(3) {
            vm.onAction(MediaLibraryAction.LoadMore)
            advanceUntilIdle()
        }
        val state = vm.uiState.value
        assertEquals(96, state.items.size)
        assertEquals("1_1000", state.items.first().mediaId)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `carrega primeira pagina e monta secoes`() = runTest(dispatcher) {
        val media = FakeMediaRepo().apply {
            listPages = { channelId, _ -> MediaPage(listOf(item("a", channelId, 600, 1)), nextCursor = 0L) }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.sections.size)
        assertEquals(1, state.sections[0].items.size)
    }

    @Test
    fun `filtra itens abaixo da duracao minima`() = runTest(dispatcher) {
        minDurationFlow.value = 15 // 15 min => 900s
        val media = FakeMediaRepo().apply {
            listPages = { channelId, _ ->
                MediaPage(
                    listOf(
                        item("short", channelId, 60, 1),
                        item("long", channelId, 1200, 2)
                    ),
                    nextCursor = 0L
                )
            }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        val items = vm.uiState.value.sections.flatMap { it.items }
        assertEquals(1, items.size)
        assertEquals("tlong", items[0].title)
    }

    @Test
    fun `load more anexa proxima pagina e atualiza hasMore`() = runTest(dispatcher) {
        // Páginas cheias (30 filmes visíveis): a carga para em uma página e o "Carregar mais" busca a próxima.
        val media = FakeMediaRepo().apply {
            listPages = { channelId, cursor ->
                if (cursor == 0L) {
                    MediaPage((1..30).map { item("a$it", channelId, 600, it) }, nextCursor = 100L)
                } else {
                    MediaPage((31..60).map { item("b$it", channelId, 600, it) }, nextCursor = 0L)
                }
            }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sections[0].hasMore)

        vm.onAction(MediaLibraryAction.LoadMoreChannel(1))
        advanceUntilIdle()

        val section = vm.uiState.value.sections[0]
        assertEquals(60, section.items.size)
        assertFalse(section.hasMore)
    }

    @Test
    fun `pagina com poucos filmes visiveis continua buscando ate preencher`() = runTest(dispatcher) {
        // Canal com blocos de episódios (NBRlib): a 1ª página rende 1 filme; a carga busca as seguintes.
        val requested = mutableListOf<Long>()
        val media = FakeMediaRepo().apply {
            listPages = { channelId, cursor ->
                requested += cursor
                when (cursor) {
                    0L -> MediaPage(listOf(item("a", channelId, 600, 1)), nextCursor = 100L)
                    100L -> MediaPage(listOf(item("b", channelId, 600, 2)), nextCursor = 200L)
                    else -> MediaPage(listOf(item("c", channelId, 600, 3)), nextCursor = 0L)
                }
            }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        val section = vm.uiState.value.sections[0]
        assertEquals(listOf(0L, 100L, 200L), requested)
        assertEquals(3, section.items.size)
        assertFalse(section.hasMore)
    }

    @Test
    fun `card reflete o progresso salvo`() = runTest(dispatcher) {
        val media = FakeMediaRepo().apply {
            listPages = { channelId, _ -> MediaPage(listOf(item("a", channelId, 600, 1)), nextCursor = 0L) }
        }
        // 600s de vídeo, 300s assistidos => 0.5
        val vm = buildViewModel(media, FakeProgressStore(mapOf("a" to 300_000L)))
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        assertEquals(0.5f, vm.uiState.value.sections[0].items[0].progress, 0.001f)
    }

    @Test
    fun `busca popula searchResults com os resultados do servidor sem trocar a biblioteca`() = runTest(dispatcher) {
        val media = FakeMediaRepo().apply {
            listPages = { channelId, _ -> MediaPage(listOf(item("lib", channelId, 600, 1)), nextCursor = 0L) }
            searchPages = { channelId, query, _ -> MediaPage(listOf(item("found-$query", channelId, 600, 9)), nextCursor = 0L) }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()
        assertEquals("tlib", vm.uiState.value.sections[0].items[0].title)

        vm.onAction(MediaLibraryAction.SearchChanged("xyz"))
        advanceUntilIdle()

        // A busca virou um overlay de sugestões: alimenta searchResults, não substitui as sections.
        val encontrados = vm.uiState.value.searchResults.map { it.title }
        assertTrue("esperava resultado de busca do servidor, veio $encontrados", encontrados.any { it.contains("found-xyz") })
        assertEquals("tlib", vm.uiState.value.sections[0].items[0].title)
    }

    @Test
    fun `atualizar desliga o indicador e mantem a grade sem esqueleto`() = runTest(dispatcher) {
        val media = FakeMediaRepo().apply {
            listPages = { channelId, _ -> MediaPage(listOf(item("a", channelId, 600, 1)), nextCursor = 0L) }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        vm.onAction(MediaLibraryAction.Refresh)
        assertTrue(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals(1, vm.uiState.value.items.size)
    }

    @Test
    fun `atualizar com erro tambem desliga o indicador`() = runTest(dispatcher) {
        var fail = false
        val media = FakeMediaRepo().apply {
            listPages = { channelId, _ ->
                if (fail) error("rede") else MediaPage(listOf(item("a", channelId, 600, 1)), nextCursor = 0L)
            }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        fail = true
        vm.onAction(MediaLibraryAction.Refresh)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `carregar mais cancelado pelo atualizar libera o botao`() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val media = FakeMediaRepo().apply {
            listPages = { channelId, cursor ->
                if (cursor == 0L) MediaPage((1..30).map { item("a$it", channelId, 600, it) }, nextCursor = 100L)
                else { gate.await(); MediaPage(emptyList(), 0L) }
            }
        }
        val vm = buildViewModel(media)
        channelsFlow.value = listOf(ChannelSummary(1, "C1", null))
        advanceUntilIdle()

        vm.onAction(MediaLibraryAction.LoadMore)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoadingMore)

        vm.onAction(MediaLibraryAction.Refresh)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoadingMore)
        assertFalse(vm.uiState.value.isRefreshing)
    }
}
