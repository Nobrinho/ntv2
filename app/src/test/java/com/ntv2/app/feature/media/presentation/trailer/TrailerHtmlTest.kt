package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerHtmlTest {

    private val html = TrailerHtml.build("dQw4w9WgXcQ")

    @Test
    fun `player usa os parametros exatos com o id`() {
        assertTrue(html.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("mute: 1"))
        assertTrue(html.contains("controls: 0"))
        assertTrue(html.contains("playlist: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("origin: 'https://nobrinho.github.io'"))
    }

    @Test
    fun `pagina avisa o app quando toca e quando da erro`() {
        assertTrue(html.contains("NtvTrailer.playing()"))
        assertTrue(html.contains("NtvTrailer.error("))
    }

    @Test
    fun `base url e o site do projeto`() {
        assertEquals("https://nobrinho.github.io/", TrailerHtml.BASE_URL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `id fora do formato e recusado`() {
        TrailerHtml.build("a'b")
    }
}
