package com.ntv2.app.core.player.telegram

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TdlibTelegramPlaybackDataSource(
    private val playbackGateway: TdlibPlaybackGateway
) : TelegramPlaybackDataSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = java.util.concurrent.ConcurrentHashMap<Int, MutableStateFlow<TdlibPlaybackFileState>>()
    private val observeJobs = java.util.concurrent.ConcurrentHashMap<Int, Job>()

    private val openErrors = java.util.concurrent.ConcurrentHashMap<Int, String>()

    override suspend fun inspectFile(fileId: Int): PlaybackFileHandle? {
        return runCatching { playbackGateway.openFile(fileId) }
            .onSuccess { openErrors.remove(fileId) }
            .onFailure { error -> openErrors[fileId] = error.message ?: error.javaClass.simpleName }
            .getOrNull()
            ?.toHandle()
    }

    override fun lastOpenError(fileId: Int): String? = openErrors[fileId]

    override suspend fun open(fileId: Int): PlaybackFileHandle {
        val opened = playbackGateway.openFile(fileId)
        val stateFlow = states.computeIfAbsent(fileId) { MutableStateFlow(opened) }
        stateFlow.value = opened

        observeJobs.put(fileId, scope.launch {
            playbackGateway.observeFile(fileId).collect { remote ->
                states[fileId]?.update { remote }
            }
        })?.cancel()

        return opened.toHandle()
    }

    override fun observe(fileId: Int): Flow<TdlibPlaybackFileState> {
        return states.computeIfAbsent(fileId) {
            MutableStateFlow(
                TdlibPlaybackFileState(
                    fileId = fileId,
                    localPath = "",
                    downloadedBytes = 0L,
                    expectedBytes = 0L,
                    isDownloadComplete = false
                )
            )
        }
    }

    override suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
        playbackGateway.requestChunk(fileId, offsetBytes, lengthBytes, priority)
    }

    override suspend fun close(fileId: Int) {
        observeJobs.remove(fileId)?.cancel()
        playbackGateway.cancelFile(fileId)
        states.remove(fileId)
    }

    override suspend fun deleteFile(fileId: Int) {
        observeJobs.remove(fileId)?.cancel()
        playbackGateway.deleteFile(fileId)
        states.remove(fileId)
    }

    override fun resolvePath(fileId: Int): String? = states[fileId]?.value?.localPath

    override fun downloadedBytes(fileId: Int): Long = states[fileId]?.value?.downloadedBytes ?: 0L

    override fun expectedBytes(fileId: Int): Long? = states[fileId]?.value?.expectedBytes

    override fun isComplete(fileId: Int): Boolean = states[fileId]?.value?.isDownloadComplete ?: false

    override fun contiguousReadableStart(fileId: Int): Long {
        val state = states[fileId]?.value ?: return 0L
        // Concluído: o arquivo todo está no disco, região começa em 0.
        return if (state.isDownloadComplete) 0L else state.downloadOffset
    }

    override fun contiguousReadableEnd(fileId: Int): Long {
        val state = states[fileId]?.value ?: return 0L
        val prefixEnd = state.downloadOffset + state.downloadedPrefixBytes
        return if (state.isDownloadComplete) {
            maxOf(state.expectedBytes, prefixEnd, state.downloadedBytes)
        } else {
            prefixEnd
        }
    }

    override fun requestRange(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
        scope.launch {
            playbackGateway.requestChunk(fileId, offsetBytes, lengthBytes, priority)
        }
    }

    override suspend fun downloadedPrefixFrom(fileId: Int, offset: Long): Long =
        runCatching { playbackGateway.downloadedPrefixSize(fileId, offset) }.getOrDefault(0L)

    override suspend fun awaitReadableBeyond(fileId: Int, position: Long) {
        val flow = states[fileId] ?: run {
            delay(200L)
            return
        }
        // Espera até que [position] esteja DENTRO da região contígua baixada (início <= position < fim),
        // ou o download concluir. Só checar o fim causava leitura de lixo quando o offset estava à
        // frente da posição (após seek); e loop ocupado quando a região não cobria a posição.
        flow.first { state ->
            state.isDownloadComplete ||
                (state.downloadOffset <= position && position < state.downloadOffset + state.downloadedPrefixBytes)
        }
    }

    private fun TdlibPlaybackFileState.toHandle(): PlaybackFileHandle {
        return PlaybackFileHandle(
            fileId = fileId,
            localPath = localPath,
            downloadedBytes = downloadedBytes,
            expectedBytes = expectedBytes,
            isDownloadComplete = isDownloadComplete
        )
    }
}
