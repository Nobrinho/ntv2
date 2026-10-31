package com.ntv2.app.core.catalog

import androidx.room.withTransaction

/** Uma linha do catálogo local. [data]: JSON no formato de `catalog_*` (snake_case). */
data class CatalogRow(
    val ntvId: String,
    val kind: String,
    val tmdbId: Long?,
    val chatId: Long?,
    val videoMessageId: Long?,
    val changedAt: String?,
    val data: String
)

/** Cursor de `catalog_changes` (`next` da última página aplicada) + quando a rodada terminou. */
data class CatalogCursor(val since: String?, val afterId: String?, val lastSyncAt: Long = 0L)

/**
 * Uma escrita atômica no catálogo: [reset] apaga tudo antes (itens e cursor), depois remove
 * [removed], grava [upserts] e, por último, o [cursor] (null = não mexe no cursor).
 */
data class CatalogBatch(
    val upserts: List<CatalogRow> = emptyList(),
    val removed: List<String> = emptyList(),
    val reset: Boolean = false,
    val cursor: CatalogCursor? = null
)

interface CatalogStore {
    suspend fun byNtvId(ntvId: String): CatalogRow?
    /** [messageId] no formato do Bot API (o mesmo do índice). */
    suspend fun byMessage(chatId: Long, messageId: Long): CatalogRow?
    suspend fun apply(batch: CatalogBatch)
    /** Null = catálogo nunca carregado (falta a primeira carga). */
    suspend fun cursor(): CatalogCursor?
    suspend fun count(): Int
    /** Até [limit] linhas com `ntv_id` > [afterNtvId], em ordem de `ntv_id` (para ler tudo aos poucos). */
    suspend fun page(afterNtvId: String, limit: Int): List<CatalogRow>
}

class RoomCatalogStore(private val db: CatalogDatabase) : CatalogStore {
    private val dao = db.catalogDao()

    override suspend fun byNtvId(ntvId: String): CatalogRow? = dao.byNtvId(ntvId)?.toRow()

    override suspend fun byMessage(chatId: Long, messageId: Long): CatalogRow? =
        dao.byMessage(chatId, messageId)?.toRow()

    override suspend fun apply(batch: CatalogBatch) {
        db.withTransaction {
            if (batch.reset) {
                dao.clear()
                dao.clearState()
            }
            // SQLite limita as variáveis por comando (999 nas versões antigas do Android).
            batch.removed.chunked(SQL_CHUNK).forEach { dao.deleteIds(it) }
            batch.upserts.chunked(SQL_CHUNK).forEach { chunk -> dao.upsertAll(chunk.map { it.toEntity() }) }
            batch.cursor?.let { dao.saveState(CatalogSyncStateEntity(0, it.since, it.afterId, it.lastSyncAt)) }
        }
    }

    override suspend fun cursor(): CatalogCursor? =
        dao.state()?.let { CatalogCursor(it.since, it.afterId, it.lastSyncAt) }

    override suspend fun count(): Int = dao.count()

    override suspend fun page(afterNtvId: String, limit: Int): List<CatalogRow> =
        dao.page(afterNtvId, limit).map { it.toRow() }

    private fun CatalogItemEntity.toRow() = CatalogRow(ntvId, kind, tmdbId, chatId, videoMessageId, changedAt, data)
    private fun CatalogRow.toEntity() = CatalogItemEntity(ntvId, kind, tmdbId, chatId, videoMessageId, changedAt, data)

    private companion object {
        const val SQL_CHUNK = 400
    }
}
