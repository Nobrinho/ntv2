package com.ntv2.app.core.catalog

/** Mesmo comportamento do [RoomCatalogStore] (ordem: reset → removidos → upserts → cursor), em memória. */
class InMemoryCatalogStore : CatalogStore {
    val rows = LinkedHashMap<String, CatalogRow>()
    var state: CatalogCursor? = null
    val batches = mutableListOf<CatalogBatch>()

    override suspend fun byNtvId(ntvId: String) = rows[ntvId]

    override suspend fun byMessage(chatId: Long, messageId: Long) =
        rows.values.firstOrNull { it.chatId == chatId && it.videoMessageId == messageId }

    override suspend fun apply(batch: CatalogBatch) {
        batches += batch
        if (batch.reset) {
            rows.clear()
            state = null
        }
        batch.removed.forEach { rows.remove(it) }
        batch.upserts.forEach { rows[it.ntvId] = it }
        batch.cursor?.let { state = it }
    }

    override suspend fun cursor() = state
    override suspend fun count() = rows.size
}
