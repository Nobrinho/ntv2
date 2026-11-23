package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Consultas da UI são do perfil ativo e ignoram tombstones (deleted = 1); as de sincronização
 * (…ForSync, changedSince) os incluem e cobrem todos os perfis.
 */
@Dao
interface WatchHistoryDao {
    /** Histórico completo do perfil, mais recente primeiro. Poucas dezenas de linhas na prática. */
    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(profileId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND deleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAll(profileId: String): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0")
    suspend fun get(profileId: String, mediaId: String): WatchHistoryEntity?

    /** Grava/atualiza os metadados ao abrir (preserva a posição se a linha já existir). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    /** Atualiza só a posição/estado a partir dos ticks do player (não ressuscita tombstones). */
    @Query(
        "UPDATE watch_history SET lastPositionMs = :positionMs, durationMs = :durationMs, " +
            "completed = :completed, updatedAt = :updatedAt " +
            "WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0"
    )
    suspend fun updateProgress(
        profileId: String,
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
        updatedAt: Long
    )

    /** Remove via tombstone: a linha fica para a remoção chegar aos outros aparelhos. */
    @Query(
        "UPDATE watch_history SET deleted = 1, updatedAt = :updatedAt " +
            "WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0"
    )
    suspend fun markDeleted(profileId: String, mediaId: String, updatedAt: Long)

    /** Limpa todo o histórico do perfil via tombstone (também usado quando o perfil é apagado). */
    @Query("UPDATE watch_history SET deleted = 1, updatedAt = :updatedAt WHERE profileId = :profileId AND deleted = 0")
    suspend fun markAllDeleted(profileId: String, updatedAt: Long)

    // ---- Sincronização ----

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun getForSync(profileId: String, mediaId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE updatedAt > :sinceMs ORDER BY updatedAt ASC")
    suspend fun changedSince(sinceMs: Long): List<WatchHistoryEntity>

    /**
     * Posição vinda de outro aparelho (pull do playback_progress): atualiza a linha do histórico sem
     * mexer no updatedAt — não é uma alteração local e não deve voltar ao servidor.
     */
    @Query(
        "UPDATE watch_history SET lastPositionMs = :positionMs, durationMs = :durationMs, " +
            "completed = :completed WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0"
    )
    suspend fun applyRemotePosition(
        profileId: String,
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean
    )

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}
