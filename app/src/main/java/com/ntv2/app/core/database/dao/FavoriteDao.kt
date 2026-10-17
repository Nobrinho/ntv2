package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/** Consultas da UI ignoram tombstones (deleted = 1); as de sincronização (…ForSync) os incluem. */
@Dao
interface FavoriteDao {
    /** Minha lista, do mais recente ao mais antigo. */
    @Query("SELECT * FROM favorites WHERE deleted = 0 ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    /** Ids favoritados — para marcar o coração nos cards/detalhes. */
    @Query("SELECT mediaId FROM favorites WHERE deleted = 0")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId AND deleted = 0)")
    suspend fun isFavorite(mediaId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    /** Remove via tombstone: a linha fica para a remoção chegar aos outros aparelhos. */
    @Query("UPDATE favorites SET deleted = 1, updatedAt = :updatedAt WHERE mediaId = :mediaId AND deleted = 0")
    suspend fun markDeleted(mediaId: String, updatedAt: Long)

    // ---- Sincronização ----

    @Query("SELECT * FROM favorites WHERE mediaId = :mediaId")
    suspend fun getForSync(mediaId: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE updatedAt > :sinceMs ORDER BY updatedAt ASC")
    suspend fun changedSince(sinceMs: Long): List<FavoriteEntity>
}
