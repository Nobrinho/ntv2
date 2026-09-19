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
     * Bytes contíguos já baixados a partir de [offset], consultando o TDLib diretamente.
     * Reconhece bytes no disco em QUALQUER região (frente e fim), ao contrário do prefixo
     * relativo ao offset único de download.
     */
    suspend fun downloadedPrefixFrom(fileId: Int, offset: Long): Long

    /**
     * Janela deslizante: fim do trecho [início fixado, evictedEnd) cujos blocos já foram liberados
     * do disco (punch hole). 0 = nada liberado. O TDLib ainda considera esses bytes baixados.
     */
    fun evictedEnd(fileId: Int): Long = 0L

    /** Registra que o trecho até [end] foi liberado do disco. */
    fun markEvicted(fileId: Int, end: Long) = Unit

    /**
     * Apaga a cópia local (inclusive os trechos liberados) para o TDLib baixar de novo a partir
     * de onde o player precisar — usado ao voltar para um trecho já liberado.
     */
    suspend fun resetLocalCopy(fileId: Int) = Unit
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

    /** Motivo da última falha ao abrir o arquivo no TDLib (null se não houve). */
    fun lastOpenError(fileId: Int): String? = null
}
