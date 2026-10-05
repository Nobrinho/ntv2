package com.ntv2.app.core.recommendations

import com.ntv2.app.core.recommendations.RecommendationEngine.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    @Test
    fun `parseGenres normaliza separadores e caixa`() {
        assertEquals(setOf("ação", "drama"), RecommendationEngine.parseGenres("Ação, Drama"))
        assertEquals(setOf("ficção", "aventura"), RecommendationEngine.parseGenres("Ficção / Aventura"))
        assertTrue(RecommendationEngine.parseGenres(null).isEmpty())
        assertTrue(RecommendationEngine.parseGenres("  ").isEmpty())
    }

    @Test
    fun `ranqueia por soma de pesos dos generos em comum`() {
        val taste = mapOf("ação" to 3, "drama" to 1)
        val candidates = listOf(
            Candidate("a", setOf("ação", "drama")), // 4
            Candidate("b", setOf("ação")),          // 3
            Candidate("c", setOf("drama")),         // 1
            Candidate("d", setOf("comédia"))        // 0 (fora)
        )
        val result = RecommendationEngine.recommend(taste, candidates, exclude = emptySet())
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `exclui ids ja vistos ou favoritados`() {
        val taste = mapOf("ação" to 2)
        val candidates = listOf(Candidate("a", setOf("ação")), Candidate("b", setOf("ação")))
        val result = RecommendationEngine.recommend(taste, candidates, exclude = setOf("a"))
        assertEquals(listOf("b"), result)
    }

    @Test
    fun `respeita o limite`() {
        val taste = mapOf("ação" to 1)
        val candidates = (1..10).map { Candidate("m$it", setOf("ação")) }
        assertEquals(3, RecommendationEngine.recommend(taste, candidates, emptySet(), limit = 3).size)
    }

    @Test
    fun `sem gosto ou sem candidatos devolve vazio`() {
        assertTrue(RecommendationEngine.recommend(emptyMap(), listOf(Candidate("a", setOf("x"))), emptySet()).isEmpty())
        assertTrue(RecommendationEngine.recommend(mapOf("x" to 1), emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `buildTaste pesa favoritos mais que historico`() {
        val taste = RecommendationEngine.buildTaste(
            favoriteGenres = listOf("Ação"),
            historyGenres = listOf("Ação, Drama")
        )
        assertEquals(4, taste["ação"]) // 3 (fav) + 1 (hist)
        assertEquals(1, taste["drama"])
    }

    @Test
    fun `empates nao seguem a ordem do catalogo e sao estaveis por semente`() {
        val taste = mapOf("ação" to 1)
        val candidates = (1..30).map { Candidate("m$it", setOf("ação")) }
        val a = RecommendationEngine.recommend(taste, candidates, emptySet(), limit = 10, seed = 1)
        assertEquals(a, RecommendationEngine.recommend(taste, candidates, emptySet(), limit = 10, seed = 1))
        assertTrue(a != candidates.take(10).map { it.id })
        assertTrue(a != RecommendationEngine.recommend(taste, candidates, emptySet(), limit = 10, seed = 2))
    }

    @Test
    fun `mais generos fora do gosto nao ganham de quem combina melhor`() {
        val taste = mapOf("ação" to 3)
        val candidates = listOf(
            Candidate("muitos", setOf("ação", "x", "y", "z", "w")),
            Candidate("exato", setOf("ação"))
        )
        assertEquals("exato", RecommendationEngine.recommend(taste, candidates, emptySet()).first())
    }
}
