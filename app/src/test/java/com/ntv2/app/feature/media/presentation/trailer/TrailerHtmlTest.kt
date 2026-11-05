package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val full = TrailerHtml.buildFullscreen("dQw4w9WgXcQ")

    @Test
    fun `tela cheia toca com som, com controles e sem repetir`() {
        assertTrue(full.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(full.contains("mute: 0"))
        assertTrue(full.contains("controls: 1"))
        assertTrue(full.contains("loop: 0"))
        assertFalse(full.contains("playlist:"))
        assertTrue(full.contains("origin: 'https://nobrinho.github.io'"))
    }

    @Test
    fun `tela cheia avisa tocando, fim e erro e aceita pausar e avancar`() {
        assertTrue(full.contains("NtvTrailer.playing()"))
        assertTrue(full.contains("NtvTrailer.ended()"))
        assertTrue(full.contains("NtvTrailer.error("))
        assertTrue(full.contains("function ntvToggle()"))
        assertTrue(full.contains("function ntvSeek(delta)"))
    }

    @Test
    fun `os dois modos avisam pronto e cada mudanca de estado do player`() {
        for (page in listOf(html, full)) {
            assertTrue(page.contains("NtvTrailer.ready()"))
            assertTrue(page.contains("NtvTrailer.state(String(e.data))"))
        }
    }

    @Test
    fun `callback da API e definido antes de carregar o script do YouTube`() {
        // Com o widgetapi no cache do processo, ele roda na hora: se o callback ainda não existir,
        // o player nunca é criado (2ª abertura ficava carregando para sempre).
        for (page in listOf(html, full)) {
            val callback = page.indexOf("function onYouTubeIframeAPIReady")
            val api = page.indexOf("https://www.youtube.com/iframe_api")
            assertTrue(callback in 0 until api)
        }
    }

    @Test
    fun `player ocupa a tela pelo viewport e nao por porcentagem do body`() {
        // height: 100% dava 0 px no iframe (body sem altura): o vídeo tocava invisível.
        for (page in listOf(html, full)) {
            assertTrue(page.contains("#player, iframe { position: fixed"))
            assertTrue(page.contains("width: 100vw"))
            assertTrue(page.contains("height: 100vh"))
        }
    }

    @Test
    fun `previa continua muda e em loop`() {
        assertTrue(html.contains("loop: 1"))
        assertFalse(html.contains("NtvTrailer.ended()"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tela cheia tambem recusa id fora do formato`() {
        TrailerHtml.buildFullscreen("<script>")
    }
}
