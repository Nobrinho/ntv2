package com.ntv2.app.core.telegram.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieMetadataParserTest {

    @Test
    fun `filme com legenda rotulada e sinopse sem rotulo (caso A)`() {
        val caption = """
            🎬| Filme: Fator de Risco
            🎥| Diretor: Austin Stark
            🔊| Áudio: Dual
            📅| Lançamento: 2015
            ⭐| Gêneros: Drama

            Após o trágico derramamento de óleo de 2010 no Golfo do México, um político idealista luta para defender as vítimas locais. No entanto, suas aspirações desmoronam rapidamente quando um grave escândalo sexual destrói sua carreira e seu casamento, forçando-o a confrontar suas próprias falhas.

            @PolemicFilmes | @PolemicMovies | @PolemicHub
        """.trimIndent()

        val meta = MovieMetadataParser.parse(caption)
        assertEquals("Fator de Risco", meta.title)
        assertEquals("Austin Stark", meta.director)
        assertEquals("Dual", meta.audio)
        assertEquals(2015, meta.year)
        assertEquals("Drama", meta.genres)
        assertTrue(meta.synopsis!!.startsWith("Após o trágico derramamento"))
        // Sinopse não deve conter @links.
        assertTrue(!meta.synopsis!!.contains("@Polemic"))
    }

    @Test
    fun `outro filme rotulado (caso B)`() {
        val caption = """
            🎬| Filme: Poder Além da Vida
            🎥| Diretor: Victor Salva
            🔊| Áudio: Dual
            📅| Lançamento: 2006
            ⭐| Gêneros: Drama

            Dan Millman é um atleta que tem dinheiro, mulheres perseguindo-o e aptidões para participar das Olimpíadas. Mas um encontro com Sócrates, um homem que questiona tudo, faz sua visão de mundo mudar completamente.

            @PolemicFilmes | @PolemicMovies | @PolemicHub
        """.trimIndent()

        val meta = MovieMetadataParser.parse(caption)
        assertEquals("Poder Além da Vida", meta.title)
        assertEquals("Victor Salva", meta.director)
        assertEquals(2006, meta.year)
        assertTrue(meta.synopsis!!.startsWith("Dan Millman"))
    }

    @Test
    fun `documentario com titulo na primeira linha e sinopse rotulada (caso C)`() {
        val caption = """
            Meu nome é Eneas
            #Documentário #politica

            ©|Copyright: Brasil Paralelo
            📅|Ano: 2026
            🎙|Áudio: Português 🇧🇷

            📖|Sinopse: Na década de 1990, um médico que também possuía diploma em matemática e física tentou mudar o Brasil, mas foi ridicularizado pela imprensa.
            Com apenas 15 segundos no horário eleitoral, ele conseguiu fazer com que o país inteiro decorasse seu nome, Enéas Carneiro.

            @acervodocumentarios
        """.trimIndent()

        val meta = MovieMetadataParser.parse(caption)
        assertEquals("Meu nome é Eneas", meta.title)
        assertEquals(2026, meta.year)
        assertTrue(meta.audio!!.startsWith("Português"))
        assertNull(meta.director)
        assertNull(meta.genres)
        assertTrue(meta.synopsis!!.startsWith("Na década de 1990"))
        // Título não deve virar "Copyright..." nem a sinopse conter o @canal.
        assertTrue(!meta.synopsis!!.contains("@acervo"))
    }

    @Test
    fun `logo aceita caminho TMDB e URL completa`() {
        val porCaminho = MovieMetadataParser.parse(
            """
            Filme: Duna
            Logo: /abc.png
            """.trimIndent()
        )
        assertEquals("https://image.tmdb.org/t/p/w500/abc.png", porCaminho.logoUrl)

        val porUrl = MovieMetadataParser.parse(
            """
            Filme: Duna
            Logo: https://cdn.exemplo/duna.png
            """.trimIndent()
        )
        assertEquals("https://cdn.exemplo/duna.png", porUrl.logoUrl)

        assertNull(MovieMetadataParser.parse("Filme: Duna").logoUrl)
    }

    @Test
    fun `v3 de filme devolve ntv_id e ids sem titulo`() {
        val meta = MovieMetadataParser.parse("ntv:m_603 tmdb:603 imdb:tt0133093")
        assertEquals("m_603", meta.ntvId)
        assertEquals("603", meta.tmdbId)
        assertEquals("tt0133093", meta.imdbId)
        // Sem título: o cartão vem do catálogo ou do nome do arquivo, nunca da linha de IDs.
        assertNull(meta.title)
        assertNull(meta.synopsis)
        assertEquals(false, meta.isEpisode)
    }

    @Test
    fun `v3 de episodio em qualquer ordem e com chave desconhecida`() {
        val meta = MovieMetadataParser.parse("  S01E03 imdb:tt0944947 futuro:x tmdb:1399 ntv:e_1399_01_03 ")
        assertEquals("e_1399_01_03", meta.ntvId)
        assertEquals("1399", meta.seriesTmdbId)
        assertEquals("1399", meta.tmdbId)
        assertEquals(1, meta.seasonNumber)
        assertEquals(3, meta.episodeNumber)
        assertTrue(meta.isEpisode)
        assertNull(meta.title)
    }

    @Test
    fun `v3 de episodio sem SxxEyy tira temporada e episodio do ntv_id`() {
        val meta = MovieMetadataParser.parse("ntv:e_1399_02_10")
        assertEquals("1399", meta.seriesTmdbId)
        assertEquals(2, meta.seasonNumber)
        assertEquals(10, meta.episodeNumber)
    }

    @Test
    fun `legenda antiga continua igual e sem ntv_id`() {
        val antiga = "Título: Matrix\nAno: 1999\nTMDB: 603\nSinopse: Neo descobre a verdade."
        val meta = MovieMetadataParser.parse(antiga)
        assertNull(meta.ntvId)
        assertEquals("Matrix", meta.title)
        assertEquals(1999, meta.year)
        assertEquals("603", meta.tmdbId)
        // Uma linha de texto livre com dois-pontos não vira v3.
        val livre = MovieMetadataParser.parse("Missão: Impossível (1996)")
        assertNull(livre.ntvId)
        assertEquals("Missão: Impossível (1996)", livre.title)
        // ntv:... junto de texto livre também não.
        assertNull(MovieMetadataParser.parse("Matrix ntv:m_603").ntvId)
    }

    @Test
    fun `legenda vazia retorna vazio`() {
        val meta = MovieMetadataParser.parse(null)
        assertNull(meta.title)
        assertNull(meta.synopsis)
    }
}
