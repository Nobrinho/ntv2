package com.ntv2.app.core.player.progress

import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.WatchHistoryDao
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** Metadados denormalizados de um título aberto, para renderizar histórico/continuar sem rede. */
data class WatchedMediaMeta(
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val posterPath: String?,
    val thumbnailPath: String?,
    val durationSeconds: Int,
    val genres: String?
)

/** Um item do histórico (ou de "continuar assistindo"). */
data class WatchedItem(
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val posterPath: String?,
    val thumbnailPath: String?,
    val durationMs: Long,
    val lastPositionMs: Long,
    val completed: Boolean,
    val genres: String?,
    val updatedAt: Long
)

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

    // ---- Histórico / Continuar assistindo (personalização) ----

    /** Grava os metadados ao ABRIR um título (preserva a posição já salva, se houver). */
    suspend fun recordOpened(meta: WatchedMediaMeta) {}

    /** Histórico reativo (tudo, mais recente primeiro). */
    fun observeHistory(): Flow<List<WatchedItem>> = emptyFlow()

    /** "Continuar assistindo": não concluídos, com posição na janela útil, mais recente primeiro. */
    suspend fun continueWatching(limit: Int = 20): List<WatchedItem> = emptyList()

    /** Histórico completo (tudo). */
    suspend fun history(): List<WatchedItem> = emptyList()

    /** Remove um item do histórico (também zera o progresso salvo). */
    suspend fun removeFromHistory(mediaId: String) {}

    /** Limpa todo o histórico (mantém favoritos). */
    suspend fun clearHistory() {}
}

class RoomPlaybackProgressStore(
    private val dao: PlaybackProgressDao,
    // Opcional: quando ausente (ex.: testes de resume), o histórico simplesmente não é mantido.
    private val historyDao: WatchHistoryDao? = null
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
            PlaybackProgressPolicy.SaveAction.Ignore -> {
                // Ignorado para resume (começo demais), mas ainda atualiza a posição do histórico.
            }
        }
        // Atualiza a posição/estado do histórico (no-op se a linha não existir ou sem historyDao).
        val completed = durationMs > 0L && positionMs >= durationMs - PlaybackProgressPolicy.END_GUARD_MS
        historyDao?.updateProgress(mediaId, positionMs, durationMs, completed, System.currentTimeMillis())
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

    // ---- Histórico / Continuar assistindo ----

    override suspend fun recordOpened(meta: WatchedMediaMeta) {
        val dao = historyDao ?: return
        val existing = dao.get(meta.mediaId)
        dao.upsert(
            WatchHistoryEntity(
                mediaId = meta.mediaId,
                channelId = meta.channelId,
                channelTitle = meta.channelTitle,
                title = meta.title,
                posterPath = meta.posterPath,
                thumbnailPath = meta.thumbnailPath,
                durationMs = if (meta.durationSeconds > 0) meta.durationSeconds * 1_000L else existing?.durationMs ?: 0L,
                lastPositionMs = existing?.lastPositionMs ?: 0L,
                completed = existing?.completed ?: false,
                genres = meta.genres ?: existing?.genres,
                updatedAt = System.currentTimeMillis()
            )
        )
        changesFlow.tryEmit(meta.mediaId)
    }

    override fun observeHistory(): Flow<List<WatchedItem>> =
        historyDao?.observeAll()?.map { list -> list.map { it.toItem() } } ?: emptyFlow()

    override suspend fun continueWatching(limit: Int): List<WatchedItem> {
        val dao = historyDao ?: return emptyList()
        return dao.getAll()
            .asSequence()
            .filter { !it.completed }
            .filter { it.lastPositionMs >= PlaybackProgressPolicy.MIN_POSITION_MS }
            .filter { it.durationMs <= 0L || it.lastPositionMs < it.durationMs - PlaybackProgressPolicy.END_GUARD_MS }
            .take(limit)
            .map { it.toItem() }
            .toList()
    }

    override suspend fun history(): List<WatchedItem> =
        historyDao?.getAll()?.map { it.toItem() } ?: emptyList()

    override suspend fun removeFromHistory(mediaId: String) {
        historyDao?.delete(mediaId)
        dao.delete(mediaId)
        changesFlow.tryEmit(mediaId)
    }

    override suspend fun clearHistory() {
        historyDao?.clear()
    }

    private fun WatchHistoryEntity.toItem() = WatchedItem(
        mediaId = mediaId,
        channelId = channelId,
        channelTitle = channelTitle,
        title = title,
        posterPath = posterPath,
        thumbnailPath = thumbnailPath,
        durationMs = durationMs,
        lastPositionMs = lastPositionMs,
        completed = completed,
        genres = genres,
        updatedAt = updatedAt
    )
}
