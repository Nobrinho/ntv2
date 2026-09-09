package com.ntv2.app.core.player.telegram

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import kotlinx.coroutines.flow.Flow

interface PartialFileAccessor {
    fun resolvePath(fileId: Int): String?
    fun downloadedBytes(fileId: Int): Long
    fun expectedBytes(fileId: Int): Long?
    fun isComplete(fileId: Int): Boolean

    /**
     * Offset absoluto até onde os bytes estão disponíveis de forma contígua
     * (downloadOffset + downloadedPrefixSize). É o limite seguro de leitura.
     */
    fun contiguousReadableEnd(fileId: Int): Long

    /**
     * Solicita ao Telegram a faixa a partir de [offsetBytes]. lengthBytes=0 => até o fim.
     * Fire-and-forget (a implementação despacha na própria scope de IO).
     */
    fun requestRange(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int)
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
