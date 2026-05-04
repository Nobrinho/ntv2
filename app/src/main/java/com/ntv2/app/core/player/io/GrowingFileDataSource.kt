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

        val startWait = SystemClock.elapsedRealtime()
        while (true) {
            val available = partialFileAccessor.downloadedBytes(fileId)
            val canRead = available - readPosition

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

            if (SystemClock.elapsedRealtime() - startWait > stallTimeoutMs) {
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
