package com.ntv2.app.core.player.exoplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDecoderPolicyTest {

    @Test
    fun `primeira falha em 1080p troca para software`() {
        assertTrue(VideoDecoderPolicy.shouldSwitchToSoftware(alreadySoftware = false, videoHeight = 800))
        assertTrue(VideoDecoderPolicy.shouldSwitchToSoftware(alreadySoftware = false, videoHeight = 1080))
    }

    @Test
    fun `altura desconhecida troca para software`() {
        assertTrue(VideoDecoderPolicy.shouldSwitchToSoftware(alreadySoftware = false, videoHeight = 0))
    }

    @Test
    fun `4K fica no hardware`() {
        assertFalse(VideoDecoderPolicy.shouldSwitchToSoftware(alreadySoftware = false, videoHeight = 2160))
    }

    @Test
    fun `ja em software nao troca de novo`() {
        assertFalse(VideoDecoderPolicy.shouldSwitchToSoftware(alreadySoftware = true, videoHeight = 800))
    }

    @Test
    fun `memoria mantem os mais recentes sem repetir`() {
        val list = SharedPrefsSoftwareDecoderMemory.appendRecent(listOf("a", "b", "c"), "b", max = 3)
        assertEquals(listOf("a", "c", "b"), list)
        val trimmed = SharedPrefsSoftwareDecoderMemory.appendRecent(listOf("a", "b", "c"), "d", max = 3)
        assertEquals(listOf("b", "c", "d"), trimmed)
    }
}
