package com.ntv2.app.core.player.telegram

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import kotlinx.coroutines.flow.Flow

interface PartialFileAccessor {
    fun resolvePath(fileId: Int): String?
    fun downloadedBytes(fileId: Int): Long
    fun expectedBytes(fileId: Int): Long?
    fun isComplete(fileId: Int): Boolean

    /**
     * Início da região contígua disponível (downloadOffset; 0 se o download concluiu).
     * Ler numa posição ANTES disso retornaria lixo — a região baixada começa aqui.
     */
    fun contiguousReadableStart(fileId: Int): Long

    /**
     * Fim da região contígua disponível (downloadOffset + downloadedPrefixSize).
     * A leitura só é válida no intervalo [contiguousReadableStart, contiguousReadableEnd).
     */
    fun contiguousReadableEnd(fileId: Int): Long

    /**
     * Solicita ao Telegram a faixa a partir de [offsetBytes]. lengthBytes=0 => até o fim.
     * Fire-and-forget (a implementação despacha na própria scope de IO).
     */
    fun requestRange(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int)

    /**
     * Suspende até [contiguousReadableEnd] ultrapassar [position] (qualquer avanço) ou o
     * download concluir. Permite espera reativa no lugar de polling.
     */
    suspend fun awaitReadableBeyond(fileId: Int, position: Long)
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

    /** Encerra e remove a cópia local do arquivo (libera armazenamento). */
    suspend fun deleteFile(fileId: Int)
}
