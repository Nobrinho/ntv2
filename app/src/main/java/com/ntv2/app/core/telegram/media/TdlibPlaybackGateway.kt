package com.ntv2.app.core.telegram.media

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

data class TdlibPlaybackFileState(
    val fileId: Int,
    val localPath: String,
    val downloadedBytes: Long,
    val expectedBytes: Long,
    val isDownloadComplete: Boolean,
    // C2: offset a partir do qual o prefixo contíguo é válido e o tamanho desse prefixo.
    // Necessário para MP4 com moov no fim e para seeks (TDLib: downloadOffset/downloadedPrefixSize).
    val downloadOffset: Long = 0L,
    val downloadedPrefixBytes: Long = 0L
)

interface TdlibPlaybackGateway {
    suspend fun openFile(fileId: Int): TdlibPlaybackFileState
    fun observeFile(fileId: Int): Flow<TdlibPlaybackFileState>
    suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int)
    suspend fun cancelFile(fileId: Int)
}

class FakeTdlibPlaybackGateway(
    context: Context
) : TdlibPlaybackGateway {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = mutableMapOf<Int, MutableStateFlow<TdlibPlaybackFileState>>()
    private val writeJobs = mutableMapOf<Int, Job>()

    override suspend fun openFile(fileId: Int): TdlibPlaybackFileState {
        val dir = File(appContext.cacheDir, "telegram-playback")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "tg_video_$fileId.partial")
        if (!file.exists()) {
            file.createNewFile()
        }

        val expected = 180L * 1024L * 1024L
        val initial = TdlibPlaybackFileState(
            fileId = fileId,
            localPath = file.absolutePath,
            downloadedBytes = file.length(),
            expectedBytes = expected,
            isDownloadComplete = file.length() >= expected,
            downloadOffset = 0L,
            downloadedPrefixBytes = file.length()
        )

        val flow = states.getOrPut(fileId) { MutableStateFlow(initial) }
        flow.value = initial
        return initial
    }

    override fun observeFile(fileId: Int): Flow<TdlibPlaybackFileState> {
        return states.getOrPut(fileId) {
            MutableStateFlow(
                TdlibPlaybackFileState(
                    fileId = fileId,
                    localPath = File(appContext.cacheDir, "telegram-playback/tg_video_$fileId.partial").absolutePath,
                    downloadedBytes = 0L,
                    expectedBytes = 180L * 1024L * 1024L,
                    isDownloadComplete = false
                )
            )
        }.asStateFlow()
    }

    override suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
        val flow = states[fileId] ?: return
        val state = flow.value
        if (state.isDownloadComplete) {
            return
        }

        writeJobs[fileId]?.cancel()
        writeJobs[fileId] = scope.launch {
            val file = File(state.localPath)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val expected = state.expectedBytes
            val start = min(offsetBytes, expected)
            val end = min(offsetBytes + lengthBytes, expected)
            if (end <= start) {
                return@launch
            }

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var cursor = start
                while (cursor < end) {
                    val remaining = (end - cursor).toInt()
                    val write = min(buffer.size, remaining)
                    raf.write(buffer, 0, write)
                    cursor += write
                    flow.value = flow.value.copy(
                        downloadedBytes = maxOf(flow.value.downloadedBytes, cursor),
                        isDownloadComplete = cursor >= expected,
                        downloadedPrefixBytes = maxOf(flow.value.downloadedPrefixBytes, cursor)
                    )
                    delay(if (priority > 0) 20 else 45)
                }
            }
        }
    }

    override suspend fun cancelFile(fileId: Int) {
        writeJobs.remove(fileId)?.cancel()
    }
}
