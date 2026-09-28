package com.ntv2.app.core.player.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProfilesTest {
    private val mb = 1024L * 1024L
    private val gb = 1024L * mb
    private val ram = 64L * mb
    private val twoHours = 2L * 60L * 60L * 1000L

    @Test
    fun `4K remux uses large windows capped at the maximum`() {
        // ~80 GB em 2 h passa de 10 MB/s (fica no teto).
        val p = StreamProfiles.forMedia(80L * gb, twoHours, ram, freeBytes = 4L * gb, downloadFloor = 700L * mb)
        assertEquals(StreamProfiles.MAX_BYTES_PER_SECOND, p.bytesPerSecond)
        assertEquals(StreamProfiles.MAX_AHEAD_BYTES, p.aheadWindowBytes)
        // RAM + 30 s de 4K > 160 MB antigos: voltar 10 s não cai em trecho apagado.
        assertTrue(p.keepBehindBytes >= ram + 30L * 10L * mb)
        assertTrue(p.diskWindowEnabled)
    }

    @Test
    fun `1080p keeps minimum windows`() {
        // 4 GB em 2 h ≈ 0,57 MB/s.
        val p = StreamProfiles.forMedia(4L * gb, twoHours, ram, freeBytes = 1L * gb, downloadFloor = 700L * mb)
        assertEquals(StreamProfiles.MIN_AHEAD_BYTES, p.aheadWindowBytes)
        assertEquals(StreamProfiles.MIN_KEEP_BEHIND_BYTES, p.keepBehindBytes)
    }

    @Test
    fun `disk window is disabled when the whole file fits`() {
        val p = StreamProfiles.forMedia(2L * gb, twoHours, ram, freeBytes = 20L * gb, downloadFloor = 700L * mb)
        assertFalse(p.diskWindowEnabled)
    }

    @Test
    fun `unknown duration or free space falls back safely`() {
        val p = StreamProfiles.forMedia(2L * gb, 0L, ram, freeBytes = null, downloadFloor = 0L)
        assertEquals(StreamProfiles.FALLBACK_BYTES_PER_SECOND, p.bytesPerSecond)
        assertTrue(p.diskWindowEnabled)
    }

    @Test
    fun `ram buffer is a sixth of the heap within bounds`() {
        assertEquals((256L * mb / 6L).toInt(), StreamProfiles.ramBufferBytes(256L * mb))
        assertEquals((32L * mb).toInt(), StreamProfiles.ramBufferBytes(64L * mb))
        assertEquals((64L * mb).toInt(), StreamProfiles.ramBufferBytes(1024L * mb))
    }
}
