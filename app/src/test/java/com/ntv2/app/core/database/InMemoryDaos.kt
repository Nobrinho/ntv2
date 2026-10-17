package com.ntv2.app.core.database

import com.ntv2.app.core.database.dao.FavoriteDao
import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.WatchHistoryDao
import com.ntv2.app.core.database.entity.FavoriteEntity
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** DAOs em memória com a mesma semântica do SQL (tombstones fora das consultas da UI). */
class InMemoryFavoriteDao : FavoriteDao {
    val rows = linkedMapOf<String, FavoriteEntity>()
    private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    private fun publish() { flow.value = rows.values.toList() }
    override fun observeAll(): Flow<List<FavoriteEntity>> =
        flow.map { list -> list.filter { !it.deleted }.sortedByDescending { it.addedAt } }
    override fun observeIds(): Flow<List<String>> = flow.map { list -> list.filter { !it.deleted }.map { it.mediaId } }
    override suspend fun isFavorite(mediaId: String): Boolean = rows[mediaId]?.deleted == false
    override suspend fun upsert(entity: FavoriteEntity) { rows[entity.mediaId] = entity; publish() }
    override suspend fun markDeleted(mediaId: String, updatedAt: Long) {
        val e = rows[mediaId]?.takeIf { !it.deleted } ?: return
        rows[mediaId] = e.copy(deleted = true, updatedAt = updatedAt); publish()
    }
    override suspend fun getForSync(mediaId: String): FavoriteEntity? = rows[mediaId]
    override suspend fun changedSince(sinceMs: Long): List<FavoriteEntity> =
        rows.values.filter { it.updatedAt > sinceMs }.sortedBy { it.updatedAt }
}

class InMemoryProgressDao : PlaybackProgressDao {
    val rows = linkedMapOf<String, PlaybackProgressEntity>()
    override suspend fun get(mediaId: String): PlaybackProgressEntity? = rows[mediaId]?.takeIf { !it.deleted }
    override suspend fun getAll(): List<PlaybackProgressEntity> = rows.values.filter { !it.deleted }
    override suspend fun upsert(entity: PlaybackProgressEntity) { rows[entity.mediaId] = entity }
    override suspend fun markDeleted(mediaId: String, updatedAt: Long) {
        val e = rows[mediaId]?.takeIf { !it.deleted } ?: return
        rows[mediaId] = e.copy(deleted = true, updatedAt = updatedAt)
    }
    override suspend fun getForSync(mediaId: String): PlaybackProgressEntity? = rows[mediaId]
    override suspend fun changedSince(sinceMs: Long): List<PlaybackProgressEntity> =
        rows.values.filter { it.updatedAt > sinceMs }.sortedBy { it.updatedAt }
}

class InMemoryHistoryDao : WatchHistoryDao {
    val rows = linkedMapOf<String, WatchHistoryEntity>()
    private val flow = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    private fun publish() { flow.value = rows.values.toList() }
    private fun visible() = rows.values.filter { !it.deleted }.sortedByDescending { it.updatedAt }
    override fun observeAll(): Flow<List<WatchHistoryEntity>> =
        flow.map { list -> list.filter { !it.deleted }.sortedByDescending { it.updatedAt } }
    override suspend fun getAll(): List<WatchHistoryEntity> = visible()
    override suspend fun get(mediaId: String): WatchHistoryEntity? = rows[mediaId]?.takeIf { !it.deleted }
    override suspend fun upsert(entity: WatchHistoryEntity) { rows[entity.mediaId] = entity; publish() }
    override suspend fun updateProgress(mediaId: String, positionMs: Long, durationMs: Long, completed: Boolean, updatedAt: Long) {
        val e = get(mediaId) ?: return
        rows[mediaId] = e.copy(lastPositionMs = positionMs, durationMs = durationMs, completed = completed, updatedAt = updatedAt)
        publish()
    }
    override suspend fun markDeleted(mediaId: String, updatedAt: Long) {
        val e = get(mediaId) ?: return
        rows[mediaId] = e.copy(deleted = true, updatedAt = updatedAt); publish()
    }
    override suspend fun markAllDeleted(updatedAt: Long) {
        visible().forEach { rows[it.mediaId] = it.copy(deleted = true, updatedAt = updatedAt) }
        publish()
    }
    override suspend fun getForSync(mediaId: String): WatchHistoryEntity? = rows[mediaId]
    override suspend fun changedSince(sinceMs: Long): List<WatchHistoryEntity> =
        rows.values.filter { it.updatedAt > sinceMs }.sortedBy { it.updatedAt }
    override suspend fun applyRemotePosition(mediaId: String, positionMs: Long, durationMs: Long, completed: Boolean) {
        val e = get(mediaId) ?: return
        rows[mediaId] = e.copy(lastPositionMs = positionMs, durationMs = durationMs, completed = completed)
        publish()
    }
}
