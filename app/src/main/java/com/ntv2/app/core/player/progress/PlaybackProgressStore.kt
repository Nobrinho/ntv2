package com.ntv2.app.core.player.progress

import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

/** Persiste a posição de reprodução por mídia, para retomar de onde parou. */
interface PlaybackProgressStore {
    /** Posição para retomar (0 se não houver nada útil salvo). */
    suspend fun resumePositionMs(mediaId: String, durationMs: Long): Long

    /** Registra progresso; salva, ignora ou limpa conforme a política. */
    suspend fun onProgress(mediaId: String, positionMs: Long, durationMs: Long)

    /** Remove o progresso salvo (ex.: assistido até o fim). */
    suspend fun clear(mediaId: String)

    /** Posição salva (ms) por mídia, apenas para os ids com progresso. Para exibir nos cards. */
    suspend fun savedPositions(mediaIds: List<String>): Map<String, Long>

    /** Emite o mediaId a cada gravação/limpeza — para a UI (ex.: Detalhes) refletir o progresso. */
    val changes: Flow<String> get() = emptyFlow()
}

class RoomPlaybackProgressStore(
    private val dao: PlaybackProgressDao
) : PlaybackProgressStore {

    private val changesFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val changes: Flow<String> = changesFlow

    override suspend fun resumePositionMs(mediaId: String, durationMs: Long): Long {
        val saved = dao.get(mediaId)?.positionMs ?: return 0L
        return PlaybackProgressPolicy.resumePosition(saved, durationMs)
    }

    override suspend fun onProgress(mediaId: String, positionMs: Long, durationMs: Long) {
        when (val action = PlaybackProgressPolicy.onProgress(positionMs, durationMs)) {
            is PlaybackProgressPolicy.SaveAction.Save -> dao.upsert(
                PlaybackProgressEntity(
                    mediaId = mediaId,
                    positionMs = action.positionMs,
                    durationMs = action.durationMs,
                    updatedAt = System.currentTimeMillis()
                )
            )
            PlaybackProgressPolicy.SaveAction.Clear -> dao.delete(mediaId)
            PlaybackProgressPolicy.SaveAction.Ignore -> return
        }
        changesFlow.tryEmit(mediaId)
    }

    override suspend fun clear(mediaId: String) {
        dao.delete(mediaId)
        changesFlow.tryEmit(mediaId)
    }

    override suspend fun savedPositions(mediaIds: List<String>): Map<String, Long> {
        if (mediaIds.isEmpty()) return emptyMap()
        // Lê a tabela inteira e cruza na memória: ela só tem os filmes começados (poucas linhas),
        // enquanto a grade pode ter até 1.000 cards — um IN (...) com todos os ids era mais caro e
        // estourava o limite de 999 parâmetros do SQLite antes do Android 11.
        val wanted = mediaIds.toHashSet()
        return dao.getAll()
            .filter { it.mediaId in wanted }
            .associate { it.mediaId to it.positionMs }
    }
}
