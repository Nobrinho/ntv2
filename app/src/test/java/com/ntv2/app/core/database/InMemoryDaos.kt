package com.ntv2.app.core.database

import com.ntv2.app.core.database.dao.FavoriteDao
import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.WatchHistoryDao
import com.ntv2.app.core.database.entity.FavoriteEntity
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import com.ntv2.app.core.profiles.ProfileIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Linhas por perfil. [rows] é o perfil inicial ("p_main"), indexado por mediaId, como nos testes
 * anteriores aos perfis; os outros perfis ficam em [others].
 */
abstract class ProfiledRows<T> {
    val rows = linkedMapOf<String, T>()
    val others = linkedMapOf<String, LinkedHashMap<String, T>>()
    fun table(profileId: String): LinkedHashMap<String, T> =
        if (profileId == ProfileIds.MAIN) rows else others.getOrPut(profileId) { linkedMapOf() }
    fun all(): List<T> = rows.values + others.values.flatMap { it.values }
}

/** DAOs em memória com a mesma semântica do SQL (tombstones fora das consultas da UI). */
class InMemoryFavoriteDao : ProfiledRows<FavoriteEntity>(), FavoriteDao {
    private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    private fun publish() { flow.value = all() }
    override fun observeAll(profileId: String): Flow<List<FavoriteEntity>> =
        flow.map { list -> list.filter { it.profileId == profileId && !it.deleted }.sortedByDescending { it.addedAt } }
    override fun observeIds(profileId: String): Flow<List<String>> =
        flow.map { list -> list.filter { it.profileId == profileId && !it.deleted }.map { it.mediaId } }
    override suspend fun isFavorite(profileId: String, mediaId: String): Boolean =
        table(profileId)[mediaId]?.deleted == false
    override suspend fun upsert(entity: FavoriteEntity) { table(entity.profileId)[entity.mediaId] = entity; publish() }
    override suspend fun markDeleted(profileId: String, mediaId: String, updatedAt: Long) {
        val t = table(profileId)
        val e = t[mediaId]?.takeIf { !it.deleted } ?: return
        t[mediaId] = e.copy(deleted = true, updatedAt = updatedAt); publish()
    }
    override suspend fun getForSync(profileId: String, mediaId: String): FavoriteEntity? = table(profileId)[mediaId]
    override suspend fun changedSince(sinceMs: Long): List<FavoriteEntity> =
        all().filter { it.updatedAt > sinceMs }.sortedBy { it.updatedAt }
    override suspend fun markProfileDeleted(profileId: String, updatedAt: Long) {
        val t = table(profileId)
        t.values.filter { !it.deleted }.forEach { t[it.mediaId] = it.copy(deleted = true, updatedAt = updatedAt) }
        publish()
    }
    override suspend fun deleteAll() { rows.clear(); others.clear(); publish() }
}

class InMemoryProgressDao : ProfiledRows<PlaybackProgressEntity>(), PlaybackProgressDao {
    override suspend fun get(profileId: String, mediaId: String): PlaybackProgressEntity? =
        table(profileId)[mediaId]?.takeIf { !it.deleted }
    override suspend fun getAll(profileId: String): List<PlaybackProgressEntity> =
        table(profileId).values.filter { !it.deleted }
    override suspend fun upsert(entity: PlaybackProgressEntity) { table(entity.profileId)[entity.mediaId] = entity }
    override suspend fun markDeleted(profileId: String, mediaId: String, updatedAt: Long) {
        val t = table(profileId)
        val e = t[mediaId]?.takeIf { !it.deleted } ?: return
        t[mediaId] = e.copy(deleted = true, updatedAt = updatedAt)
    }
    override suspend fun getForSync(profileId: String, mediaId: String): PlaybackProgressEntity? = table(profileId)[mediaId]
    override suspend fun changedSince(sinceMs: Long): List<PlaybackProgressEntity> =
        all().filter { it.updatedAt > sinceMs }.sortedBy { it.updatedAt }
    override suspend fun markProfileDeleted(profileId: String, updatedAt: Long) {
        val t = table(profileId)
        t.values.filter { !it.deleted }.forEach { t[it.mediaId] = it.copy(deleted = true, updatedAt = updatedAt) }
    }
    override suspend fun deleteAll() { rows.clear(); others.clear() }
}

class InMemoryHistoryDao : ProfiledRows<WatchHistoryEntity>(), WatchHistoryDao {
    private val flow = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    private fun publish() { flow.value = all() }
    private fun visible(profileId: String) =
        table(profileId).values.filter { !it.deleted }.sortedByDescending { it.updatedAt }
    override fun observeAll(profileId: String): Flow<List<WatchHistoryEntity>> =
        flow.map { list -> list.filter { it.profileId == profileId && !it.deleted }.sortedByDescending { it.updatedAt } }
    override suspend fun getAll(profileId: String): List<WatchHistoryEntity> = visible(profileId)
    override suspend fun get(profileId: String, mediaId: String): WatchHistoryEntity? =
        table(profileId)[mediaId]?.takeIf { !it.deleted }
    override suspend fun upsert(entity: WatchHistoryEntity) { table(entity.profileId)[entity.mediaId] = entity; publish() }
    override suspend fun updateProgress(
        profileId: String, mediaId: String, positionMs: Long, durationMs: Long, completed: Boolean, updatedAt: Long
    ) {
        val e = get(profileId, mediaId) ?: return
        table(profileId)[mediaId] =
            e.copy(lastPositionMs = positionMs, durationMs = durationMs, completed = completed, updatedAt = updatedAt)
        publish()
    }
    override suspend fun markDeleted(profileId: String, mediaId: String, updatedAt: Long) {
        val e = get(profileId, mediaId) ?: return
        table(profileId)[mediaId] = e.copy(deleted = true, updatedAt = updatedAt); publish()
    }
    override suspend fun markAllDeleted(profileId: String, updatedAt: Long) {
        visible(profileId).forEach { table(profileId)[it.mediaId] = it.copy(deleted = true, updatedAt = updatedAt) }
        publish()
    }
    override suspend fun getForSync(profileId: String, mediaId: String): WatchHistoryEntity? = table(profileId)[mediaId]
    override suspend fun changedSince(sinceMs: Long): List<WatchHistoryEntity> =
        all().filter { it.updatedAt > sinceMs }.sortedBy { it.updatedAt }
    override suspend fun applyRemotePosition(
        profileId: String, mediaId: String, positionMs: Long, durationMs: Long, completed: Boolean
    ) {
        val e = get(profileId, mediaId) ?: return
        table(profileId)[mediaId] = e.copy(lastPositionMs = positionMs, durationMs = durationMs, completed = completed)
        publish()
    }
    override suspend fun deleteAll() { rows.clear(); others.clear(); publish() }
}
