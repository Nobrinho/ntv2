package com.ntv2.app.core.storage

import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.flow.Flow

/**
 * Registra em [PendingFileDeletions] todo vídeo que passa a ter bytes no disco (abrir, pré-download,
 * janela do player) e desregistra quando ele é apagado. Assim um parcial deixado por um processo
 * morto é encontrado e removido na próxima abertura do app.
 */
class TrackingPlaybackGateway(
    private val delegate: TdlibPlaybackGateway,
    private val pending: PendingFileDeletions
) : TdlibPlaybackGateway by delegate {

    override suspend fun openFile(fileId: Int): TdlibPlaybackFileState {
        pending.markDownloading(fileId)
        return delegate.openFile(fileId)
    }

    override fun observeFile(fileId: Int): Flow<TdlibPlaybackFileState> = delegate.observeFile(fileId)

    override suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
        pending.markDownloading(fileId)
        delegate.requestChunk(fileId, offsetBytes, lengthBytes, priority)
    }

    override suspend fun deleteFile(fileId: Int) {
        delegate.deleteFile(fileId)
        pending.markDeleted(fileId)
    }
}
