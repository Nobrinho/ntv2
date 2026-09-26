package com.ntv2.app.core.library

import com.ntv2.app.core.database.dao.FavoriteDao
import com.ntv2.app.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomUserLibraryRepositoryTest {

    private class FakeFavoriteDao : FavoriteDao {
        val rows = linkedMapOf<String, FavoriteEntity>()
        private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
        private fun publish() { flow.value = rows.values.sortedByDescending { it.addedAt } }
        override fun observeAll(): Flow<List<FavoriteEntity>> = flow
        override fun observeIds(): Flow<List<String>> {
            val f = MutableStateFlow(rows.keys.toList())
            return f
        }
        override suspend fun isFavorite(mediaId: String): Boolean = rows.containsKey(mediaId)
        override suspend fun upsert(entity: FavoriteEntity) { rows[entity.mediaId] = entity; publish() }
        override suspend fun delete(mediaId: String) { rows.remove(mediaId); publish() }
    }

    private fun media(id: String) = FavoriteMedia(
        mediaId = id, channelId = 1L, channelTitle = "Canal", title = "Filme $id",
        posterPath = null, thumbnailPath = null, durationSeconds = 6000, tmdbId = "42", genres = "Ação",
        addedAt = 0L
    )

    @Test
    fun `toggle adiciona quando ausente e remove quando presente`() = runTest {
        val repo = RoomUserLibraryRepository(FakeFavoriteDao())

        assertTrue(repo.toggle(media("a")))   // agora favoritado
        assertTrue(repo.isFavorite("a"))

        assertFalse(repo.toggle(media("a")))  // desfavoritado
        assertFalse(repo.isFavorite("a"))
    }

    @Test
    fun `observeFavorites reflete a lista com mais recente primeiro`() = runTest {
        val repo = RoomUserLibraryRepository(FakeFavoriteDao())
        repo.add(media("a").copy(addedAt = 100L))
        repo.add(media("b").copy(addedAt = 200L))

        val list = repo.observeFavorites().first()
        assertEquals(listOf("b", "a"), list.map { it.mediaId })
    }

    @Test
    fun `add preenche addedAt quando zero`() = runTest {
        val dao = FakeFavoriteDao()
        RoomUserLibraryRepository(dao).add(media("a"))
        assertTrue(dao.rows.getValue("a").addedAt > 0L)
    }
}
