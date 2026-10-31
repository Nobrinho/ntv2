package com.ntv2.app.core.catalog

import com.ntv2.app.core.telegram.media.MovieMetadataParser
import com.ntv2.app.core.telegram.media.MovieMeta
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCardResolverTest {

    private val chat = -1001234L

    private suspend fun storeWith(vararg rows: CatalogRow) = InMemoryCatalogStore().apply { apply(CatalogBatch(upserts = rows.toList())) }

    private fun movie(tmdb: Long, title: String, msg: Long?) = CatalogMerge.rowOf(
        KIND_MOVIE, NtvIds.movie(tmdb), null,
        JSONObject().put("tmdb_id", tmdb).put("title", title).putOpt("video_message_id", msg).put("chat_id", chat)
            .put("poster_url", "/p.jpg").put("genres", org.json.JSONArray(listOf("Ação", "Ficção")))
            .put("extra", JSONObject().put("director", "Wachowski"))
    )

    @Test
    fun `ntv_id ganha da mensagem e a mensagem aceita o id do TDLib`() = runTest {
        val store = storeWith(movie(603, "Matrix", 50), movie(604, "Outro", 51))
        val resolver = CatalogCardResolver(store) { true }

        // A legenda v3 diz m_603, mesmo que a mensagem seja a do outro filme.
        assertEquals("Matrix", resolver.metaFor("m_603", chat, 51L shl 20)?.title)
        // Sem ntv_id: pelo (chat, mensagem); TDLib usa id << 20.
        val byMsg = resolver.metaFor(null, chat, 51L shl 20)
        assertEquals("Outro", byMsg?.title)
        // ntv_id que não existe no catálogo cai para a mensagem.
        assertEquals("Matrix", resolver.metaFor("m_999", chat, 50)?.title)
        // Outro canal / mensagem desconhecida: null (quem chama usa a legenda).
        assertNull(resolver.metaFor(null, -1L, 50))
        assertNull(resolver.metaFor(null, chat, 77))
    }

    @Test
    fun `flag desligada nunca usa o catalogo`() = runTest {
        val resolver = CatalogCardResolver(storeWith(movie(603, "Matrix", 50))) { false }
        assertNull(resolver.metaFor("m_603", chat, 50))
    }

    @Test
    fun `filme vira MovieMeta com imagem TMDB e campos de extra`() = runTest {
        val meta = CatalogCardResolver(storeWith(movie(603, "Matrix", 50))) { true }.metaFor("m_603", chat, 0)!!
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", meta.posterUrl)
        assertEquals("Ação, Ficção", meta.genres)
        assertEquals("Wachowski", meta.director)
        assertEquals("603", meta.tmdbId)
        assertEquals("m_603", meta.ntvId)
        assertTrue(meta.isRich)
    }

    @Test
    fun `episodio herda serie e monta o titulo como a legenda rica`() = runTest {
        val series = CatalogMerge.rowOf(
            KIND_SERIES, "s_1399", null,
            JSONObject().put("tmdb_id", 1399).put("title", "Game of Thrones").put("poster_url", "https://x/s.jpg")
                .put("overview", "Sinopse da série").put("genres", org.json.JSONArray(listOf("Drama")))
        )
        val ep = CatalogMerge.rowOf(
            KIND_EPISODE, "e_1399_01_03", null,
            JSONObject().put("series_tmdb_id", 1399).put("season_number", 1).put("episode_number", 3)
                .put("title", "Lord Snow").put("video_message_id", 90).put("chat_id", chat).put("episode_tmdb_id", 63058)
        )
        val meta = CatalogCardResolver(storeWith(series, ep)) { true }.metaFor("e_1399_01_03", chat, 0)!!
        assertEquals("Game of Thrones — S01E03 — Lord Snow", meta.title)
        assertTrue(meta.isEpisode)
        assertEquals("Game of Thrones", meta.seriesTitle)
        assertEquals("https://x/s.jpg", meta.posterUrl)
        assertEquals("Sinopse da série", meta.synopsis)
        assertEquals("Drama", meta.genres)
        assertEquals("1399", meta.seriesTmdbId)
        assertEquals("63058", meta.episodeTmdbId)
        assertEquals(1399L, ep.tmdbId)
    }

    @Test
    fun `ordem do nome do cartao catalogo legenda arquivo e nunca a linha v3`() {
        val v3 = "ntv:m_603 tmdb:603"
        val v3Meta = MovieMetadataParser.parse(v3)
        val catalog = MovieMeta(title = "Matrix")
        val antiga = MovieMetadataParser.parse("Título: Matrix Legenda\nTMDB: 603")

        assertEquals("Matrix", CardResolution.videoName(catalog, antiga, "Matrix (1999)", "x"))
        assertEquals("Matrix Legenda", CardResolution.videoName(null, antiga, "Matrix (1999)", "x"))
        assertEquals("Matrix (1999)", CardResolution.videoName(null, v3Meta, "Matrix (1999)", v3))
        // Sem nome de arquivo: a linha de IDs não vira título (o gateway cai em "Video <id>").
        assertNull(CardResolution.videoName(null, v3Meta, null, v3))
        // Legenda antiga só com texto livre continua usando a 1ª linha.
        assertEquals("Um filme", CardResolution.videoName(null, MovieMeta(), null, "\n Um filme \nmais"))
    }

    @Test
    fun `v3 sem catalogo fica com nome do arquivo e logo da legenda sem parear poster`() = runTest {
        val raw = "ntv:m_603 tmdb:603 logo:/abc.png"
        val caption = MovieMetadataParser.parse(raw)
        // Backend fora e sem cópia local: o catálogo não acha.
        val catalog = CatalogCardResolver(InMemoryCatalogStore()) { true }.metaFor(caption.ntvId, chat, 50)
        assertNull(catalog)
        assertEquals(false, CardResolution.needsPosterPairing(caption))
        assertEquals("Matrix (1999)", CardResolution.videoName(catalog, caption, "Matrix (1999)", raw))
        assertEquals("https://image.tmdb.org/t/p/w500/abc.png", caption.logoUrl)
        // Legenda antiga sem dados completos ainda pareia com o pôster vizinho, como antes.
        assertTrue(CardResolution.needsPosterPairing(MovieMetadataParser.parse("Matrix\nTMDB: 603")))
        assertEquals(false, CardResolution.needsPosterPairing(MovieMetadataParser.parse("Título: Matrix\nSinopse: Neo")))
    }

    @Test
    fun `v3 no catalogo usa o catalogo e guarda a logo da legenda se o catalogo nao tiver`() = runTest {
        val raw = "ntv:m_603 tmdb:603 logo:/abc.png"
        val caption = MovieMetadataParser.parse(raw)
        val catalog = CatalogCardResolver(storeWith(movie(603, "Matrix", 50))) { true }.metaFor(caption.ntvId, chat, 999)!!
        val merged = CardResolution.merge(catalog, caption)
        assertEquals("Matrix", CardResolution.videoName(catalog, caption, "arquivo", raw))
        assertEquals("https://image.tmdb.org/t/p/w500/abc.png", merged.logoUrl)
        assertEquals("Wachowski", merged.director)
    }

    @Test
    fun `merge mantem da legenda o que o catalogo nao tem`() {
        val catalog = MovieMeta(title = "Matrix", posterUrl = "https://c/p.jpg", synopsis = "do catálogo")
        val caption = MovieMeta(title = "Matrix leg", posterUrl = "https://l/p.jpg", director = "Wachowski", rating = 8.7, synopsis = "da legenda")
        val merged = CardResolution.merge(catalog, caption)
        assertEquals("Matrix", merged.title)
        assertEquals("https://c/p.jpg", merged.posterUrl)
        assertEquals("do catálogo", merged.synopsis)
        assertEquals("Wachowski", merged.director)
        assertEquals(8.7, merged.rating!!, 0.0)
    }
}
