package com.ntv2.app.core.player.source

import com.ntv2.app.core.multipart.MultiPartRegistry
import com.ntv2.app.core.multipart.PartRef
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
        override suspend fun close(fileId: Int) = Unit
        override suspend fun deleteFile(fileId: Int) = Unit
        override fun resolvePath(fileId: Int): String? = handle?.localPath
        override fun downloadedBytes(fileId: Int): Long = handle?.downloadedBytes ?: 0L
        override fun expectedBytes(fileId: Int): Long? = handle?.expectedBytes
        override fun isComplete(fileId: Int): Boolean = handle?.isDownloadComplete ?: false
        override fun requestRange(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
            chunkRequests += Triple(fileId, offsetBytes, lengthBytes)
        }
        override suspend fun downloadedPrefixFrom(fileId: Int, offset: Long): Long = 0L
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

    @Test
    fun `filme dividido abre a uri multi com o tamanho total das partes`() = runTest {
        val f = temp.newFile("p1.partial").apply { writeBytes(ByteArray(8 * 1024 * 1024)) }
        val handle = PlaybackFileHandle(
            fileId = 11,
            localPath = f.absolutePath,
            downloadedBytes = 8L * 1024 * 1024,
            expectedBytes = 1_900L,
            isDownloadComplete = false
        )
        val registry = MultiPartRegistry().apply {
            register(listOf(PartRef(11, 1_900), PartRef(12, 1_900), PartRef(13, 700)))
        }
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(handle), registry)

        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m", fileId = 11))

        val available = result as PlaybackSourceResolution.Available
        val source = available.source as PlaybackSource.TelegramFile
        assertEquals("tgfile://multi/11", source.playbackUri)
        assertEquals(4_500L, source.expectedBytes)
        assertEquals(4_500L, available.availability.expectedBytes)
        assertEquals(11, source.fileId)
    }

    @Test
    fun `filme dividido nunca e dado como completo mesmo com a parte 1 completa`() = runTest {
        val f = temp.newFile("p1c.partial").apply { writeBytes(ByteArray(1024)) }
        val handle = PlaybackFileHandle(
            fileId = 11, localPath = f.absolutePath, downloadedBytes = 1024,
            expectedBytes = 1024, isDownloadComplete = true
        )
        val registry = MultiPartRegistry().apply { register(listOf(PartRef(11, 1_024), PartRef(12, 1_024))) }
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(handle), registry)

        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m", fileId = 11)) as PlaybackSourceResolution.Available

        assertEquals(false, (result.source as PlaybackSource.TelegramFile).isDownloadComplete)
        assertEquals(false, result.availability.isDownloadComplete)
    }

    @Test
    fun `arquivo unico fora do registro continua usando a uri de video`() = runTest {
        val f = temp.newFile("u.partial").apply { writeBytes(ByteArray(1024)) }
        val handle = PlaybackFileHandle(
            fileId = 9, localPath = f.absolutePath, downloadedBytes = 1024,
            expectedBytes = 1024, isDownloadComplete = true
        )
        val registry = MultiPartRegistry().apply { register(listOf(PartRef(11, 10), PartRef(12, 10))) }
        val resolver = DefaultPlaybackSourceResolver(FakeDataSource(handle), registry)

        val result = resolver.resolve(PlaybackSourceRequest(mediaId = "m", fileId = 9)) as PlaybackSourceResolution.Available

        assertEquals("tgfile://video/9", (result.source as PlaybackSource.TelegramFile).playbackUri)
        assertEquals(true, result.availability.isDownloadComplete)
    }
}
