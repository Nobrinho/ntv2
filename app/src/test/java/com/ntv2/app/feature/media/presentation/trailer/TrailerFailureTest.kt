package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerFailureTest {

    @Test
    fun `tempo esgotado nao bloqueia o video na sessao`() {
        assertFalse(TrailerFailure.TIMEOUT.blocksSession)
    }

    @Test
    fun `erro do player, renderizador caido e falta de WebView bloqueiam na sessao`() {
        assertTrue(TrailerFailure.PLAYER_ERROR.blocksSession)
        assertTrue(TrailerFailure.RENDERER_GONE.blocksSession)
        assertTrue(TrailerFailure.NO_WEBVIEW.blocksSession)
    }

    @Test
    fun `so falha que bloqueia vai para a lista da sessao`() {
        val timeoutId = "tmOutAAAAAA"
        val errorId = "plErrAAAAAA"
        TrailerFailure.TIMEOUT.record(timeoutId)
        TrailerFailure.PLAYER_ERROR.record(errorId)
        assertFalse(timeoutId in TrailerFailures.ids)
        assertTrue(errorId in TrailerFailures.ids)
    }
}
