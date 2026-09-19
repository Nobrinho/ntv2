package com.ntv2.app.core.player.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskWindowPolicyTest {
    private val mb = 1024L * 1024L
    private val policy = DiskWindowPolicy(
        headPinBytes = 16 * mb,
        tailPinBytes = 32 * mb,
        keepBehindBytes = 160 * mb,
        minEvictBytes = 32 * mb
    )
    private val size = 3000 * mb

    @Test
    fun `libera o trecho entre o inicio fixado e a leitura menos a folga`() {
        val range = policy.evictionRange(evictedEnd = 0L, readPosition = 400 * mb, streamStart = 0L, fileSize = size)
        assertEquals(16 * mb until 240 * mb, range)
    }

    @Test
    fun `continua de onde parou`() {
        val range = policy.evictionRange(evictedEnd = 240 * mb, readPosition = 450 * mb, streamStart = 0L, fileSize = size)
        assertEquals(240 * mb until 290 * mb, range)
    }

    @Test
    fun `nao libera pouco de cada vez`() {
        assertNull(policy.evictionRange(evictedEnd = 240 * mb, readPosition = 420 * mb, streamStart = 0L, fileSize = size))
    }

    @Test
    fun `leitura curta no fim do arquivo (indice do MKV) nao libera nada`() {
        assertNull(
            policy.evictionRange(evictedEnd = 0L, readPosition = size - 1 * mb, streamStart = size - 5 * mb, fileSize = size)
        )
    }

    @Test
    fun `apos seek adiante so libera depois de ler a folga continua`() {
        assertNull(policy.evictionRange(0L, readPosition = 1100 * mb, streamStart = 1000 * mb, fileSize = size))
        assertEquals(16 * mb until 1040 * mb, policy.evictionRange(0L, readPosition = 1200 * mb, streamStart = 1000 * mb, fileSize = size))
    }

    @Test
    fun `nunca invade o fim fixado`() {
        val smallBehind = policy.copy(keepBehindBytes = 8 * mb)
        val range = smallBehind.evictionRange(evictedEnd = 0L, readPosition = size, streamStart = 0L, fileSize = size)!!
        assertEquals(size - 32 * mb, range.last + 1)
    }

    @Test
    fun `sem tamanho conhecido nao libera`() {
        assertNull(policy.evictionRange(0L, 400 * mb, 0L, fileSize = 0L))
    }

    @Test
    fun `posicao no trecho liberado exige rebaixar`() {
        assertTrue(policy.isEvicted(100 * mb, evictedEnd = 240 * mb))
        assertFalse(policy.isEvicted(8 * mb, evictedEnd = 240 * mb))
        assertFalse(policy.isEvicted(240 * mb, evictedEnd = 240 * mb))
    }

    @Test
    fun `leitura no inicio fixado para na borda do trecho liberado`() {
        assertEquals(6 * mb, policy.clampReadable(10 * mb, readable = 500 * mb, evictedEnd = 240 * mb))
        assertEquals(0L, policy.clampReadable(100 * mb, readable = 500 * mb, evictedEnd = 240 * mb))
        assertEquals(500 * mb, policy.clampReadable(300 * mb, readable = 500 * mb, evictedEnd = 240 * mb))
        assertEquals(500 * mb, policy.clampReadable(10 * mb, readable = 500 * mb, evictedEnd = 0L))
    }
}
