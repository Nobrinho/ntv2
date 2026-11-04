package com.ntv2.app.feature.media.data.index

import com.ntv2.app.core.catalog.CatalogBatch
import com.ntv2.app.core.catalog.CatalogCursor
import com.ntv2.app.core.catalog.CatalogMerge
import com.ntv2.app.core.catalog.InMemoryCatalogStore
import com.ntv2.app.core.catalog.KIND_MOVIE
import com.ntv2.app.core.catalog.KIND_SERIES
import com.ntv2.app.core.telegram.media.CastMemberMeta
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIndexSnapshotTest {

    private val chat = -1001L

    private fun movie(tmdb: Long, title: String, year: String, genres: List<String>, cast: List<String>, msg: Long) = IndexMovie(
        tmdbId = tmdb, title = title, originalTitle = null, year = year, posterUrl = "https://p/$tmdb",
        backdropUrl = null, logoUrl = "https://image.tmdb.org/t/p/w500/l$tmdb.png", overview = null, genres = genres,
        cast = cast.map { CastMemberMeta(it, null) }, videoMessageId = msg
    )

    private val original = IndexSnapshot(
        channelId = chat,
        movies = listOf(
            movie(2, "Ação Mortal", "2019", listOf("Ação"), listOf("Keanu Reeves"), 20),
            movie(1, "A Origem", "2010", listOf("Ficção científica", "Ação"), listOf("Leonardo DiCaprio"), 10),
            movie(3, "Eletric Dreams", "2020", listOf("Drama"), emptyList(), 30)
        ),
        series = listOf(
            IndexSeries(
                tmdbId = 1399, title = "Game of Thrones", originalTitle = null, posterUrl = null, backdropUrl = null,
                logoUrl = null, overview = null, genres = listOf("Drama"),
                trailerUrl = "https://youtu.be/abcdefghijk",
                seasons = listOf(
                    IndexSeason(2, listOf(IndexEpisode(null, 2, 1, "O Norte", null, "2012-04-01", 50, null, null, null, null, null, 40))),
                    IndexSeason(1, listOf(
                        IndexEpisode(null, 1, 2, "Estrada", null, "2011-04-24", 55, null, null, null, null, null, 32),
                        IndexEpisode(null, 1, 1, "Inverno", null, "2011-04-17", 60, null, null, null, null, null, 31)
                    ))
                )
            )
        ),
        generatedAtMillis = 1L
    )

    private suspend fun seededStore() = InMemoryCatalogStore().apply {
        apply(CatalogBatch(upserts = IndexCatalogSeed.from(original).rows, cursor = CatalogCursor("s", null, lastSyncAt = 99L)))
    }

    @Test
    fun `catalogo local volta aos mesmos modelos do indice`() = runTest {
        val snap = CatalogIndexSnapshot.load(seededStore())!!
        assertEquals(chat, snap.channelId)
        // Mesma ordem do index.json: título em minúsculas.
        assertEquals(listOf("A Origem", "Ação Mortal", "Eletric Dreams"), snap.movies.map { it.title })
        val origem = snap.movies.first()
        assertEquals("2010", origem.year)
        assertEquals(listOf("Ficção científica", "Ação"), origem.genres)
        assertEquals("Leonardo DiCaprio", origem.cast.single().name)
        assertEquals(10L, origem.videoMessageId)
        assertEquals("https://image.tmdb.org/t/p/w500/l1.png", origem.logoUrl)
        val got = snap.series.single()
        assertEquals(listOf(1, 2), got.seasons.map { it.number })
        assertEquals(listOf(1, 2), got.seasons.first().episodes.map { it.episodeNumber })
        assertEquals(2011, got.year)
        assertEquals("https://youtu.be/abcdefghijk", got.trailerUrl)
        assertEquals(99L, snap.generatedAtMillis)
    }

    @Test
    fun `trailer do filme do indice passa pelo catalogo local e None vira null`() = runTest {
        val com = movie(7, "Com", "2001", emptyList(), emptyList(), 70).copy(trailerUrl = "https://youtu.be/abcdefghijk")
        val sem = movie(8, "Sem", "2002", emptyList(), emptyList(), 80)
        val store = InMemoryCatalogStore().apply {
            apply(CatalogBatch(upserts = IndexCatalogSeed.from(IndexSnapshot(chat, listOf(com, sem), emptyList(), 1L)).rows))
            apply(CatalogBatch(upserts = listOf(
                CatalogMerge.rowOf(KIND_MOVIE, "m_9", null, JSONObject().put("tmdb_id", 9).put("title", "Nulo").put("trailer_url", "None"))
            )))
        }
        val byTitle = CatalogIndexSnapshot.load(store)!!.movies.associateBy { it.title }
        assertEquals("https://youtu.be/abcdefghijk", byTitle.getValue("Com").trailerUrl)
        assertNull(byTitle.getValue("Sem").trailerUrl)
        assertNull(byTitle.getValue("Nulo").trailerUrl)
    }

    @Test
    fun `trailer_url da serie no catalogo vira trailerUrl e None vira null`() = runTest {
        val store = InMemoryCatalogStore()
        store.apply(CatalogBatch(upserts = listOf(
            CatalogMerge.rowOf(KIND_SERIES, "s_1", null, JSONObject().put("tmdb_id", 1).put("title", "Com").put("trailer_url", "https://youtu.be/abcdefghijk")),
            CatalogMerge.rowOf(KIND_SERIES, "s_2", null, JSONObject().put("tmdb_id", 2).put("title", "Sem").put("trailer_url", "None"))
        )))
        val byTitle = CatalogIndexSnapshot.load(store)!!.series.associateBy { it.title }
        assertEquals("https://youtu.be/abcdefghijk", byTitle.getValue("Com").trailerUrl)
        assertNull(byTitle.getValue("Sem").trailerUrl)
    }

    @Test
    fun `le em paginas e catalogo vazio devolve null`() = runTest {
        val store = InMemoryCatalogStore()
        assertNull(CatalogIndexSnapshot.load(store))
        val rows = (1..1203L).map {
            CatalogMerge.rowOf(KIND_MOVIE, "m_$it", null, JSONObject().put("tmdb_id", it).put("title", "Filme $it").put("chat_id", chat))
        }
        store.apply(CatalogBatch(upserts = rows))
        assertEquals(1203, CatalogIndexSnapshot.load(store)!!.movies.size)
    }

    @Test
    fun `busca sobre o catalogo local mantem acento elenco filtros e erro de digitacao sem baixar o indice`() = runTest {
        val store = seededStore()
        var loads = 0
        val repo = SearchIndexRepository(
            // URL inválida: se tentasse baixar, falharia e a busca voltaria vazia.
            indexUrl = "http://invalido.localhost/index.json",
            client = OkHttpClient(),
            localIndex = { loads++; CatalogIndexSnapshot.load(store) }
        )
        assertTrue(repo.covers(chat))
        // Sem acento/caixa.
        assertEquals(listOf("Ação Mortal"), repo.search(chat, "acao mortal").map { it.title })
        // Elenco.
        assertEquals(listOf("A Origem"), repo.searchFiltered(chat, "", emptySet(), emptySet(), actor = "leonardo dicaprio").first.map { it.title })
        assertEquals(1, repo.countByActor(chat, "Keanu Reeves"))
        // Gênero + ano.
        assertEquals(listOf("A Origem", "Ação Mortal"), repo.searchFiltered(chat, "", setOf("acao"), emptySet()).first.map { it.title })
        assertEquals(listOf("Eletric Dreams"), repo.searchFiltered(chat, "", emptySet(), setOf(2020)).first.map { it.title })
        assertEquals(listOf("Game of Thrones"), repo.searchFiltered(chat, "", emptySet(), setOf(2011)).second.map { it.title })
        // Erro de digitação.
        assertEquals(listOf("Eletric Dreams"), repo.search(chat, "electric").map { it.title })
        // Episódio pelo código e filme pela mensagem do vídeo (TDLib << 20).
        assertEquals(listOf("Game of Thrones"), repo.searchSeries(chat, "s01e02").map { it.title })
        assertEquals("A Origem", repo.movieByMessage(chat, 10L shl 20)?.title)
        val (genres, years) = repo.filterOptions(chat)
        assertTrue("Ação" in genres)
        assertEquals(listOf(2020, 2019, 2011, 2010), years)
        // Uma leitura do banco para todas as consultas (cache em memória como antes).
        assertEquals(1, loads)

        // Sync mudou o catálogo: a próxima consulta relê.
        store.apply(CatalogBatch(removed = listOf("m_3")))
        repo.invalidate()
        assertTrue(repo.search(chat, "electric").isEmpty())
        assertEquals(2, loads)
    }

    @Test
    fun `json do backend com ano numerico e cast com profile_url`() = runTest {
        val store = InMemoryCatalogStore()
        val d = JSONObject().put("tmdb_id", 603).put("title", "Matrix").put("year", 1999).put("chat_id", chat)
            .put("logo_url", "/m.png")
            .put("cast", JSONArray().put(JSONObject().put("name", "Keanu").put("character", "Neo").put("profile_url", "https://k")))
        store.apply(CatalogBatch(upserts = listOf(CatalogMerge.rowOf(KIND_MOVIE, "m_603", "t", d))))
        val m = CatalogIndexSnapshot.load(store)!!.movies.single()
        assertEquals("1999", m.year)
        assertEquals("https://k", m.cast.single().photoUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/m.png", m.logoUrl)
    }
}
