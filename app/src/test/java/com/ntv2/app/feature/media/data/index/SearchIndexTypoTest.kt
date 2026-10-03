package com.ntv2.app.feature.media.data.index

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTypoTest {

    private fun match(title: String, query: String, fuzzy: Boolean): Boolean {
        val key = normalizeForIndex(title)
        val tokens = normalizeForIndex(query).split(' ').filter { it.isNotBlank() }
        return matchesTokens(key, wordsOf(key), tokens, fuzzy)
    }

    @Test
    fun `letra faltando casa so no modo tolerante`() {
        assertFalse(match("The Electric State", "the eletric state", fuzzy = false))
        assertTrue(match("The Electric State", "the eletric state", fuzzy = true))
    }

    @Test
    fun `letras trocadas e sobrando`() {
        assertTrue(match("Interstellar", "intrestellar", fuzzy = true))
        assertTrue(match("Gladiator", "gladiatorr", fuzzy = true))
    }

    @Test
    fun `palavras curtas exigem acerto exato`() {
        assertFalse(match("The Matrix", "thx matrix", fuzzy = true))
        assertFalse(match("Cars", "cats", fuzzy = true))
    }

    @Test
    fun `palavra errada demais nao casa`() {
        assertFalse(match("The Electric State", "eletrik stat", fuzzy = true))
        assertFalse(match("The Electric State", "estado eletrico", fuzzy = true))
    }

    @Test
    fun `acento e pontuacao`() {
        assertTrue(match("Amélie: Poulain", "amelie poulain", fuzzy = false))
        assertTrue(match("Amélie: Poulain", "amelei", fuzzy = true))
    }
}
