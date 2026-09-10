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
        override val minDurationMinutes: Flow<Int>
    ) : SettingsRepository {
        override suspend fun updateMinDurationMinutes(value: Int) = Unit
    }

    private class FakeMediaRepo : MediaRepository {
        var listPages: (channelId: Long, cursor: Long) -> MediaPage = { _, _ -> MediaPage(emptyList(), 0L) }
        var searchPages: (channelId: Long, query: String, cursor: Long) -> MediaPage = { _, _, _ -> MediaPage(emptyList(), 0L) }

        override suspend fun fetchChannelVideos(channelId: Long, channelTitle: String, fromMessageId: Long, limit: Int): MediaPage =
            listPages(channelId, fromMessageId)

        override suspend fun searchChannelVideos(channelId: Long, channelTitle: String, query: String, fromMessageId: Long, limit: Int): MediaPage =
            searchPages(channelId, query, fromMessageId)
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
        progressStore: PlaybackProgressStore = FakeProgressStore()
    ) = MediaLibraryViewModel(
        mediaRepository = media,
        channelRepository = FakeChannelRepo(channelsFlow),
        settingsRepository = FakeSettingsRepo(minDurationFlow),
        progressStore = progressStore,
        ioDispatcher = dispatcher
    )

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
        val media = FakeMediaRepo().apply {
            listPages = { channelId, cursor ->
                if (cursor == 0L) {
                    MediaPage(listOf(item("a", channelId, 600, 1)), nextCursor = 100L)
                } else {
                    MediaPage(listOf(item("b", channelId, 600, 2)), nextCursor = 0L)
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
        assertEquals(2, section.items.size)
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
    fun `busca substitui a fonte pelos resultados do servidor`() = runTest(dispatcher) {
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

        val titles = vm.uiState.value.sections.flatMap { it.items }.map { it.title }
        assertTrue("esperava resultado de busca do servidor, veio $titles", titles.any { it.contains("found-xyz") })
    }
}
