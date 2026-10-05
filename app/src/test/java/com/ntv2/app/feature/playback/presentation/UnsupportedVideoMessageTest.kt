package com.ntv2.app.feature.playback.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class UnsupportedVideoMessageTest {
    @Test
    fun `mensagem cita o formato quando conhecido`() {
        assertEquals(
            "Este aparelho não consegue exibir a imagem deste vídeo (Dolby Vision 4K). Só o áudio será reproduzido.",
            unsupportedVideoMessage("Dolby Vision 4K")
        )
    }

    @Test
    fun `mensagem sem formato`() {
        assertEquals(
            "Este aparelho não consegue exibir a imagem deste vídeo. Só o áudio será reproduzido.",
            unsupportedVideoMessage(null)
        )
    }
}
