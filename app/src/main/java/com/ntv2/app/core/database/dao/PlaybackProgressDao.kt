package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.PlaybackProgressEntity

/** Consultas do app ignoram tombstones (deleted = 1); as de sincronização (…ForSync) os incluem. */
@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE mediaId = :mediaId AND deleted = 0")
    suspend fun get(mediaId: String): PlaybackProgressEntity?

    /** Todos os progressos salvos (só filmes começados: dezenas de linhas, não milhares). */
    @Query("SELECT * FROM playback_progress WHERE deleted = 0")
    suspend fun getAll(): List<PlaybackProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackProgressEntity)

    /** Remove via tombstone: a linha fica para a remoção chegar aos outros aparelhos. */
    @Query("UPDATE playback_progress SET deleted = 1, updatedAt = :updatedAt WHERE mediaId = :mediaId AND deleted = 0")
    suspend fun markDeleted(mediaId: String, updatedAt: Long)

    // ---- Sincronização ----

    @Query("SELECT * FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun getForSync(mediaId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE updatedAt > :sinceMs ORDER BY updatedAt ASC")
    suspend fun changedSince(sinceMs: Long): List<PlaybackProgressEntity>
}
