package com.ntv2.app.core.player.io

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ntv2.app.core.player.telegram.PartialFileAccessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class GrowingFileDataSourceFactory(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long,
    private val readAheadBytes: Long
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return GrowingFileDataSource(partialFileAccessor, stallTimeoutMs, readAheadBytes)
    }
}

private class GrowingFileDataSource(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long,
    private val readAheadBytes: Long
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
            // C2: contiguidade real a partir do downloadOffset, não o downloadedSize (que pode
            // conter regiões esparsas/"garbage" segundo a doc do TDLib). A decisão fica isolada
            // em PartialReadPlanner (testável em JVM).
            val readableStart = partialFileAccessor.contiguousReadableStart(fileId)
            val readableEnd = partialFileAccessor.contiguousReadableEnd(fileId)
            maybeRequestAhead()
            when (
                val plan = PartialReadPlanner.plan(
                    contiguousReadableStart = readableStart,
                    contiguousReadableEnd = readableEnd,
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
                    // A4: espera reativa — suspende até o prefixo contíguo avançar (sinalizado pelo
                    // StateFlow do download), em vez de polling com sleep na thread do loader.
                    // Timeout adaptativo: aborta só se NÃO houver avanço dentro do stallTimeout.
                    val progressed = runBlocking {
                        withTimeoutOrNull(stallTimeoutMs) {
                            // Espera até que a posição de leitura esteja coberta pela região baixada.
                            partialFileAccessor.awaitReadableBeyond(fileId, readPosition)
                            true
                        }
                    }
                    if (progressed == null) {
                        throw IOException("Timeout aguardando bytes do arquivo parcial")
                    }
                }
            }
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
