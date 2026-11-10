package com.ntv2.app.core.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGlideStateTest {
    private val a = Rect(0f, 0f, 100f, 100f)
    private val b = Rect(200f, 0f, 300f, 100f)
    private val c = Rect(400f, 0f, 500f, 100f)

    /** Simula o anel já visível (fade-in terminado) e desenhado no item atual. */
    private fun FocusGlideState.settle() {
        alpha = 1f
        t = 1f
        displayed()
    }

    @Test
    fun `primeiro foco aparece direto no item, sem deslizar`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        assertEquals(1f, g.t)
        assertEquals(a, g.displayed()!!.first)
        assertTrue(g.visible)
    }

    @Test
    fun `foco rapido parte da posicao exibida, nao do destino anterior`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        g.settle()
        g.onFocus("b", b, 6f)
        g.t = 0.5f
        val mid = g.displayed()!!.first // meio do caminho a -> b
        assertEquals(100f, mid.left, 0.01f)
        g.onFocus("c", c, 6f) // antes de chegar em b
        assertEquals(0f, g.t)
        assertEquals(mid, g.displayed()!!.first)
    }

    @Test
    fun `blur seguido de foco nao apaga o anel`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        g.settle()
        g.onBlur("a")
        assertTrue(g.visible) // tolerância: ainda não apagou
        g.onFocus("b", b, 6f)
        g.hideIfIdle() // fim da tolerância chega depois do novo foco
        assertTrue(g.visible)
        assertEquals(0f, g.t) // desliza de a para b
    }

    @Test
    fun `sem foco depois da tolerancia o anel some`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        g.onBlur("a")
        g.hideIfIdle()
        assertFalse(g.visible)
    }

    @Test
    fun `blur de item antigo depois do novo foco nao afeta`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        g.onFocus("b", b, 6f)
        g.onBlur("a")
        assertEquals("b", g.focusedId)
    }

    @Test
    fun `foco sem posicao completa no primeiro layout`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        g.settle()
        g.onFocusPending("b", 6f)
        assertEquals("b", g.focusedId)
        g.onPositioned("b", b)
        assertEquals(0f, g.t) // desliza a partir do anel em a
        g.t = 1f
        assertEquals(b, g.displayed()!!.first)
    }

    @Test
    fun `rolagem durante o deslize leva a partida junto`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f)
        g.settle()
        g.onFocus("b", b, 6f)
        // A lista rola 50 px para cima: o item focado sobe e a partida também.
        g.onPositioned("b", b.translate(0f, -50f))
        g.t = 0f
        val start = g.displayed()!!.first
        assertEquals(-50f, start.top, 0.01f)
        assertNotNull(start)
    }

    @Test
    fun `escala interpolada com o deslize`() {
        val g = FocusGlideState()
        g.onFocus("a", a, 6f, scale = 1f)
        g.settle()
        g.onFocus("b", b, 6f, scale = 1.1f)
        g.t = 0f
        assertEquals(100f, g.displayed()!!.first.width, 0.01f)
        g.t = 1f
        assertEquals(110f, g.displayed()!!.first.width, 0.01f)
    }
}
