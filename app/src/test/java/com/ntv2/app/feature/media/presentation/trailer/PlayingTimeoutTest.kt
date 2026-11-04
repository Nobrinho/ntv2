package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayingTimeoutTest {

    @Test
    fun `comeca com o prazo inteiro`() {
        assertEquals(8000L, PlayingTimeout(8000L).remainingMs)
    }

    @Test
    fun `tempo em primeiro plano se acumula entre pausas`() {
        val t = PlayingTimeout(8000L)
        t.consume(3000L)
        t.consume(2500L)
        assertEquals(2500L, t.remainingMs)
    }

    @Test
    fun `nao fica negativo nem volta com tempo negativo`() {
        val t = PlayingTimeout(8000L)
        t.consume(-100L)
        assertEquals(8000L, t.remainingMs)
        t.consume(9000L)
        assertEquals(0L, t.remainingMs)
    }
}
