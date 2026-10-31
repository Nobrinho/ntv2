package com.ntv2.app.core.catalog

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Item do catálogo (filme, série ou episódio), espelho de `catalog_*` do backend. [data] é a linha
 * como veio (`catalog_changes`/`index.json`), em JSON: campo novo no backend não pede migração aqui.
 */
@Entity(
    tableName = "catalog_item",
    indices = [Index(value = ["chat_id", "video_message_id"])]
)
data class CatalogItemEntity(
    @PrimaryKey @ColumnInfo(name = "ntv_id") val ntvId: String,
    val kind: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Long?,
    @ColumnInfo(name = "chat_id") val chatId: Long?,
    /** ID do Bot API (como no índice e no backend), não o do TDLib. Filme em partes: a parte 1. */
    @ColumnInfo(name = "video_message_id") val videoMessageId: Long?,
    @ColumnInfo(name = "changed_at") val changedAt: String?,
    val data: String
)

/** Onde o sync parou (linha única). Ausente = catálogo nunca carregado. */
@Entity(tableName = "catalog_sync_state")
data class CatalogSyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val since: String?,
    @ColumnInfo(name = "after_id") val afterId: String?,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_item WHERE ntv_id = :ntvId")
    suspend fun byNtvId(ntvId: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_item WHERE chat_id = :chatId AND video_message_id = :messageId LIMIT 1")
    suspend fun byMessage(chatId: Long, messageId: Long): CatalogItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogItemEntity>)

    @Query("DELETE FROM catalog_item WHERE ntv_id IN (:ntvIds)")
    suspend fun deleteIds(ntvIds: List<String>)

    @Query("DELETE FROM catalog_item")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM catalog_item")
    suspend fun count(): Int

    @Query("SELECT * FROM catalog_sync_state WHERE id = 0")
    suspend fun state(): CatalogSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: CatalogSyncStateEntity)

    @Query("DELETE FROM catalog_sync_state")
    suspend fun clearState()
}

/**
 * Banco PRÓPRIO do catálogo (`ntv2_catalog.db`), separado do `ntv2.db` (Minha lista, histórico,
 * progresso). É cache que se reconstrói do `index.json` + `catalog_changes`, então:
 * - o `ntv2.db` não ganha migração nem muda de versão por causa do catálogo;
 * - mudança de schema aqui pode recriar o arquivo ([fallbackToDestructiveMigration]); o cursor do sync
 *   mora no mesmo arquivo e some junto, então a próxima rodada recarrega tudo do zero;
 * - rollback (versão antiga do app instalada por cima, ou flag `remote_catalog` desligada): o arquivo
 *   fica sem uso, sem afetar nada; downgrade de versão recria o arquivo.
 */
@Database(
    entities = [CatalogItemEntity::class, CatalogSyncStateEntity::class],
    version = 1,
    exportSchema = true
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val NAME = "ntv2_catalog.db"

        fun build(context: Context, name: String = NAME): CatalogDatabase =
            Room.databaseBuilder(context.applicationContext, CatalogDatabase::class.java, name)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
