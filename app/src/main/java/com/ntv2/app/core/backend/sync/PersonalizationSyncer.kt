package com.ntv2.app.core.backend.sync

import com.ntv2.app.core.backend.BackendSession
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.database.dao.FavoriteDao
import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.WatchHistoryDao
import com.ntv2.app.core.database.entity.FavoriteEntity
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import com.ntv2.app.core.player.progress.PlaybackProgressPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Resultado de uma rodada (para testes e log). */
data class SyncResult(
    val skipped: String? = null,
    val pushed: Map<String, Int> = emptyMap(),
    val pulled: Map<String, Int> = emptyMap()
)

/**
 * Sincroniza Minha lista, progresso e histórico com o Supabase (contrato em ntv2-backend/CONTRATO.md).
 * O Room continua sendo a fonte local: sem rede ou com o backend desligado, nada muda no app.
 *
 * Rodada: push das linhas com `updatedAt` > marca local (upsert com merge-duplicates; o trigger
 * `keep_newest` do servidor descarta o que for mais velho), depois pull de `updated_at > marca
 * remota`, aplicado com last-write-wins. Remoções viajam como tombstone (`deleted`).
 */
class PersonalizationSyncer(
    private val client: SupabaseClient,
    private val session: BackendSession,
    private val favoriteDao: FavoriteDao,
    private val progressDao: PlaybackProgressDao,
    private val historyDao: WatchHistoryDao,
    private val marks: SyncMarks,
    private val syncFlag: SyncFlag = AppConfigSyncFlag(client),
    private val pageSize: Int = PAGE_SIZE
) {
    private val mutex = Mutex()

    /** Usuário com que a última passada começou (só lido dentro do [mutex]). */
    private var runUserId: String? = null

    suspend fun sync(): SyncResult = mutex.withLock {
        runUserId = null
        val first = syncOnce()
        // Um 401 no meio (usuário apagado no servidor) recriou a sessão: refaz já com o usuário novo,
        // que sobe tudo de novo, em vez de esperar o próximo disparo.
        val started = runUserId
        if (started != null && session.userId() != started) syncOnce() else first
    }

    private suspend fun syncOnce(): SyncResult {
        if (!client.config.enabled) return SyncResult(skipped = "backend desligado")
        if (!syncFlag.isEnabled()) return SyncResult(skipped = "flag sync=false")
        val userId = session.userId() ?: return SyncResult(skipped = "sem sessão")
        runUserId = userId
        // Sessão anônima nova (ex.: banco do servidor resetado): sobe tudo e baixa tudo de novo.
        if (marks.userId() != userId) marks.reset(userId)

        val pushed = linkedMapOf<String, Int>()
        pushed[FAVORITES] = push(FAVORITES, favoriteDao.changedSince(marks.pushedUpTo(FAVORITES)), { it.updatedAt }) {
            favoriteToJson(it, userId)
        }
        pushed[HISTORY] = push(HISTORY, historyDao.changedSince(marks.pushedUpTo(HISTORY)), { it.updatedAt }) {
            historyToJson(it, userId)
        }
        pushed[PROGRESS] = push(PROGRESS, progressDao.changedSince(marks.pushedUpTo(PROGRESS)), { it.updatedAt }) {
            progressToJson(it, userId)
        }

        // Histórico antes do progresso: a posição baixada depois atualiza as linhas do histórico.
        val pulled = linkedMapOf<String, Int>()
        pulled[FAVORITES] = pull(FAVORITES, ::applyFavorite) { since ->
            favoriteDao.changedSince(since).map { it.mediaId to it.updatedAt }
        }
        pulled[HISTORY] = pull(HISTORY, ::applyHistory) { since ->
            historyDao.changedSince(since).map { it.mediaId to it.updatedAt }
        }
        pulled[PROGRESS] = pull(PROGRESS, ::applyProgress) { since ->
            progressDao.changedSince(since).map { it.mediaId to it.updatedAt }
        }
        return SyncResult(pushed = pushed, pulled = pulled)
    }

    private suspend fun <T> push(table: String, rows: List<T>, updatedAt: (T) -> Long, toJson: (T) -> JSONObject): Int {
        if (rows.isEmpty()) return 0
        rows.chunked(pageSize).forEach { chunk ->
            val body = JSONArray().apply { chunk.forEach { put(toJson(it)) } }.toString()
            client.post("$table?on_conflict=user_id,media_id", body, prefer = "resolution=merge-duplicates,return=minimal")
            marks.setPushedUpTo(table, chunk.maxOf(updatedAt))
        }
        return rows.size
    }

    /**
     * Baixa em páginas e aplica cada linha. Depois, se as únicas linhas locais acima da marca de push
     * forem exatamente as que vieram do servidor, avança a marca de push — senão elas voltariam ao
     * servidor na próxima rodada (inofensivo, mas desperdício).
     */
    private suspend fun pull(
        table: String,
        apply: suspend (JSONObject, Long) -> Boolean,
        localChangedSince: suspend (Long) -> List<Pair<String, Long>>
    ): Int {
        val applied = hashMapOf<String, Long>()
        while (true) {
            val since = marks.pulledUpTo(table) ?: EPOCH_ISO
            val path = "$table?updated_at=gt.${URLEncoder.encode(since, "UTF-8")}" +
                "&order=updated_at.asc&limit=$pageSize"
            val page = JSONArray(client.get(path))
            for (i in 0 until page.length()) {
                val row = page.getJSONObject(i)
                val iso = row.getString("updated_at")
                val remoteMs = SyncTime.toEpochMs(iso)
                if (remoteMs != null && apply(row, remoteMs)) applied[row.getString("media_id")] = remoteMs
                marks.setPulledUpTo(table, iso)
            }
            if (page.length() < pageSize) break
        }
        if (applied.isNotEmpty()) {
            val pending = localChangedSince(marks.pushedUpTo(table))
            if (pending.isNotEmpty() && pending.all { (id, at) -> applied[id] == at }) {
                marks.setPushedUpTo(table, pending.maxOf { it.second })
            }
        }
        return applied.size
    }

    // ---- Favoritos ----

    private fun favoriteToJson(e: FavoriteEntity, userId: String) = JSONObject()
        .put("user_id", userId)
        .put("media_id", e.mediaId)
        .put("title", e.title)
        .put("poster_url", remoteUrl(e.posterPath))
        .put("channel_name", e.channelTitle)
        .put("tmdb_id", e.tmdbId ?: JSONObject.NULL)
        // `year` não existe no Room: fora do corpo, o upsert preserva o que o servidor tiver.
        .put("updated_at", SyncTime.toIso(e.updatedAt))
        .put("deleted", e.deleted)

    private suspend fun applyFavorite(row: JSONObject, remoteMs: Long): Boolean {
        val id = row.getString("media_id")
        val local = favoriteDao.getForSync(id)
        if (!LastWriteWins.remoteWins(local?.updatedAt, remoteMs)) return false
        favoriteDao.upsert(
            FavoriteEntity(
                mediaId = id,
                channelId = local?.channelId ?: channelIdOf(id),
                channelTitle = row.str("channel_name") ?: local?.channelTitle.orEmpty(),
                title = row.str("title") ?: local?.title.orEmpty(),
                posterPath = row.str("poster_url") ?: local?.posterPath,
                thumbnailPath = local?.thumbnailPath,
                durationSeconds = local?.durationSeconds ?: 0,
                tmdbId = row.str("tmdb_id") ?: local?.tmdbId,
                genres = local?.genres,
                addedAt = local?.addedAt ?: remoteMs,
                updatedAt = remoteMs,
                deleted = row.optBoolean("deleted", false)
            )
        )
        return true
    }

    // ---- Histórico ----

    private fun historyToJson(e: WatchHistoryEntity, userId: String) = JSONObject()
        .put("user_id", userId)
        .put("media_id", e.mediaId)
        .put("title", e.title)
        .put("poster_url", remoteUrl(e.posterPath))
        .put("channel_name", e.channelTitle)
        .put("last_opened_at", SyncTime.toIso(e.updatedAt))
        .put("updated_at", SyncTime.toIso(e.updatedAt))
        .put("deleted", e.deleted)

    private suspend fun applyHistory(row: JSONObject, remoteMs: Long): Boolean {
        val id = row.getString("media_id")
        val local = historyDao.getForSync(id)
        if (!LastWriteWins.remoteWins(local?.updatedAt, remoteMs)) return false
        // O watch_history remoto não tem posição: vem do progresso local (ou do pull do progresso, a seguir).
        val progress = if (local == null) progressDao.get(id) else null
        historyDao.upsert(
            WatchHistoryEntity(
                mediaId = id,
                channelId = local?.channelId ?: channelIdOf(id),
                channelTitle = row.str("channel_name") ?: local?.channelTitle.orEmpty(),
                title = row.str("title") ?: local?.title.orEmpty(),
                posterPath = row.str("poster_url") ?: local?.posterPath,
                thumbnailPath = local?.thumbnailPath,
                durationMs = local?.durationMs ?: progress?.durationMs ?: 0L,
                lastPositionMs = local?.lastPositionMs ?: progress?.positionMs ?: 0L,
                completed = local?.completed ?: false,
                genres = local?.genres,
                updatedAt = remoteMs,
                deleted = row.optBoolean("deleted", false)
            )
        )
        return true
    }

    // ---- Progresso ----

    private fun progressToJson(e: PlaybackProgressEntity, userId: String) = JSONObject()
        .put("user_id", userId)
        .put("media_id", e.mediaId)
        .put("position_ms", e.positionMs)
        .put("duration_ms", e.durationMs)
        .put("updated_at", SyncTime.toIso(e.updatedAt))
        .put("deleted", e.deleted)

    private suspend fun applyProgress(row: JSONObject, remoteMs: Long): Boolean {
        val id = row.getString("media_id")
        val local = progressDao.getForSync(id)
        if (!LastWriteWins.remoteWins(local?.updatedAt, remoteMs)) return false
        val deleted = row.optBoolean("deleted", false)
        val position = row.optLong("position_ms", local?.positionMs ?: 0L)
        val duration = row.optLong("duration_ms", local?.durationMs ?: 0L)
        progressDao.upsert(PlaybackProgressEntity(id, position, duration, remoteMs, deleted))
        if (!deleted) {
            val completed = duration > 0L && position >= duration - PlaybackProgressPolicy.END_GUARD_MS
            historyDao.applyRemotePosition(id, position, duration, completed)
        }
        return true
    }

    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    companion object {
        const val FAVORITES = "favorites"
        const val PROGRESS = "playback_progress"
        const val HISTORY = "watch_history"
        const val PAGE_SIZE = 500
        private const val EPOCH_ISO = "1970-01-01T00:00:00Z"

        /** "chatId_messageId" → chatId; cartões de série ("series_<tmdbId>") não têm canal: 0. */
        internal fun channelIdOf(mediaId: String): Long = mediaId.substringBefore('_').toLongOrNull() ?: 0L

        /** Só URLs viajam: caminho de arquivo local (capa baixada pelo TDLib) não serve no outro aparelho. */
        internal fun remoteUrl(path: String?): Any =
            path?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: JSONObject.NULL
    }
}

/** Se a sincronização está ligada no servidor (app_config.flags.sync). */
fun interface SyncFlag {
    suspend fun isEnabled(): Boolean
}

/**
 * Lê `flags.sync` direto do app_config, com cache curto.
 * TODO: trocar pelo RemoteConfigRepository (core/backend/config) quando ele entrar no main.
 */
class AppConfigSyncFlag(
    private val client: SupabaseClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 15 * 60_000L
) : SyncFlag {
    private var cached: Boolean? = null
    private var fetchedAt = 0L

    override suspend fun isEnabled(): Boolean {
        cached?.let { if (now() - fetchedAt < ttlMs) return it }
        val rows = JSONArray(client.get("app_config?select=key,value&key=eq.flags", authenticated = false))
        // Sem a chave ou sem o campo: ligado (é o valor inicial do contrato).
        val flags = if (rows.length() > 0) rows.getJSONObject(0).optJSONObject("value") else null
        val enabled = flags?.optBoolean("sync", true) ?: true
        cached = enabled
        fetchedAt = now()
        return enabled
    }
}
