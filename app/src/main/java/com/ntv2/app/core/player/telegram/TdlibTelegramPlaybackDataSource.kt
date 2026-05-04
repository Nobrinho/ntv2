package com.ntv2.app.core.player.telegram

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TdlibTelegramPlaybackDataSource(
    private val playbackGateway: TdlibPlaybackGateway
) : TelegramPlaybackDataSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = mutableMapOf<Int, MutableStateFlow<TdlibPlaybackFileState>>()
    private val observeJobs = mutableMapOf<Int, Job>()

    override suspend fun inspectFile(fileId: Int): PlaybackFileHandle? {
        return runCatching { playbackGateway.openFile(fileId) }
            .getOrNull()
            ?.toHandle()
    }

    override suspend fun open(fileId: Int): PlaybackFileHandle {
        val opened = playbackGateway.openFile(fileId)
        val stateFlow = states.getOrPut(fileId) { MutableStateFlow(opened) }
        stateFlow.value = opened

        observeJobs[fileId]?.cancel()
        observeJobs[fileId] = scope.launch {
            playbackGateway.observeFile(fileId).collect { remote ->
                states[fileId]?.update { remote }
            }
        }

        return opened.toHandle()
    }

    override fun observe(fileId: Int): Flow<TdlibPlaybackFileState> {
        return states.getOrPut(fileId) {
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

    override fun resolvePath(fileId: Int): String? = states[fileId]?.value?.localPath

    override fun downloadedBytes(fileId: Int): Long = states[fileId]?.value?.downloadedBytes ?: 0L

    override fun expectedBytes(fileId: Int): Long? = states[fileId]?.value?.expectedBytes

    override fun isComplete(fileId: Int): Boolean = states[fileId]?.value?.isDownloadComplete ?: false

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
