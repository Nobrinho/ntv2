package com.ntv2.app.core.storage

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPlaybackGatewayTest {

    private class InMemoryPending(previous: Set<Int> = emptySet()) : PendingFileDeletions {
        val persisted = previous.toMutableSet()
        private val previousSessions = previous.toSet()
        private val active = mutableSetOf<Int>()
        override fun markDownloading(fileId: Int) { active += fileId; persisted += fileId }
        override fun markDeleted(fileId: Int) { active -= fileId; persisted -= fileId }
        override fun orphans(): Set<Int> = previousSessions.filter { it in persisted }.toSet()
        override fun isActiveInThisSession(fileId: Int): Boolean = fileId in active
    }

    private class RecordingGateway : TdlibPlaybackGateway {
        val deleted = mutableListOf<Int>()
        override suspend fun openFile(fileId: Int) = TdlibPlaybackFileState(fileId, "/tmp/$fileId", 0L, 100L, false)
        override fun observeFile(fileId: Int): Flow<TdlibPlaybackFileState> = emptyFlow()
        override suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) = Unit
        override suspend fun cancelFile(fileId: Int) = Unit
        override suspend fun deleteFile(fileId: Int) { deleted += fileId }
        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long = 0L
    }

    @Test
    fun `abrir registra o arquivo e apagar remove o registro`() = runTest {
        val pending = InMemoryPending()
        val delegate = RecordingGateway()
        val gateway = TrackingPlaybackGateway(delegate, pending)

        gateway.openFile(7)
        assertTrue(7 in pending.persisted)
        assertTrue(pending.isActiveInThisSession(7))

        gateway.deleteFile(7)
        assertEquals(listOf(7), delegate.deleted)
        assertFalse(7 in pending.persisted)
        assertFalse(pending.isActiveInThisSession(7))
    }

    @Test
    fun `pre-download pelo requestChunk tambem registra`() = runTest {
        val pending = InMemoryPending()
        TrackingPlaybackGateway(RecordingGateway(), pending).requestChunk(9, 0L, 8L, 8)
        assertTrue(9 in pending.persisted)
    }

    @Test
    fun `orfaos sao apenas os de sessoes anteriores ainda nao apagados`() = runTest {
        val pending = InMemoryPending(previous = setOf(1, 2))
        val gateway = TrackingPlaybackGateway(RecordingGateway(), pending)
        gateway.openFile(3)
        gateway.deleteFile(2)
        assertEquals(setOf(1), pending.orphans())
    }
}
