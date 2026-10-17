package com.ntv2.app.core.library

import com.ntv2.app.core.backend.sync.LocalChange
import com.ntv2.app.core.database.InMemoryFavoriteDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomUserLibraryRepositoryTest {

    private fun media(id: String) = FavoriteMedia(
        mediaId = id, channelId = 1L, channelTitle = "Canal", title = "Filme $id",
        posterPath = null, thumbnailPath = null, durationSeconds = 6000, tmdbId = "42", genres = "Ação",
        addedAt = 0L
    )

    @Test
    fun `toggle adiciona quando ausente e remove quando presente`() = runTest {
        val repo = RoomUserLibraryRepository(InMemoryFavoriteDao())

        assertTrue(repo.toggle(media("a")))   // agora favoritado
        assertTrue(repo.isFavorite("a"))

        assertFalse(repo.toggle(media("a")))  // desfavoritado
        assertFalse(repo.isFavorite("a"))
    }

    @Test
    fun `observeFavorites reflete a lista com mais recente primeiro`() = runTest {
        val repo = RoomUserLibraryRepository(InMemoryFavoriteDao())
        repo.add(media("a").copy(addedAt = 100L))
        repo.add(media("b").copy(addedAt = 200L))

        val list = repo.observeFavorites().first()
        assertEquals(listOf("b", "a"), list.map { it.mediaId })
    }

    @Test
    fun `add preenche addedAt quando zero`() = runTest {
        val dao = InMemoryFavoriteDao()
        RoomUserLibraryRepository(dao).add(media("a"))
        assertTrue(dao.rows.getValue("a").addedAt > 0L)
    }

    @Test
    fun `desfavoritar vira tombstone e refavoritar revive`() = runTest {
        val dao = InMemoryFavoriteDao()
        var clock = 100L
        val changes = mutableListOf<LocalChange>()
        val repo = RoomUserLibraryRepository(dao, now = { clock }, onLocalChange = { changes += it })

        repo.add(media("a"))
        clock = 200L
        repo.remove("a")

        assertFalse(repo.isFavorite("a"))
        assertTrue(repo.observeFavorites().first().isEmpty())
        assertTrue(repo.observeFavoriteIds().first().isEmpty())
        assertTrue(dao.rows.getValue("a").deleted)
        assertEquals(200L, dao.rows.getValue("a").updatedAt)

        clock = 300L
        assertTrue(repo.toggle(media("a")))
        assertFalse(dao.rows.getValue("a").deleted)
        assertEquals(300L, dao.rows.getValue("a").updatedAt)
        assertEquals(listOf(LocalChange.EDIT, LocalChange.EDIT, LocalChange.EDIT), changes)
    }
}
