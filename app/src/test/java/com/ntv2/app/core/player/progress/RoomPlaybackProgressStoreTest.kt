package com.ntv2.app.core.player.progress

import com.ntv2.app.core.backend.sync.LocalChange
import com.ntv2.app.core.database.InMemoryHistoryDao
import com.ntv2.app.core.database.InMemoryProgressDao
import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
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
        override suspend fun markDeleted(mediaId: String, updatedAt: Long) { deleted += mediaId }
        override suspend fun getForSync(mediaId: String): PlaybackProgressEntity? = null
        override suspend fun changedSince(sinceMs: Long): List<PlaybackProgressEntity> = emptyList()
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
        val hist = InMemoryHistoryDao()
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
        val hist = InMemoryHistoryDao()
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
        val hist = InMemoryHistoryDao()
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
        val hist = InMemoryHistoryDao()
        val store = RoomPlaybackProgressStore(RecordingDao(emptyMap()), hist)

        store.recordOpened(meta("a"))
        store.onProgress("a", positionMs = 40 * 60_000L, durationMs = 7200 * 1_000L)
        // Reabrir o mesmo título não pode zerar a posição.
        store.recordOpened(meta("a"))

        assertEquals(40 * 60_000L, store.history().first().lastPositionMs)
    }

    @Test
    fun `remover do historico apaga do historico e do progresso`() = runTest {
        val hist = InMemoryHistoryDao()
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
        val hist = InMemoryHistoryDao()
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

    @Test
    fun `remocoes viram tombstone com updatedAt e avisam a sincronizacao`() = runTest {
        val hist = InMemoryHistoryDao()
        val prog = InMemoryProgressDao()
        val changes = mutableListOf<LocalChange>()
        var clock = 1_000L
        val store = RoomPlaybackProgressStore(prog, hist, now = { clock }, onLocalChange = { changes += it })

        store.recordOpened(meta("a"))
        clock = 2_000L
        store.onProgress("a", positionMs = 30 * 60_000L, durationMs = 7200 * 1_000L)
        clock = 3_000L
        store.removeFromHistory("a")

        // Some da UI, mas as linhas ficam marcadas para a remoção chegar aos outros aparelhos.
        assertTrue(store.history().isEmpty())
        assertEquals(0L, store.resumePositionMs("a", 7200 * 1_000L))
        assertTrue(hist.rows.getValue("a").deleted)
        assertEquals(3_000L, hist.rows.getValue("a").updatedAt)
        assertTrue(prog.rows.getValue("a").deleted)
        assertEquals(3_000L, prog.rows.getValue("a").updatedAt)
        assertEquals(listOf(LocalChange.EDIT, LocalChange.PROGRESS, LocalChange.EDIT), changes)
    }

    @Test
    fun `tick nao ressuscita historico removido, mas reabrir sim`() = runTest {
        val hist = InMemoryHistoryDao()
        val store = RoomPlaybackProgressStore(InMemoryProgressDao(), hist)
        store.recordOpened(meta("a"))
        store.clearHistory()
        store.onProgress("a", positionMs = 30 * 60_000L, durationMs = 7200 * 1_000L)
        assertTrue(store.history().isEmpty())

        store.recordOpened(meta("a"))
        assertEquals(listOf("a"), store.history().map { it.mediaId })
        assertFalse(hist.rows.getValue("a").deleted)
        // Removido antes: volta sem a posição antiga.
        assertEquals(0L, store.history().first().lastPositionMs)
    }

    @Test
    fun `assistido ate o fim apaga o progresso via tombstone`() = runTest {
        val prog = InMemoryProgressDao()
        val store = RoomPlaybackProgressStore(prog, now = { 5_000L })
        store.onProgress("a", positionMs = 30 * 60_000L, durationMs = 7200 * 1_000L)
        store.onProgress("a", positionMs = 7200 * 1_000L - 1_000L, durationMs = 7200 * 1_000L)
        assertTrue(prog.rows.getValue("a").deleted)
        assertEquals(emptyMap<String, Long>(), store.savedPositions(listOf("a")))
    }
}
