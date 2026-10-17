package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Consultas da UI ignoram tombstones (deleted = 1); as de sincronização (…ForSync) os incluem. */
@Dao
interface WatchHistoryDao {
    /** Histórico completo, mais recente primeiro. Poucas dezenas de linhas na prática. */
    @Query("SELECT * FROM watch_history WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE deleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId AND deleted = 0")
    suspend fun get(mediaId: String): WatchHistoryEntity?

    /** Grava/atualiza os metadados ao abrir (preserva a posição se a linha já existir). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    /** Atualiza só a posição/estado a partir dos ticks do player (não ressuscita tombstones). */
    @Query(
        "UPDATE watch_history SET lastPositionMs = :positionMs, durationMs = :durationMs, " +
            "completed = :completed, updatedAt = :updatedAt WHERE mediaId = :mediaId AND deleted = 0"
    )
    suspend fun updateProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
        updatedAt: Long
    )

    /** Remove via tombstone: a linha fica para a remoção chegar aos outros aparelhos. */
    @Query("UPDATE watch_history SET deleted = 1, updatedAt = :updatedAt WHERE mediaId = :mediaId AND deleted = 0")
    suspend fun markDeleted(mediaId: String, updatedAt: Long)

    /** Limpa todo o histórico via tombstone. */
    @Query("UPDATE watch_history SET deleted = 1, updatedAt = :updatedAt WHERE deleted = 0")
    suspend fun markAllDeleted(updatedAt: Long)

    // ---- Sincronização ----

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId")
    suspend fun getForSync(mediaId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE updatedAt > :sinceMs ORDER BY updatedAt ASC")
    suspend fun changedSince(sinceMs: Long): List<WatchHistoryEntity>

    /**
     * Posição vinda de outro aparelho (pull do playback_progress): atualiza a linha do histórico sem
     * mexer no updatedAt — não é uma alteração local e não deve voltar ao servidor.
     */
    @Query(
        "UPDATE watch_history SET lastPositionMs = :positionMs, durationMs = :durationMs, " +
            "completed = :completed WHERE mediaId = :mediaId AND deleted = 0"
    )
    suspend fun applyRemotePosition(mediaId: String, positionMs: Long, durationMs: Long, completed: Boolean)
}
