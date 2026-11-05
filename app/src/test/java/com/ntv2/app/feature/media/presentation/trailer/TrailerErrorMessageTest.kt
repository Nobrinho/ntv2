package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerErrorMessageTest {

    @Test
    fun `bloqueio fora do YouTube explica e mostra o codigo`() {
        val msg = trailerErrorMessage("150")
        assertTrue(msg.contains("fora do YouTube"))
        assertTrue(msg.contains("150"))
        assertTrue(trailerErrorMessage("101").contains("fora do YouTube"))
    }

    @Test
    fun `origem recusada aparece com o codigo`() {
        assertTrue(trailerErrorMessage("153").contains("origem"))
    }

    @Test
    fun `codigo desconhecido ainda mostra o numero`() {
        assertTrue(trailerErrorMessage("999").contains("999"))
    }
}
