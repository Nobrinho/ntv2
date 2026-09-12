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
    fun `legenda vazia retorna vazio`() {
        val meta = MovieMetadataParser.parse(null)
        assertNull(meta.title)
        assertNull(meta.synopsis)
    }
}
