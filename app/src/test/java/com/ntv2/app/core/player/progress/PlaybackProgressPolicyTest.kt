package com.ntv2.app.core.player.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressPolicyTest {

    private val duration = 60L * 60 * 1000 // 1h

    @Test
    fun `salva posicao no meio`() {
        val action = PlaybackProgressPolicy.onProgress(positionMs = 30 * 60 * 1000, durationMs = duration)
        assertEquals(PlaybackProgressPolicy.SaveAction.Save(30 * 60 * 1000, duration), action)
    }

    @Test
    fun `ignora posicao muito no comeco`() {
        val action = PlaybackProgressPolicy.onProgress(positionMs = 3_000, durationMs = duration)
        assertEquals(PlaybackProgressPolicy.SaveAction.Ignore, action)
    }

    @Test
    fun `limpa quando perto do fim (assistido)`() {
        val action = PlaybackProgressPolicy.onProgress(positionMs = duration - 2_000, durationMs = duration)
        assertEquals(PlaybackProgressPolicy.SaveAction.Clear, action)
    }

    @Test
    fun `ignora quando duracao desconhecida`() {
        val action = PlaybackProgressPolicy.onProgress(positionMs = 30_000, durationMs = 0)
        assertEquals(PlaybackProgressPolicy.SaveAction.Ignore, action)
    }

    @Test
    fun `retoma posicao valida`() {
        assertEquals(30 * 60 * 1000L, PlaybackProgressPolicy.resumePosition(30 * 60 * 1000, duration))
    }

    @Test
    fun `nao retoma posicao trivial`() {
        assertEquals(0L, PlaybackProgressPolicy.resumePosition(3_000, duration))
    }

    @Test
    fun `nao retoma posicao perto do fim`() {
        assertEquals(0L, PlaybackProgressPolicy.resumePosition(duration - 2_000, duration))
    }
}
