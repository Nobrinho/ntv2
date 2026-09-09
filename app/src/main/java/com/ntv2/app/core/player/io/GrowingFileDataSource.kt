package com.ntv2.app.core.player.io

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ntv2.app.core.player.telegram.PartialFileAccessor
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.min

class GrowingFileDataSourceFactory(
    private val partialFileAccessor: PartialFileAccessor,
    private val pollIntervalMs: Long,
    private val stallTimeoutMs: Long
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return GrowingFileDataSource(partialFileAccessor, pollIntervalMs, stallTimeoutMs)
    }
}

private class GrowingFileDataSource(
    private val partialFileAccessor: PartialFileAccessor,
    private val pollIntervalMs: Long,
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

        var lastReadableEnd = -1L
        var lastProgressAt = SystemClock.elapsedRealtime()
        while (true) {
            // C2: contiguidade real a partir do downloadOffset, não o downloadedSize (que pode
            // conter regiões esparsas/"garbage" segundo a doc do TDLib).
            val readableEnd = partialFileAccessor.contiguousReadableEnd(fileId)
            val canRead = readableEnd - readPosition

            if (canRead > 0L) {
                val maxByAvailability = min(canRead, length.toLong()).toInt()
                val maxToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                    maxByAvailability
                } else {
                    min(maxByAvailability.toLong(), bytesRemaining).toInt()
                }

                val read = raf.read(buffer, offset, maxToRead)
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

            if (partialFileAccessor.isComplete(fileId)) {
                return C.RESULT_END_OF_INPUT
            }

            // Timeout adaptativo: só aborta se o prefixo contíguo não avançar dentro da janela
            // (rede lenta mas progredindo não é abortada; rede parada é).
            val now = SystemClock.elapsedRealtime()
            if (readableEnd > lastReadableEnd) {
                lastReadableEnd = readableEnd
                lastProgressAt = now
            }
            if (now - lastProgressAt > stallTimeoutMs) {
                throw IOException("Timeout aguardando bytes do arquivo parcial")
            }

            SystemClock.sleep(pollIntervalMs)
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
