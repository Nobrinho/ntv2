package com.ntv2.app.core.player.io

import android.net.Uri
import android.os.SystemClock
import android.os.StatFs
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ntv2.app.core.player.telegram.PartialFileAccessor
import com.ntv2.app.core.storage.LowStorageException
import com.ntv2.app.core.storage.StorageBudget
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class GrowingFileDataSourceFactory(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long,
    private val readAheadBytes: Long,
    /** Chamado quando o download adiante é suspenso por falta de espaço (dispara limpeza de caches). */
    private val onLowStorage: () -> Unit = {}
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return GrowingFileDataSource(partialFileAccessor, stallTimeoutMs, readAheadBytes, onLowStorage)
    }
}

private const val NUDGE_INTERVAL_MS = 2_000L

private class GrowingFileDataSource(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long,
    private val readAheadBytes: Long,
    private val onLowStorage: () -> Unit
) : BaseDataSource(false) {

    private var dataSpec: DataSpec? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var fileId: Int = -1
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    // Base do download deste open (posição do dataSpec). O download é sempre estendido de forma
    // CONTÍGUA a partir daqui (só aumentando o tamanho) — nunca movendo o offset à frente, o que
    // criaria um buraco entre a fronteira baixada e a nova posição e travaria a leitura.
    private var downloadBaseOffset: Long = 0L
    // Até onde já pedimos download; mantém uma janela à frente do ponto de leitura em vez de
    // baixar o arquivo inteiro de uma vez (isso enchia o disco e o TDLib abortava por ENOSPC).
    private var lastRequestedEnd: Long = 0L
    // Cache do prefixo baixado consultado ao TDLib (evita consultar a cada leitura).
    private var cacheBase: Long = -1L
    private var cachePrefix: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        fileId = parseFileId(dataSpec.uri)
        if (fileId < 0) {
            throw IOException("URI inválida para playback: ${dataSpec.uri}")
        }

        readPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            dataSpec.length
        }

        val filePath = partialFileAccessor.resolvePath(fileId)
            ?: throw IOException("Arquivo local ainda não disponível para fileId=$fileId")
        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("Arquivo não encontrado: $filePath")
        }
        randomAccessFile = RandomAccessFile(file, "r")
        randomAccessFile?.seek(readPosition)
        transferStarted(dataSpec)

        // C2: baixa a faixa exigida por ESTE open (posição de seek ou índice no fim do arquivo),
        // mas LIMITADA a uma janela — não o arquivo inteiro (evita encher o disco). Leituras de
        // tamanho fixo (ex.: índice do MKV) pedem exatamente o solicitado.
        downloadBaseOffset = readPosition
        cacheBase = -1L
        cachePrefix = 0L
        val requestLen = if (bytesRemaining == C.LENGTH_UNSET.toLong()) readAheadBytes else bytesRemaining
        partialFileAccessor.requestRange(fileId, readPosition, requestLen, priority = 32)
        lastRequestedEnd = readPosition + requestLen

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        while (true) {
            // Legibilidade por POSIÇÃO: pergunta ao TDLib quantos bytes contíguos há a partir de
            // readPosition (reconhece frente E fim já no disco). Antes usávamos o prefixo relativo
            // ao offset único, que "esquecia" a frente após buscar o índice no fim (MKV) → travava.
            val prefix = readablePrefixFrom(readPosition)
            when (
                val plan = PartialReadPlanner.plan(
                    contiguousReadableStart = readPosition,
                    contiguousReadableEnd = readPosition + prefix,
                    readPosition = readPosition,
                    bytesRemaining = bytesRemaining,
                    requestedLength = length,
                    isComplete = partialFileAccessor.isComplete(fileId)
                )
            ) {
                is PartialReadPlanner.Plan.Read -> {
                    val read = raf.read(buffer, offset, plan.maxBytes)
                    if (read <= 0) {
                        return C.RESULT_END_OF_INPUT
                    }
                    readPosition += read
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                        bytesRemaining -= read
                    }
                    bytesTransferred(read)
                    return read
                }

                PartialReadPlanner.Plan.EndOfInput -> return C.RESULT_END_OF_INPUT

                PartialReadPlanner.Plan.Wait -> {
                    maybeRequestAhead()
                    val covered = runBlocking { awaitCoverageOrStall(readPosition) }
                    if (!covered) {
                        // Parou porque o guarda de disco suspendeu o download: erro específico, para a
                        // tela explicar o motivo em vez de um "Timeout" genérico.
                        if (!hasEnoughFreeSpace()) {
                            throw LowStorageException("Pouco espaço livre no aparelho para continuar o vídeo")
                        }
                        throw IOException("Timeout aguardando bytes do arquivo parcial")
                    }
                }
            }
        }
    }

    /**
     * Bytes contíguos disponíveis a partir de [pos], com cache: só consulta o TDLib quando a
     * posição sai da região já conhecida (evita uma consulta por leitura).
     */
    private fun readablePrefixFrom(pos: Long): Long {
        if (cacheBase in 0..pos && pos < cacheBase + cachePrefix) {
            return cacheBase + cachePrefix - pos
        }
        val prefix = runBlocking { partialFileAccessor.downloadedPrefixFrom(fileId, pos) }
        cacheBase = pos
        cachePrefix = prefix
        return prefix
    }

    /**
     * Aguarda até que [position] esteja coberta pela região contígua baixada, OU o download
     * conclua. Retorna false apenas se o download ficar TRAVADO (nenhum byte novo) por mais que
     * [stallTimeoutMs]. Usa progresso real de download (downloadedBytes) para não abortar um
     * seek lento mas em andamento — ex.: buscar o índice do MKV lá no fim do arquivo, que demora
     * o TDLib "pular" pela rede e antes causava retry/flip-flop e minutos de buffering.
     */
    private suspend fun awaitCoverageOrStall(position: Long): Boolean {
        var lastDownloaded = partialFileAccessor.downloadedBytes(fileId)
        var lastProgressAt = SystemClock.elapsedRealtime()
        var lastNudgeAt = 0L
        while (true) {
            if (partialFileAccessor.isComplete(fileId)) return true
            if (partialFileAccessor.downloadedPrefixFrom(fileId, position) > 0L) return true

            val now = SystemClock.elapsedRealtime()
            // Re-solicita periodicamente a faixa necessária: sem isso o TDLib conclui a janela
            // anterior e fica OCIOSO (o pedido do índice no fim do MKV se perdia → stall/erro →
            // ExoPlayer re-tentava → flip-flop frente/fim, minutos de buffering ou falha).
            // Respeita o guarda de disco (não re-solicita se o espaço livre estiver baixo).
            if (now - lastNudgeAt > NUDGE_INTERVAL_MS && hasEnoughFreeSpace()) {
                val len = (position - downloadBaseOffset + readAheadBytes).coerceAtLeast(readAheadBytes)
                partialFileAccessor.requestRange(fileId, downloadBaseOffset, len, priority = 32)
                lastNudgeAt = now
            }

            delay(300L)

            val downloaded = partialFileAccessor.downloadedBytes(fileId)
            if (downloaded > lastDownloaded) {
                lastDownloaded = downloaded
                lastProgressAt = SystemClock.elapsedRealtime()
            }
            if (SystemClock.elapsedRealtime() - lastProgressAt > stallTimeoutMs) return false
        }
    }

    /**
     * Mantém ~[readAheadBytes] baixados à frente do ponto de leitura, estendendo a solicitação
     * conforme a reprodução avança — em vez de baixar tudo de uma vez. Só para leituras de
     * tamanho aberto (playback); leituras fixas (índice) não estendem.
     */
    private fun maybeRequestAhead() {
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) return
        if (partialFileAccessor.isComplete(fileId)) return
        // Guarda de disco: não estende o download se o espaço livre estiver baixo. Sem isso, um
        // filme grande enche o disco e o TDLib faz abort() (ENOSPC no binlog) → app fecha. Aqui
        // a reprodução degrada (para de baixar adiante) em vez de derrubar o app.
        if (!hasEnoughFreeSpace()) return
        val desiredEnd = readPosition + readAheadBytes
        if (desiredEnd > lastRequestedEnd) {
            // Estende o download CONTÍGUO a partir da base (aumenta o tamanho), sem mover o offset
            // à frente — assim não abre buracos que travariam a leitura.
            partialFileAccessor.requestRange(
                fileId,
                downloadBaseOffset,
                desiredEnd - downloadBaseOffset,
                priority = 32
            )
            lastRequestedEnd = desiredEnd
        }
    }

    /**
     * Espaço livre suficiente para continuar baixando. O piso fica ACIMA do limiar em que o sistema
     * avisa "armazenamento baixo" (ver [StorageBudget]); antes eram 300 MB fixos, abaixo do limiar,
     * e o Fire OS acabava mostrando o aviso no meio do filme.
     */
    private fun hasEnoughFreeSpace(): Boolean {
        val parent = partialFileAccessor.resolvePath(fileId)?.let { File(it).parentFile } ?: return true
        val enough = runCatching {
            val stat = StatFs(parent.path)
            StorageBudget.canDownload(stat.availableBytes, stat.totalBytes)
        }.getOrDefault(true)
        if (!enough) onLowStorage()
        return enough
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } finally {
            randomAccessFile = null
            dataSpec?.let { transferEnded() }
            dataSpec = null
            fileId = -1
            readPosition = 0L
            bytesRemaining = C.LENGTH_UNSET.toLong()
            downloadBaseOffset = 0L
            lastRequestedEnd = 0L
        }
    }

    private fun parseFileId(uri: Uri): Int {
        val last = uri.lastPathSegment ?: return -1
        return last.toIntOrNull() ?: -1
    }
}
