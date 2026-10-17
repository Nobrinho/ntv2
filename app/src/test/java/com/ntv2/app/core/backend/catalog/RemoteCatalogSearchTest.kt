package com.ntv2.app.core.backend.catalog

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendRequest
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.feature.media.data.index.CatalogKind
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogSearchTest {

    private val searchJson = """
        {
          "movies": [{
            "tmdb_id": 603, "title": "Matrix", "original_title": "The Matrix", "year": 1999,
            "poster_url": "https://img/p.jpg", "backdrop_url": null, "logo_url": "/logo.png",
            "overview": "", "genres": ["Ação", "Ficção científica"],
            "cast": [{"name": "Keanu Reeves", "character": "Neo", "profile_url": "https://img/k.jpg"}, {"name": ""}],
            "video_message_id": 1234, "search_key": "matrix the matrix"
          }],
          "series": [{
            "tmdb_id": 1396, "title": "Breaking Bad", "original_title": null, "year": 2008,
            "poster_url": "https://img/bb.jpg", "logo_url": "https://img/bb-logo.png", "genres": ["Drama"], "cast": []
          }],
          "total": 2
        }
    """.trimIndent()

    private val episodesJson = """
        [
          {"number": 2, "episodes": [
            {"series_tmdb_id": 1396, "season_number": 2, "episode_number": 1, "title": "Seven Thirty-Seven",
             "air_date": "2009-03-08", "duration_min": 47, "quality": "1080p", "audio": "Dual", "video_message_id": 900}
          ]},
          {"number": 1, "episodes": [
            {"season_number": 1, "episode_number": 2, "title": "Cat's in the Bag...", "air_date": "2008-01-27", "video_message_id": 802},
            {"season_number": 1, "episode_number": 1, "episode_tmdb_id": 62085, "title": "Pilot", "air_date": "2008-01-20",
             "logo_url": "/ep.png", "video_message_id": 801}
          ]}
        ]
    """.trimIndent()

    @Test
    fun `parse do search_catalog vira IndexMovie e IndexSeries`() {
        val page = parseSearchCatalog(searchJson)
        assertEquals(2, page.total)
        val m = page.movies.single()
        assertEquals(603L, m.tmdbId)
        assertEquals("Matrix", m.title)
        assertEquals("The Matrix", m.originalTitle)
        assertEquals("1999", m.year)
        assertNull(m.backdropUrl)
        assertNull(m.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/logo.png", m.logoUrl)
        assertEquals(listOf("Ação", "Ficção científica"), m.genres)
        assertEquals(1, m.cast.size)
        assertEquals("Keanu Reeves", m.cast[0].name)
        assertEquals("https://img/k.jpg", m.cast[0].photoUrl)
        assertEquals(1234L, m.videoMessageId)

        val s = page.series.single()
        assertEquals(1396L, s.tmdbId)
        assertNull(s.originalTitle)
        assertEquals("https://img/bb-logo.png", s.logoUrl)
        assertTrue(s.seasons.isEmpty())
    }

    @Test
    fun `parse do series_episodes ordena temporadas e episodios`() {
        val seasons = parseSeriesEpisodes(episodesJson)
        assertEquals(listOf(1, 2), seasons.map { it.number })
        val s1 = seasons[0].episodes
        assertEquals(listOf(1, 2), s1.map { it.episodeNumber })
        assertEquals("S01E01", s1[0].code)
        assertEquals(62085L, s1[0].episodeTmdbId)
        assertNull(s1[1].episodeTmdbId)
        assertEquals("https://image.tmdb.org/t/p/w500/ep.png", s1[0].logoUrl)
        val e = seasons[1].episodes.single()
        assertEquals(47, e.durationMin)
        assertEquals("1080p", e.quality)
        assertEquals(900L, e.videoMessageId)
    }

    @Test
    fun `parse aceita listas vazias e campos ausentes`() {
        val page = parseSearchCatalog("""{"movies": [], "series": null}""")
        assertTrue(page.movies.isEmpty())
        assertTrue(page.series.isEmpty())
        assertTrue(parseSeriesEpisodes("[]").isEmpty())
    }

    private class FakeTransport(val responder: (BackendRequest) -> BackendResponse) : HttpTransport {
        val requests = mutableListOf<BackendRequest>()
        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            return responder(request)
        }
    }

    @Test
    fun `busca chama search_catalog com os filtros e completa temporadas das series`() = runTest {
        val transport = FakeTransport { req ->
            when {
                req.url.endsWith("/rpc/search_catalog") -> BackendResponse(200, searchJson)
                req.url.endsWith("/rpc/series_episodes") -> BackendResponse(200, episodesJson)
                else -> BackendResponse(404, "")
            }
        }
        val remote = RemoteCatalogSearch(SupabaseClient(BackendConfig("http://local", "anon"), transport))
        val (movies, series) = remote.search(0L, " matrix ", setOf("Ação"), setOf(1999), null, CatalogKind.MOVIE, 60)

        val body = JSONObject(transport.requests.first().body!!)
        assertEquals("matrix", body.getString("q"))
        assertEquals("movie", body.getString("kind"))
        assertEquals("Ação", body.getString("genre"))
        assertEquals("1999", body.getString("year"))
        assertEquals(60, body.getInt("lim"))
        assertEquals(0, body.getInt("off"))
        assertEquals(1396L, JSONObject(transport.requests[1].body!!).getLong("series_tmdb_id"))

        assertEquals(603L, movies.single().tmdbId)
        assertEquals(2, series.single().seasons.size)
        assertEquals(2008, series.single().year)

        // Temporadas ficam em cache: segunda busca não chama series_episodes de novo.
        remote.search(0L, "matrix", emptySet(), emptySet(), null, CatalogKind.ALL, 60)
        assertEquals(1, transport.requests.count { it.url.endsWith("/rpc/series_episodes") })
        assertTrue(JSONObject(transport.requests.last().body!!).isNull("genre"))
    }

    @Test
    fun `remoto so atende texto com no maximo um genero e um ano e sem ator`() {
        val remote = RemoteCatalogSearch(SupabaseClient(BackendConfig("", "")))
        assertTrue(remote.supports("matrix", setOf("Ação"), setOf(1999), null))
        assertFalse(remote.supports("", setOf("Ação"), emptySet(), null))
        assertFalse(remote.supports("matrix", setOf("Ação", "Drama"), emptySet(), null))
        assertFalse(remote.supports("matrix", emptySet(), setOf(1999, 2000), null))
        assertFalse(remote.supports("matrix", emptySet(), emptySet(), "Keanu Reeves"))
    }

    @Test
    fun `flag remote_search so desliga com false explicito`() {
        assertFalse(parseRemoteSearchFlag("""[{"value": {"remote_search": false, "sync": true}}]"""))
        assertTrue(parseRemoteSearchFlag("""[{"value": {"remote_search": true}}]"""))
        assertTrue(parseRemoteSearchFlag("""[{"value": {"sync": true}}]"""))
        assertTrue(parseRemoteSearchFlag("[]"))
    }

    @Test
    fun `flag le uma vez e fica desligada com backend desligado`() = runTest {
        val transport = FakeTransport { BackendResponse(200, """[{"value": {"remote_search": false}}]""") }
        val flag = RemoteSearchFlag(SupabaseClient(BackendConfig("http://local", "anon"), transport))
        assertFalse(flag.isEnabled())
        assertFalse(flag.isEnabled())
        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().url.endsWith("/rest/v1/app_config?select=value&key=eq.flags"))

        assertFalse(RemoteSearchFlag(SupabaseClient(BackendConfig("", ""))).isEnabled())
    }
}
