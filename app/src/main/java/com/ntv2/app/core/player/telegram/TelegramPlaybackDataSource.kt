package com.ntv2.app.core.player.telegram

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import kotlinx.coroutines.flow.Flow

interface PartialFileAccessor {
    fun resolvePath(fileId: Int): String?
    fun downloadedBytes(fileId: Int): Long
    fun expectedBytes(fileId: Int): Long?
    fun isComplete(fileId: Int): Boolean

    /**
     * Solicita ao Telegram a faixa a partir de [offsetBytes]. lengthBytes=0 => até o fim.
     * Fire-and-forget: a implementação envia os pedidos de cada arquivo em ordem (vale o último).
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

    /**
     * Libera do disco [start, end) em segundo plano. A implementação avança [evictedEnd] ANTES de
     * liberar cada bloco, para o player nunca ler um trecho no meio da liberação.
     */
    fun scheduleEviction(fileId: Int, start: Long, end: Long) = Unit

    /**
     * Apaga a cópia local (inclusive os trechos liberados) para o TDLib baixar de novo a partir
     * de onde o player precisar — usado ao voltar para um trecho já liberado.
     */
    suspend fun resetLocalCopy(fileId: Int) = Unit

    /**
     * Filme dividido: abre no TDLib uma parte que o player ainda não tocou (registra o estado e
     * começa o download do início). Sem isso [resolvePath] daria null para as partes 2 em diante.
     * Não faz nada se a parte já está aberta.
     */
    suspend fun ensureOpen(fileId: Int) = Unit

    /**
     * Filme dividido: começa a baixar o início de [fileId] em segundo plano (~[bytes]), para a
     * emenda entre partes não travar esperando o começo da parte seguinte.
     */
    fun prefetchHead(fileId: Int, bytes: Long) = Unit

    /** Filme dividido: apaga a parte [fileId] já assistida, em segundo plano (libera armazenamento). */
    fun discardFile(fileId: Int) = Unit
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
    suspend fun close(fileId: Int)

    /** Encerra e remove a cópia local do arquivo (libera armazenamento). */
    suspend fun deleteFile(fileId: Int)

    /** Motivo da última falha ao abrir o arquivo no TDLib (null se não houve). */
    fun lastOpenError(fileId: Int): String? = null
}
