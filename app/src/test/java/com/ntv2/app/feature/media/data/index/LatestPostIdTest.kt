package com.ntv2.app.feature.media.data.index

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestPostIdTest {
    private fun ep(season: Int, n: Int, msg: Long) = IndexEpisode(
        null, season, n, null, null, null, null, null, null, null, null, null, msg
    )

    private fun series(id: Long, title: String, vararg msgs: Long) = IndexSeries(
        tmdbId = id, title = title, originalTitle = null, posterUrl = null, backdropUrl = null,
        logoUrl = null, overview = null, genres = emptyList(),
        seasons = listOf(IndexSeason(1, msgs.mapIndexed { i, m -> ep(1, i + 1, m) }))
    )

    @Test
    fun `serie com episodio postado mais recente vem primeiro, sem post vai ao fim`() {
        val lista = listOf(series(1, "A", 10, 20), series(2, "Bob Esponja", 5, 99), series(3, "C"), series(4, "D", 50))
        assertEquals(listOf(2L, 4L, 1L, 3L), lista.sortedByDescending { latestPostId(it) }.map { it.tmdbId })
        assertEquals(0L, latestPostId(series(5, "vazia")))
    }
}
