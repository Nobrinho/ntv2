package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    /** Histórico completo, mais recente primeiro. Poucas dezenas de linhas na prática. */
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): WatchHistoryEntity?

    /** Grava/atualiza os metadados ao abrir (preserva a posição se a linha já existir). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    /** Atualiza só a posição/estado a partir dos ticks do player. */
    @Query(
        "UPDATE watch_history SET lastPositionMs = :positionMs, durationMs = :durationMs, " +
            "completed = :completed, updatedAt = :updatedAt WHERE mediaId = :mediaId"
    )
    suspend fun updateProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
        updatedAt: Long
    )

    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}
