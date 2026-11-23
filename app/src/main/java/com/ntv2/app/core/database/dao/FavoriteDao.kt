package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Consultas da UI são do perfil ativo e ignoram tombstones (deleted = 1); as de sincronização
 * (…ForSync, changedSince) os incluem e cobrem todos os perfis.
 */
@Dao
interface FavoriteDao {
    /** Minha lista do perfil, do mais recente ao mais antigo. */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND deleted = 0 ORDER BY addedAt DESC")
    fun observeAll(profileId: String): Flow<List<FavoriteEntity>>

    /** Ids favoritados pelo perfil — para marcar o coração nos cards/detalhes. */
    @Query("SELECT mediaId FROM favorites WHERE profileId = :profileId AND deleted = 0")
    fun observeIds(profileId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0)")
    suspend fun isFavorite(profileId: String, mediaId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    /** Remove via tombstone: a linha fica para a remoção chegar aos outros aparelhos. */
    @Query(
        "UPDATE favorites SET deleted = 1, updatedAt = :updatedAt " +
            "WHERE profileId = :profileId AND mediaId = :mediaId AND deleted = 0"
    )
    suspend fun markDeleted(profileId: String, mediaId: String, updatedAt: Long)

    // ---- Sincronização ----

    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun getForSync(profileId: String, mediaId: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE updatedAt > :sinceMs ORDER BY updatedAt ASC")
    suspend fun changedSince(sinceMs: Long): List<FavoriteEntity>

    /** Perfil apagado: tombstone em tudo dele (a remoção também precisa chegar aos outros aparelhos). */
    @Query("UPDATE favorites SET deleted = 1, updatedAt = :updatedAt WHERE profileId = :profileId AND deleted = 0")
    suspend fun markProfileDeleted(profileId: String, updatedAt: Long)

    /** "Apagar meus dados": some tudo deste aparelho, sem tombstone. */
    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}
