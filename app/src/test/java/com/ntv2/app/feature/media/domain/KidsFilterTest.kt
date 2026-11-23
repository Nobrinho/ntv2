package com.ntv2.app.feature.media.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsFilterTest {

    @Test
    fun `classificacao conhecida vale contra o limite do perfil`() {
        assertTrue(KidsFilter.allows(genres = "Terror", ageRating = "L", maxRating = 0))
        assertTrue(KidsFilter.allows(genres = null, ageRating = "10 anos", maxRating = 12))
        assertFalse(KidsFilter.allows(genres = "Animação", ageRating = "16", maxRating = 12))
    }

    @Test
    fun `sem classificacao so passam generos infantis, com ou sem acento`() {
        assertTrue(KidsFilter.allows("Aventura, Animação", null, 12))
        assertTrue(KidsFilter.allows("Familia", null, 12))
        assertFalse(KidsFilter.allows("Drama, Ação", null, 12))
        assertFalse(KidsFilter.allows(null, null, 16))
        assertTrue(KidsFilter.allowsGenres(listOf("Comédia", "Família")))
    }

    @Test
    fun `leitura da classificacao`() {
        assertEquals(0, KidsFilter.parseRating("Livre"))
        assertEquals(14, KidsFilter.parseRating("A14"))
        assertEquals(18, KidsFilter.parseRating("18 anos"))
        assertNull(KidsFilter.parseRating(""))
        assertNull(KidsFilter.parseRating("PG"))
    }
}
