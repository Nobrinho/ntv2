package com.ntv2.app.feature.media.data.index

import com.ntv2.app.core.catalog.CatalogMetaMapper
import com.ntv2.app.core.telegram.media.CastMemberMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexCatalogSeedTest {

    private fun movie(tmdb: Long, msg: Long) = IndexMovie(
        tmdbId = tmdb, title = "Matrix", originalTitle = "The Matrix", year = "1999", posterUrl = "https://p",
        backdropUrl = null, logoUrl = null, overview = "Neo", genres = listOf("Ação"),
        cast = listOf(CastMemberMeta("Keanu", "https://k")), videoMessageId = msg
    )

    private fun episode(s: Int, e: Int, msg: Long) = IndexEpisode(
        episodeTmdbId = 63058, seasonNumber = s, episodeNumber = e, title = "Lord Snow", overview = null,
        airDate = "2011-05-01", durationMin = 58, posterUrl = null, backdropUrl = null, logoUrl = null,
        quality = "1080p", audio = "Dublado", videoMessageId = msg
    )

    @Test
    fun `gera os mesmos ntv_id do backend e o canal do indice`() {
        val series = IndexSeries(
            tmdbId = 1399, title = "Game of Thrones", originalTitle = null, posterUrl = null, backdropUrl = null,
            logoUrl = null, overview = null, genres = emptyList(),
            seasons = listOf(IndexSeason(1, listOf(episode(1, 3, 90))))
        )
        val seed = IndexCatalogSeed.from(IndexSnapshot(-100123L, listOf(movie(603, 50), movie(0, 51)), listOf(series), 42L))

        assertEquals(listOf("m_603", "s_1399", "e_1399_01_03"), seed.rows.map { it.ntvId })
        assertEquals(42L, seed.generatedAtMillis)
        val ep = seed.rows.last()
        assertEquals(-100123L, ep.chatId)
        assertEquals(90L, ep.videoMessageId)
        assertEquals(1399L, ep.tmdbId)

        // A linha do índice passa pelo mesmo mapeamento das linhas do backend.
        val meta = CatalogMetaMapper.toMeta(seed.rows.first())!!
        assertEquals("Matrix", meta.title)
        assertEquals(1999, meta.year)
        assertEquals("Keanu", meta.cast.single().name)
        assertEquals("https://k", meta.cast.single().photoUrl)
    }
}
