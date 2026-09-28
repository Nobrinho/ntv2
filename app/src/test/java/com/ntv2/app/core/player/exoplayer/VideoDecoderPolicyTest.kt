package com.ntv2.app.core.player.exoplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDecoderPolicyTest {

    // Pontos reais de falha do hardware no "Linha de Frente" (AFTKM).
    private val linhaDeFrente = listOf(82_500L, 215_100L)

    @Test
    fun `software so ate 1080p`() {
        assertTrue(VideoDecoderPolicy.softwareCapable(800))
        assertTrue(VideoDecoderPolicy.softwareCapable(1080))
        assertTrue(VideoDecoderPolicy.softwareCapable(0))
        assertFalse(VideoDecoderPolicy.softwareCapable(2160))
    }

    @Test
    fun `fora das janelas usa hardware`() {
        assertNull(DecoderWindows.softwareUntil(10_000L, linhaDeFrente))
        assertNull(DecoderWindows.softwareUntil(300_000L, linhaDeFrente))
        assertNull(DecoderWindows.softwareUntil(10_000L, emptyList()))
    }

    @Test
    fun `troca para software um pouco antes do ponto e volta depois`() {
        // 4 s antes de 82,5 s já entra; a janela vai até 8 s depois do ponto.
        assertNull(DecoderWindows.softwareUntil(78_000L, listOf(82_500L)))
        assertEquals(90_500L, DecoderWindows.softwareUntil(78_600L, listOf(82_500L)))
        assertEquals(90_500L, DecoderWindows.softwareUntil(90_000L, listOf(82_500L)))
        assertNull(DecoderWindows.softwareUntil(91_000L, listOf(82_500L)))
    }

    @Test
    fun `pontos proximos viram uma janela so`() {
        // 82,5 s e 100 s: o segundo começa (96 s) antes de 90,5 + 20 s → janela única até 108 s.
        assertEquals(108_000L, DecoderWindows.softwareUntil(85_000L, listOf(82_500L, 100_000L)))
        // Linha de Frente: 215 s começa bem depois de 90,5 + 20 s → janelas separadas.
        assertEquals(90_500L, DecoderWindows.softwareUntil(85_000L, linhaDeFrente))
        assertNull(DecoderWindows.softwareUntil(150_000L, linhaDeFrente))
        assertEquals(223_100L, DecoderWindows.softwareUntil(214_000L, linhaDeFrente))
    }

    @Test
    fun `ponto repetido nao duplica`() {
        assertEquals(listOf(82_500L), DecoderWindows.addPoint(listOf(82_500L), 83_000L))
        assertEquals(listOf(82_500L, 215_100L), DecoderWindows.addPoint(listOf(215_100L), 82_500L))
    }

    @Test
    fun `memoria codifica e decodifica posicoes por video`() {
        val all = SharedPrefsDecoderTroubleMemory.decode("a|1000,2000\nb|5000")
        assertEquals(listOf(1000L, 2000L), all["a"])
        val raw = SharedPrefsDecoderTroubleMemory.encode(all, "a", listOf(1000L, 2000L, 3000L), max = 2)
        // "a" passa a ser o mais recente; mantém 2 vídeos.
        assertEquals("b|5000\na|1000,2000,3000", raw)
        val trimmed = SharedPrefsDecoderTroubleMemory.encode(
            SharedPrefsDecoderTroubleMemory.decode(raw), "c", listOf(7000L), max = 2
        )
        assertEquals("a|1000,2000,3000\nc|7000", trimmed)
    }

    @Test
    fun `linha invalida e ignorada`() {
        assertTrue(SharedPrefsDecoderTroubleMemory.decode("semseparador\nx|abc").isEmpty())
        assertTrue(SharedPrefsDecoderTroubleMemory.decode(null).isEmpty())
    }
}
