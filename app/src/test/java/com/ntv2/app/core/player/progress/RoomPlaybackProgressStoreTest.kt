package com.ntv2.app.core.player.progress

import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.WatchHistoryDao
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlaybackProgressStoreTest {

    private class RecordingDao(private val rows: Map<String, Long>) : PlaybackProgressDao {
        var queries = 0
        val deleted = mutableListOf<String>()
        override suspend fun get(mediaId: String): PlaybackProgressEntity? = null
        override suspend fun getAll(): List<PlaybackProgressEntity> {
            queries++
            return rows.map { (id, pos) -> PlaybackProgressEntity(id, pos, 0L, 0L) }
        }
        override suspend fun upsert(entity: PlaybackProgressEntity) = Unit
        override suspend fun delete(mediaId: String) { deleted += mediaId }
    }

    /** DAO de histórico em memória. updateProgress é no-op se a linha não existir (como no SQL). */
    private class FakeHistoryDao : WatchHistoryDao {
        val rows = linkedMapOf<String, WatchHistoryEntity>()
        private val flow = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
        private fun publish() { flow.value = rows.values.sortedByDescending { it.updatedAt } }
        override fun observeAll(): Flow<List<WatchHistoryEntity>> = flow
        override suspend fun getAll(): List<WatchHistoryEntity> = rows.values.sortedByDescending { it.updatedAt }
        override suspend fun get(mediaId: String): WatchHistoryEntity? = rows[mediaId]
        override suspend fun upsert(entity: WatchHistoryEntity) { rows[entity.mediaId] = entity; publish() }
        override suspend fun updateProgress(mediaId: String, positionMs: Long, durationMs: Long, completed: Boolean, updatedAt: Long) {
            val e = rows[mediaId] ?: return
            rows[mediaId] = e.copy(lastPositionMs = positionMs, durationMs = durationMs, completed = completed, updatedAt = updatedAt)
            publish()
        }
        override suspend fun delete(mediaId: String) { rows.remove(mediaId); publish() }
        override suspend fun clear() { rows.clear(); publish() }
    }

    private fun meta(id: String, durationSeconds: Int = 7200) = WatchedMediaMeta(
        mediaId = id, channelId = 1L, channelTitle = "Canal", title = "Filme $id",
        posterPath = null, thumbnailPath = null, durationSeconds = durationSeconds, genres = "Drama"
    )

    @Test
    fun `uma consulta so, qualquer que seja o tamanho da grade`() = runTest {
        val ids = (1..1_200).map { "m$it" }
        val dao = RecordingDao(mapOf("m1" to 10L, "m700" to 20L, "fora-da-grade" to 30L))
        val result = RoomPlaybackProgressStore(dao).savedPositions(ids)

        assertEquals(1, dao.queries)
        assertEquals(mapOf("m1" to 10L, "m700" to 20L), result)
    }

    @Test
    fun `lista vazia nao consulta o banco`() = runTest {
        val dao = RecordingDao(mapOf("m1" to 10L))
        assertEquals(emptyMap<String, Long>(), RoomPlaybackProgressStore(dao).savedPositions(emptyList()))
        assertTrue(dao.queries == 0)
    }

    @Test
    fun `recordOpened cria a linha e progresso na janela util vira continuar assistindo`() = runTest {
        val hist = FakeHistoryDao()
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()), hist)

        store.recordOpened(meta("a"))
        // Parou aos 30 min de 2 h: não concluído, na janela útil.
        store.onProgress("a", positionMs = 30 * 60_000L, durationMs = 7200 * 1_000L)

        val cont = store.continueWatching()
        assertEquals(1, cont.size)
        assertEquals("a", cont.first().mediaId)
        assertEquals(30 * 60_000L, cont.first().lastPositionMs)
        assertFalse(cont.first().completed)
    }

    @Test
    fun `perto do fim marca concluido, sai de continuar mas fica no historico`() = runTest {
        val hist = FakeHistoryDao()
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()), hist)

        store.recordOpened(meta("a"))
        // A 2 s do fim (dentro do END_GUARD de 10 s) → concluído.
        store.onProgress("a", positionMs = 7200 * 1_000L - 2_000L, durationMs = 7200 * 1_000L)

        assertTrue(store.continueWatching().isEmpty())
        val h = store.history()
        assertEquals(1, h.size)
        assertTrue(h.first().completed)
    }

    @Test
    fun `posicao no comeco nao entra em continuar assistindo`() = runTest {
        val hist = FakeHistoryDao()
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()), hist)

        store.recordOpened(meta("a"))
        // Abaixo de MIN_POSITION_MS (10 s): recém-aberto, ainda não conta como "continuar".
        store.onProgress("a", positionMs = 3_000L, durationMs = 7200 * 1_000L)

        assertTrue(store.continueWatching().isEmpty())
        // Mas continua no histórico (foi aberto).
        assertEquals(1, store.history().size)
    }

    @Test
    fun `recordOpened preserva a posicao ja assistida`() = runTest {
        val hist = FakeHistoryDao()
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()), hist)

        store.recordOpened(meta("a"))
        store.onProgress("a", positionMs = 40 * 60_000L, durationMs = 7200 * 1_000L)
        // Reabrir o mesmo título não pode zerar a posição.
        store.recordOpened(meta("a"))

        assertEquals(40 * 60_000L, store.history().first().lastPositionMs)
    }

    @Test
    fun `remover do historico apaga do historico e do progresso`() = runTest {
        val hist = FakeHistoryDao()
        val prog = RecordingDao(emptyMap())
        val store = RoomPlaybackProgressStore(prog, hist)

        store.recordOpened(meta("a"))
        store.onProgress("a", positionMs = 30 * 60_000L, durationMs = 7200 * 1_000L)
        store.removeFromHistory("a")

        assertTrue(store.history().isEmpty())
        assertTrue(prog.deleted.contains("a"))
    }

    @Test
    fun `limpar historico esvazia tudo`() = runTest {
        val hist = FakeHistoryDao()
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()), hist)
        store.recordOpened(meta("a"))
        store.recordOpened(meta("b"))
        store.clearHistory()
        assertTrue(store.history().isEmpty())
    }

    @Test
    fun `sem historyDao, historico e continuar ficam vazios (compat)`() = runTest {
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()))
        store.recordOpened(meta("a")) // no-op
        assertTrue(store.history().isEmpty())
        assertTrue(store.continueWatching().isEmpty())
        assertNull(null) // sanidade
    }
}
