package com.ntv2.app.core.player.progress

import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlaybackProgressStoreTest {

    private class RecordingDao(private val rows: Map<String, Long>) : PlaybackProgressDao {
        var queries = 0
        override suspend fun get(mediaId: String): PlaybackProgressEntity? = null
        override suspend fun getAll(): List<PlaybackProgressEntity> {
            queries++
            return rows.map { (id, pos) -> PlaybackProgressEntity(id, pos, 0L, 0L) }
        }
        override suspend fun upsert(entity: PlaybackProgressEntity) = Unit
        override suspend fun delete(mediaId: String) = Unit
    }

    @Test
    fun `uma consulta so, qualquer que seja o tamanho da grade`() = runTest {
        val ids = (1..1_200).map { "m$it" }
        val dao = RecordingDao(mapOf("m1" to 10L, "m700" to 20L, "fora-da-grade" to 30L))
        val result = RoomPlaybackProgressStore(dao).savedPositions(ids)

        assertEquals(1, dao.queries)
        // Só os da grade: o progresso de um filme que não está na lista fica de fora.
        assertEquals(mapOf("m1" to 10L, "m700" to 20L), result)
    }

    @Test
    fun `lista vazia nao consulta o banco`() = runTest {
        val dao = RecordingDao(mapOf("m1" to 10L))
        assertEquals(emptyMap<String, Long>(), RoomPlaybackProgressStore(dao).savedPositions(emptyList()))
        assertTrue(dao.queries == 0)
    }
}
