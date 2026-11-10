package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerHtmlTest {

    private val html = TrailerHtml.build("dQw4w9WgXcQ")

    @Test
    fun `player sem controles, sem repetir, com legenda pt`() {
        assertTrue(html.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("controls: 0"))
        assertTrue(html.contains("loop: 0"))
        assertFalse(html.contains("playlist:"))
        // Prévia sem legenda; tela cheia com legenda pt.
        assertTrue(html.contains("cc_load_policy: 0"))
        assertTrue(TrailerHtml.build("dQw4w9WgXcQ", volume = 100, captions = true).contains("cc_load_policy: 1"))
        assertTrue(html.contains("cc_lang_pref: 'pt'"))
        assertTrue(html.contains("hl: 'pt-BR'"))
        assertTrue(html.contains("origin: 'https://nobrinho.github.io'"))
    }

    @Test
    fun `previa toca com som a 50 por cento e a tela cheia a 100`() {
        assertTrue(html.contains("setVolume(50)"))
        assertTrue(html.contains("e.target.mute(); e.target.playVideo()"))
        assertTrue(TrailerHtml.build("dQw4w9WgXcQ", volume = 100).contains("setVolume(100)"))
    }

    @Test
    fun `pagina avisa tocando, fim e erro e aceita controles do d-pad`() {
        assertTrue(html.contains("NtvTrailer.playing()"))
        assertTrue(html.contains("NtvTrailer.ended()"))
        assertTrue(html.contains("NtvTrailer.error("))
        assertTrue(html.contains("NtvTrailer.ready()"))
        assertTrue(html.contains("NtvTrailer.state(String(e.data))"))
        assertTrue(html.contains("function ntvToggle()"))
        assertTrue(html.contains("function ntvSeek(delta)"))
        assertTrue(html.contains("function ntvSound(v)"))
    }

    @Test
    fun `fim e avisado antes do video acabar (sem grade de recomendados)`() {
        assertTrue(html.contains("d - t < 0.6"))
    }

    @Test
    fun `base url e o site do projeto`() {
        assertEquals("https://nobrinho.github.io/", TrailerHtml.BASE_URL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `id fora do formato e recusado`() {
        TrailerHtml.build("a'b")
    }

    @Test
    fun `callback da API e definido antes de carregar o script do YouTube`() {
        // Com o widgetapi no cache do processo, ele roda na hora: se o callback ainda não existir,
        // o player nunca é criado (2ª abertura ficava carregando para sempre).
        val callback = html.indexOf("function onYouTubeIframeAPIReady")
        val api = html.indexOf("https://www.youtube.com/iframe_api")
        assertTrue(callback in 0 until api)
    }

    @Test
    fun `player ocupa a tela pelo viewport e cobre em 16x9`() {
        // height: 100% dava 0 px no iframe (body sem altura): o vídeo tocava invisível.
        assertTrue(html.contains("width: 100vw"))
        assertTrue(html.contains("height: 100vh"))
        assertTrue(html.contains("function ntvFit()"))
    }
}
