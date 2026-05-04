package com.ntv2.app.core.player.telegram

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import kotlinx.coroutines.flow.Flow

interface PartialFileAccessor {
    fun resolvePath(fileId: Int): String?
    fun downloadedBytes(fileId: Int): Long
    fun expectedBytes(fileId: Int): Long?
    fun isComplete(fileId: Int): Boolean
}

data class PlaybackFileHandle(
    val fileId: Int,
    val localPath: String,
    val downloadedBytes: Long,
    val expectedBytes: Long,
    val isDownloadComplete: Boolean
)

interface TelegramPlaybackDataSource : PartialFileAccessor {
    suspend fun inspectFile(fileId: Int): PlaybackFileHandle?
    suspend fun open(fileId: Int): PlaybackFileHandle
    fun observe(fileId: Int): Flow<TdlibPlaybackFileState>
    suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int)
    suspend fun close(fileId: Int)
}
