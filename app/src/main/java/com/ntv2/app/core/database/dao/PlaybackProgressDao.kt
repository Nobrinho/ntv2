package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.PlaybackProgressEntity

/**
 * Consultas do app são do perfil ativo e ignoram tombstones (deleted = 1); as de sincronização
 * (…ForSync, changedSince) os incluem e cobrem todos os perfis.
 */
@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0")
    suspend fun get(profileId: String, mediaId: String): PlaybackProgressEntity?

    /** Todos os progressos salvos do perfil (só filmes começados: dezenas de linhas, não milhares). */
    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND deleted = 0")
    suspend fun getAll(profileId: String): List<PlaybackProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackProgressEntity)

    /** Remove via tombstone: a linha fica para a remoção chegar aos outros aparelhos. */
    @Query(
        "UPDATE playback_progress SET deleted = 1, updatedAt = :updatedAt " +
            "WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0"
    )
    suspend fun markDeleted(profileId: String, mediaId: String, updatedAt: Long)

    // ---- Sincronização ----

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun getForSync(profileId: String, mediaId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE updatedAt > :sinceMs ORDER BY updatedAt ASC")
    suspend fun changedSince(sinceMs: Long): List<PlaybackProgressEntity>

    @Query("UPDATE playback_progress SET deleted = 1, updatedAt = :updatedAt WHERE profileId = :profileId AND deleted = 0")
    suspend fun markProfileDeleted(profileId: String, updatedAt: Long)

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAll()
}
