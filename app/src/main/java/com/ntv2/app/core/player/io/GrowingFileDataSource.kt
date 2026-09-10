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
    private val stallTimeoutMs: Long
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return GrowingFileDataSource(partialFileAccessor, stallTimeoutMs)
    }
}

private class GrowingFileDataSource(
    private val partialFileAccessor: PartialFileAccessor,
    private val stallTimeoutMs: Long
) : BaseDataSource(false) {

    private var dataSpec: DataSpec? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var fileId: Int = -1
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

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

        // C2: garante que o Telegram baixe a faixa exigida por ESTE open (posição de seek
        // ou moov no fim do MP4). lengthBytes=0 => baixa até o fim a partir do offset.
        val requestLen = if (dataSpec.length == C.LENGTH_UNSET.toLong()) 0L else dataSpec.length
        partialFileAccessor.requestRange(fileId, readPosition, requestLen, priority = 32)

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
            val readableEnd = partialFileAccessor.contiguousReadableEnd(fileId)
            when (
                val plan = PartialReadPlanner.plan(
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
                            partialFileAccessor.awaitReadableBeyond(fileId, readableEnd)
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
        }
    }

    private fun parseFileId(uri: Uri): Int {
        val last = uri.lastPathSegment ?: return -1
        return last.toIntOrNull() ?: -1
    }
}
