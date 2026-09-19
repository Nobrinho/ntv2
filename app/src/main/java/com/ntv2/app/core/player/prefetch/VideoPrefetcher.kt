package com.ntv2.app.core.player.prefetch

import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pré-download do início do vídeo enquanto o usuário está na tela de Detalhes: ao apertar
 * Assistir, boa parte dos bytes iniciais já chegou e o vídeo começa mais rápido.
 */
interface VideoPrefetcher {
    /** Começa a baixar os primeiros MB do arquivo em prioridade baixa. */
    fun prefetch(fileId: Int)

    /** Saiu dos Detalhes sem assistir: cancela e remove o parcial (a Fire TV tem pouco espaço). */
    fun cancel(fileId: Int)
}

class TdlibVideoPrefetcher(
    private val gateway: TdlibPlaybackGateway
) : VideoPrefetcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun prefetch(fileId: Int) {
        if (fileId <= 0) return
        scope.launch {
            // Prioridade baixa (o player pede 32): não disputa com uma reprodução em andamento.
            runCatching { gateway.requestChunk(fileId, 0L, PREFETCH_BYTES, PREFETCH_PRIORITY) }
        }
    }

    override fun cancel(fileId: Int) {
        if (fileId <= 0) return
        scope.launch { runCatching { gateway.deleteFile(fileId) } }
    }

    private companion object {
        const val PREFETCH_BYTES = 8L * 1024L * 1024L
        const val PREFETCH_PRIORITY = 8
    }
}
