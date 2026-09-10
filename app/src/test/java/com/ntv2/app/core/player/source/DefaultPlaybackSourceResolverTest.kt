package com.ntv2.app.core.player.source

import com.ntv2.app.core.player.telegram.PlaybackFileHandle
import com.ntv2.app.core.player.telegram.TelegramPlaybackDataSource
import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultPlaybackSourceResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeDataSource(private val handle: PlaybackFileHandle?) : TelegramPlaybackDataSource {
        val chunkRequests = mutableListOf<Triple<Int, Long, Long>>()

        override suspend fun inspectFile(fileId: Int): PlaybackFileHandle? = handle
        override suspend fun open(fileId: Int): PlaybackFileHandle = handle!!
        override fun observe(fileId: Int): Flow<TdlibPlaybackFileState> = emptyFlow()
        override suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
            chunkRequests += Triple(fileId, offsetBytes, lengthBytes)
        }
        override suspend fun close(fileId: Int) = Unit
        override suspend fun deleteFile(fileId: Int) = Unit
        override fun resolvePath(fileId: Int): String? = handle?.localPath
        override fun downloadedBytes(fileId: Int): Long = handle?.downloadedBytes ?: 0L
        override fun expectedBytes(fileId: Int): Long? = handle?.expectedBytes
        override fun isComplete(fileId: Int): Boolean = handle?.isDownloadComplete ?: false
        override fun contiguousReadableStart(fileId: Int): Long = 0L
        override fun contiguousReadableEnd(fileId: Int): Long = handle?.downloadedBytes ?: 0L
        override fun requestRange(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) = Unit
        override suspend fun awaitReadableBeyond(fileId: Int, position: Long) = Unit
    }

    @Test
    fun `mediaId em branco retorna MissingRequestData`() = runTest {
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(null))
        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "", fileId = 1))
        assertTrue(result is PlaybackSourceResolution.Missing)
        assertEquals(
            MediaAvailability.MissingRequestData,
            (result as PlaybackSourceResolution.Missing).availability
        )
    }

    @Test
    fun `fileId invalido retorna MissingRequestData`() = runTest {
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(null))
        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m1", fileId = 0))
        assertEquals(
            MediaAvailability.MissingRequestData,
            (result as PlaybackSourceResolution.Missing).availability
        )
    }

    @Test
    fun `arquivo indisponivel no tdlib retorna TdlibFileUnavailable`() = runTest {
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(null))
        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m1", fileId = 5))
        assertEquals(
            MediaAvailability.TdlibFileUnavailable,
            (result as PlaybackSourceResolution.Missing).availability
        )
    }

    @Test
    fun `arquivo local ausente retorna LocalFileMissing`() = runTest {
        val handle = PlaybackFileHandle(
            fileId = 5,
            localPath = "/caminho/inexistente/v.partial",
            downloadedBytes = 0,
            expectedBytes = 100,
            isDownloadComplete = false
        )
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(handle))
        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m1", fileId = 5))
        assertEquals(
            MediaAvailability.LocalFileMissing,
            (result as PlaybackSourceResolution.Missing).availability
        )
    }

    @Test
    fun `incompleto abaixo do minimo retorna Downloading e pede bootstrap`() = runTest {
        val f = temp.newFile("v.partial").apply { writeBytes(ByteArray(1024)) }
        val handle = PlaybackFileHandle(
            fileId = 5,
            localPath = f.absolutePath,
            downloadedBytes = 1024,
            expectedBytes = 100L * 1024 * 1024,
            isDownloadComplete = false
        )
        val fake = FakeDataSource(handle)
        val resolver = DefaultPlaybackSourceResolver(fake)

        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m1", fileId = 5))

        assertTrue((result as PlaybackSourceResolution.Missing).availability is MediaAvailability.Downloading)
        assertTrue("deve pedir chunk de bootstrap", fake.chunkRequests.isNotEmpty())
        assertEquals(5, fake.chunkRequests.first().first)
    }

    @Test
    fun `arquivo completo retorna Available com uri de playback`() = runTest {
        val f = temp.newFile("v2.partial").apply { writeBytes(ByteArray(1024)) }
        val handle = PlaybackFileHandle(
            fileId = 7,
            localPath = f.absolutePath,
            downloadedBytes = 1024,
            expectedBytes = 1024,
            isDownloadComplete = true
        )
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(handle))

        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m7", fileId = 7))

        assertTrue(result is PlaybackSourceResolution.Available)
        val source = (result as PlaybackSourceResolution.Available).source as PlaybackSource.TelegramFile
        assertEquals("tgfile://video/7", source.playbackUri)
    }
}
