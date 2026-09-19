package com.ntv2.app.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageBudgetTest {
    private val mb = 1024L * 1024L
    private val gb = 1024L * mb

    @Test
    fun `limiar do sistema e 10 por cento em volume pequeno`() {
        assertEquals(300L * mb, StorageBudget.systemLowThreshold(3000L * mb))
    }

    @Test
    fun `limiar do sistema limitado a 500 MB em volume grande`() {
        assertEquals(500L * mb, StorageBudget.systemLowThreshold(32L * gb))
    }

    @Test
    fun `piso de download fica acima do limiar do sistema`() {
        val total = 5L * gb
        assertTrue(StorageBudget.downloadFloor(total) > StorageBudget.systemLowThreshold(total))
        assertEquals(700L * mb, StorageBudget.downloadFloor(total))
    }

    @Test
    fun `fire tv com 5 GB bloqueia download abaixo de 700 MB livres`() {
        val total = 5L * gb
        assertFalse(StorageBudget.canDownload(650L * mb, total))
        assertTrue(StorageBudget.canDownload(700L * mb, total))
    }

    @Test
    fun `iniciar exige folga alem do piso de download`() {
        val total = 5L * gb
        assertFalse(StorageBudget.canStartPlayback(800L * mb, total))
        assertTrue(StorageBudget.canStartPlayback(850L * mb, total))
    }

    @Test
    fun `snapshot expoe as decisoes`() {
        val snapshot = StorageSnapshot(freeBytes = 2L * gb, totalBytes = 5L * gb)
        assertTrue(snapshot.canDownload)
        assertTrue(snapshot.canStartPlayback)
    }
}
