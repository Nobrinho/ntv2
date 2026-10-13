package com.ntv2.app.core.player.controller

import androidx.media3.common.Player
import com.ntv2.app.core.player.PlaybackCoordinator
import com.ntv2.app.core.player.PlaybackMedia
import com.ntv2.app.core.player.PlaybackSnapshot
import com.ntv2.app.core.player.progress.PlaybackProgressStore
import com.ntv2.app.core.player.source.MediaAvailability
import com.ntv2.app.core.player.source.PlaybackSourceRequest
import com.ntv2.app.core.player.source.PlaybackSourceResolution
import com.ntv2.app.core.player.source.PlaybackSourceResolver
import com.ntv2.app.core.storage.PlaybackStorageGuard
import com.ntv2.app.core.storage.StorageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlaybackControllerTest {
    private val events = mutableListOf<String>()

    private inner class FakeCoordinator : PlaybackCoordinator {
        override val player: Player? = null
        override val snapshot: StateFlow<PlaybackSnapshot> = MutableStateFlow(PlaybackSnapshot())
        override suspend fun prepare(media: PlaybackMedia) { events += "prepare:${media.fileId}" }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun retry() = Unit
        override fun retryAt(positionMs: Long) = Unit
        override fun stop() = Unit
        override fun onAppStop() = Unit
        override fun onAppResume() = Unit
        override fun release() { events += "release" }
        override fun releaseIfOwner(fileId: Int) { events += "releaseIfOwner:$fileId" }
        override suspend fun releasePrevious(nextFileId: Int) { events += "releasePrevious:$nextFileId" }
        override fun selectAudioTrack(id: String) = Unit
        override fun selectTextTrack(id: String?) = Unit
        override fun discardMedia(fileId: Int) = Unit
        override suspend fun restartDownload(fileId: Int) = Unit
    }

    private inner class FakeStorageGuard(private val free: Long) : PlaybackStorageGuard {
        override suspend fun ensureSpaceForPlayback(): StorageSnapshot {
            events += "checkSpace"
            return StorageSnapshot(freeBytes = free, totalBytes = 5L * 1024 * 1024 * 1024)
        }
        override fun onLowStorageDuringPlayback() = Unit
    }

    private val missingResolver = object : PlaybackSourceResolver {
        override suspend fun resolve(request: PlaybackSourceRequest) =
            PlaybackSourceResolution.Missing(MediaAvailability.TdlibFileUnavailable)
    }

    private val progressStore = object : PlaybackProgressStore {
        override suspend fun resumePositionMs(mediaId: String, durationMs: Long) = 0L
        override suspend fun onProgress(mediaId: String, positionMs: Long, durationMs: Long) = Unit
        override suspend fun clear(mediaId: String) = Unit
        override suspend fun savedPositions(mediaIds: List<String>) = emptyMap<String, Long>()
    }

    private fun controller(free: Long = 3L * 1024 * 1024 * 1024) = DefaultPlaybackController(
        coordinator = FakeCoordinator(),
        sourceResolver = missingResolver,
        progressStore = progressStore,
        storageGuard = FakeStorageGuard(free)
    )

    private fun request(fileId: Int) = PlaybackPrepareRequest("m$fileId", fileId, "t", 60)

    @Test
    fun `troca de video libera o anterior antes de checar o espaco`() = runBlocking {
        controller().prepare(request(20))

        assertEquals(listOf("releasePrevious:20", "checkSpace"), events)
    }

    @Test
    fun `sem espaco o anterior ja foi liberado antes da recusa`() = runBlocking {
        val result = controller(free = 100L * 1024 * 1024).prepare(request(20))

        assertTrue(result is PlaybackPrepareResult.InsufficientStorage)
        assertEquals("releasePrevious:20", events.first())
    }

    @Test
    fun `release encerra so a sessao do dono`() {
        controller().release(ownerFileId = 10)

        assertEquals(listOf("releaseIfOwner:10"), events)
    }
}
