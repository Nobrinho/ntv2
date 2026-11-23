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
import com.ntv2.app.core.profiles.Profile
import com.ntv2.app.core.profiles.ProfileIds
import com.ntv2.app.core.profiles.ProfileRecord
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
 * Sincroniza os perfis e, por perfil, Minha lista, progresso e histórico com o Supabase (contrato em
 * ntv2-backend/CONTRATO.md).
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
    /** Perfis da conta (ProfileStore); null = não sincroniza perfis. */
    private val profiles: ProfileSyncSource? = null,
    private val syncFlag: SyncFlag = AppConfigSyncFlag(client),
    // Sincronizar entre aparelhos é Premium (a conta inteira; ver PremiumRepository).
    private val premiumCheck: suspend () -> Boolean = { true },
    private val pageSize: Int = PAGE_SIZE,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()

    /** Usuário com que a última passada começou (só lido dentro do [mutex]). */
    private var runUserId: String? = null

    /**
     * [forceFull] ("Sincronizar agora"): zera as marcas antes da rodada, então sobe e baixa tudo de
     * novo. Conserta um aparelho que ficou divergente; o last-write-wins dos dois lados evita regressão.
     */
    suspend fun sync(forceFull: Boolean = false): SyncResult = mutex.withLock {
        runUserId = null
        val first = syncOnce(forceFull)
        // Um 401 no meio (usuário apagado no servidor) recriou a sessão: refaz já com o usuário novo,
        // que sobe tudo de novo, em vez de esperar o próximo disparo.
        val started = runUserId
        if (started != null && session.userId() != started) syncOnce() else first
    }

    private suspend fun syncOnce(forceFull: Boolean = false): SyncResult {
        if (!client.config.enabled) return SyncResult(skipped = SKIP_BACKEND_OFF)
        if (!syncFlag.isEnabled()) return SyncResult(skipped = SKIP_FLAG_OFF)
        if (!premiumCheck()) return SyncResult(skipped = SKIP_NO_PREMIUM)
        val userId = session.userId() ?: return SyncResult(skipped = SKIP_NO_SESSION)
        runUserId = userId
        // Dono das linhas: a conta (aparelhos vinculados compartilham uma). Sem vínculo, é o próprio usuário.
        val owner = currentAccount(userId)
        // Conta nova (vínculo, saída ou sessão anônima nova): sobe tudo e baixa tudo de novo.
        if (forceFull || marks.userId() != owner) marks.reset(owner)

        // Relógio deste aparelho no início da rodada: vira a marca de push (ver [push]).
        val roundStart = now()
        val pushed = linkedMapOf<String, Int>()
        // Perfis antes da personalização: um perfil apagado em outro aparelho some antes de baixar as listas.
        if (profiles != null) {
            pushed[PROFILES] = push(
                PROFILES, profiles.changedSince(marks.pushedUpTo(PROFILES)), roundStart, { it.updatedAt },
                conflict = PROFILE_CONFLICT
            ) { profileToJson(it, owner) }
        }
        pushed[FAVORITES] = push(FAVORITES, favoriteDao.changedSince(marks.pushedUpTo(FAVORITES)), roundStart, { it.updatedAt }) {
            favoriteToJson(it, owner)
        }
        pushed[HISTORY] = push(HISTORY, historyDao.changedSince(marks.pushedUpTo(HISTORY)), roundStart, { it.updatedAt }) {
            historyToJson(it, owner)
        }
        pushed[PROGRESS] = push(PROGRESS, progressDao.changedSince(marks.pushedUpTo(PROGRESS)), roundStart, { it.updatedAt }) {
            progressToJson(it, owner)
        }

        // Histórico antes do progresso: a posição baixada depois atualiza as linhas do histórico.
        val pulled = linkedMapOf<String, Int>()
        if (profiles != null) {
            pulled[PROFILES] = pull(PROFILES, ::applyProfile, keyOf = { it.getString(PROFILE_ID) }) { since ->
                profiles.changedSince(since).map { it.id to it.updatedAt }
            }
        }
        pulled[FAVORITES] = pull(FAVORITES, ::applyFavorite) { since ->
            favoriteDao.changedSince(since).map { key(it.profileId, it.mediaId) to it.updatedAt }
        }
        pulled[HISTORY] = pull(HISTORY, ::applyHistory) { since ->
            historyDao.changedSince(since).map { key(it.profileId, it.mediaId) to it.updatedAt }
        }
        pulled[PROGRESS] = pull(PROGRESS, ::applyProgress) { since ->
            progressDao.changedSince(since).map { key(it.profileId, it.mediaId) to it.updatedAt }
        }
        return SyncResult(pushed = pushed, pulled = pulled)
    }

    /** `current_account` do servidor; backend antigo sem a RPC (404): o próprio usuário. */
    private suspend fun currentAccount(userId: String): String = try {
        client.rpc("current_account", "{}").trim().trim('"').ifBlank { userId }
    } catch (e: com.ntv2.app.core.backend.BackendException) {
        if (e.code == 404) userId else throw e
    }

    /**
     * Sobe o que mudou desde a marca. A marca é o relógio deste aparelho no início da rodada, não o
     * maior `updatedAt` enviado: linhas baixadas de outro aparelho trazem a data DELE, e com o relógio
     * daqui atrasado a marca ficava à frente de toda alteração local seguinte, que nunca subia.
     * Linhas com data à frente do relógio daqui só podem ter vindo do servidor: ficam para depois.
     */
    private suspend fun <T> push(
        table: String,
        rows: List<T>,
        roundStart: Long,
        updatedAt: (T) -> Long,
        conflict: String = ITEM_CONFLICT,
        toJson: (T) -> JSONObject
    ): Int {
        val eligible = rows.filter { updatedAt(it) <= roundStart }
        eligible.chunked(pageSize).forEach { chunk ->
            val body = JSONArray().apply { chunk.forEach { put(toJson(it)) } }.toString()
            client.post("$table?on_conflict=$conflict", body, prefer = "resolution=merge-duplicates,return=minimal")
            marks.setPushedUpTo(table, minOf(chunk.maxOf(updatedAt), roundStart))
        }
        // Inclusive quando a marca antiga estava no futuro (versões anteriores): volta para agora.
        marks.setPushedUpTo(table, roundStart)
        return eligible.size
    }

    /**
     * Baixa em páginas e aplica cada linha. Depois, se as únicas linhas locais acima da marca de push
     * forem exatamente as que vieram do servidor, avança a marca de push — senão elas voltariam ao
     * servidor na próxima rodada (inofensivo, mas desperdício).
     */
    private suspend fun pull(
        table: String,
        apply: suspend (JSONObject, Long) -> Boolean,
        /** Chave da linha para comparar com as alterações locais: "perfil|media" (perfis: só o id). */
        keyOf: (JSONObject) -> String = { key(it.profileOf(), it.getString(MEDIA_ID)) },
        localChangedSince: suspend (Long) -> List<Pair<String, Long>>
    ): Int {
        val applied = hashMapOf<String, Long>()
        val cursorReady = syncedAtSupported != false && try {
            pullBySyncedAt(table, apply, keyOf, applied)
            syncedAtSupported = true
            true
        } catch (e: com.ntv2.app.core.backend.BackendException) {
            // Backend sem a coluna (migration 20261116000100 ainda não aplicada): pull antigo.
            if (e.code != 400 || !e.message.orEmpty().contains(SYNCED_AT)) throw e
            syncedAtSupported = false
            false
        }
        if (!cursorReady) pullByUpdatedAt(table, apply, keyOf, applied)
        if (applied.isNotEmpty()) {
            val pending = localChangedSince(marks.pushedUpTo(table))
            if (pending.isNotEmpty() && pending.all { (id, at) -> applied[id] == at }) {
                marks.setPushedUpTo(table, minOf(pending.maxOf { it.second }, now()))
            }
        }
        return applied.size
    }

    /** null = ainda não sabe se o backend tem `synced_at`; false = backend antigo (pull por updated_at). */
    @Volatile
    private var syncedAtSupported: Boolean? = null

    /**
     * Pull pela hora do servidor: (synced_at, media_id) > cursor. Cada rodada recomeça
     * [PULL_OVERLAP_MS] antes do cursor salvo, para pegar escritas de transações que terminaram fora
     * de ordem; o que já foi aplicado volta e o last-write-wins ignora. A marca é gravada uma vez por
     * página (antes era uma escrita no DataStore por linha).
     */
    private suspend fun pullBySyncedAt(
        table: String,
        apply: suspend (JSONObject, Long) -> Boolean,
        keyOf: (JSONObject) -> String,
        applied: MutableMap<String, Long>
    ) {
        val markKey = table + CURSOR_SUFFIX
        val savedAt = marks.pulledUpTo(markKey)?.substringBefore(CURSOR_SEPARATOR)
        var at = savedAt?.let(SyncTime::toEpochMs)?.let { SyncTime.toIso(it - PULL_OVERLAP_MS) } ?: EPOCH_ISO
        var afterProfile = ""
        var afterId = ""
        val profilesOnly = table == PROFILES
        while (true) {
            val filter = pullFilter(at, afterProfile, afterId, profilesOnly)
            val order = if (profilesOnly) "synced_at.asc,profile_id.asc" else "synced_at.asc,profile_id.asc,media_id.asc"
            val path = "$table?or=${URLEncoder.encode(filter, "UTF-8")}&order=$order&limit=$pageSize"
            val page = JSONArray(client.get(path))
            for (i in 0 until page.length()) {
                val row = page.getJSONObject(i)
                val remoteMs = SyncTime.toEpochMs(row.getString("updated_at"))
                if (remoteMs != null && apply(row, remoteMs)) applied[keyOf(row)] = remoteMs
                at = row.getString(SYNCED_AT)
                afterProfile = row.profileOf()
                afterId = if (profilesOnly) "" else row.getString(MEDIA_ID)
            }
            if (page.length() > 0) {
                marks.setPulledUpTo(markKey, at + CURSOR_SEPARATOR + afterProfile + CURSOR_SEPARATOR + afterId)
            }
            if (page.length() < pageSize) break
        }
    }

    /** Pull antigo, por `updated_at` (backend sem `synced_at`). */
    private suspend fun pullByUpdatedAt(
        table: String,
        apply: suspend (JSONObject, Long) -> Boolean,
        keyOf: (JSONObject) -> String,
        applied: MutableMap<String, Long>
    ) {
        while (true) {
            val since = marks.pulledUpTo(table) ?: EPOCH_ISO
            val path = "$table?updated_at=gt.${URLEncoder.encode(since, "UTF-8")}" +
                "&order=updated_at.asc&limit=$pageSize"
            val page = JSONArray(client.get(path))
            var last: String? = null
            for (i in 0 until page.length()) {
                val row = page.getJSONObject(i)
                val iso = row.getString("updated_at")
                val remoteMs = SyncTime.toEpochMs(iso)
                if (remoteMs != null && apply(row, remoteMs)) applied[keyOf(row)] = remoteMs
                last = iso
            }
            last?.let { marks.setPulledUpTo(table, it) }
            if (page.length() < pageSize) break
        }
    }

    // ---- Perfis ----

    private fun profileToJson(r: ProfileRecord, userId: String): JSONObject {
        val o = JSONObject()
            .put("user_id", userId)
            .put(PROFILE_ID, r.id)
            .put("updated_at", SyncTime.toIso(r.updatedAt))
            .put("deleted", r.deleted)
        val p = r.profile
        if (p == null) {
            // Tombstone: `name` é obrigatório na tabela.
            o.put("name", "Perfil")
        } else {
            o.put("name", p.name.take(40).ifBlank { "Perfil" })
                .put("color", p.color.toLong())
                .put("symbol", p.symbol ?: JSONObject.NULL)
                .put("kids", p.kids)
                .put("max_rating", p.maxRating.toString())
                .put("pin_hash", p.pinHash ?: JSONObject.NULL)
                .put("auto_next", p.autoNext)
                .put("audio", p.audio)
                .put("subtitles", p.subtitles)
                .put("sort", p.sort)
        }
        return o
    }

    private suspend fun applyProfile(row: JSONObject, remoteMs: Long): Boolean {
        val store = profiles ?: return false
        val id = row.getString(PROFILE_ID)
        val profile = if (row.optBoolean("deleted", false)) null else Profile(
            id = id,
            name = row.str("name") ?: "Perfil",
            color = row.optLong("color", 0L).toInt(),
            symbol = row.str("symbol"),
            kids = row.optBoolean("kids", false),
            maxRating = row.str("max_rating")?.toIntOrNull() ?: 12,
            pinHash = row.str("pin_hash"),
            autoNext = row.optBoolean("auto_next", true),
            audio = row.str("audio") ?: "Português",
            subtitles = row.str("subtitles") ?: "Desligadas",
            sort = row.optInt("sort", 0),
            updatedAt = remoteMs
        )
        return store.applyRemote(ProfileRecord(id, remoteMs, profile))
    }

    // ---- Favoritos ----

    private fun favoriteToJson(e: FavoriteEntity, userId: String) = JSONObject()
        .put("user_id", userId)
        .put(PROFILE_ID, e.profileId)
        .put(MEDIA_ID, e.mediaId)
        .put("title", e.title)
        .put("poster_url", remoteUrl(e.posterPath))
        .put("channel_name", e.channelTitle)
        // Conteúdo próprio guarda a chave local (NtvIds.ownKey), que não é id do TMDB: vai null.
        .put("tmdb_id", e.tmdbId?.takeUnless { it.toLongOrNull()?.let(com.ntv2.app.core.catalog.NtvIds::isOwnKey) == true } ?: JSONObject.NULL)
        // `year` não existe no Room: fora do corpo, o upsert preserva o que o servidor tiver.
        .put("updated_at", SyncTime.toIso(e.updatedAt))
        .put("deleted", e.deleted)

    private suspend fun applyFavorite(row: JSONObject, remoteMs: Long): Boolean {
        val id = row.getString(MEDIA_ID)
        val pid = row.profileOf()
        val local = favoriteDao.getForSync(pid, id)
        if (!LastWriteWins.remoteWins(local?.updatedAt, remoteMs)) return false
        favoriteDao.upsert(
            FavoriteEntity(
                profileId = pid,
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
        .put(PROFILE_ID, e.profileId)
        .put(MEDIA_ID, e.mediaId)
        .put("title", e.title)
        .put("poster_url", remoteUrl(e.posterPath))
        .put("channel_name", e.channelTitle)
        .put("last_opened_at", SyncTime.toIso(e.updatedAt))
        .put("updated_at", SyncTime.toIso(e.updatedAt))
        .put("deleted", e.deleted)

    private suspend fun applyHistory(row: JSONObject, remoteMs: Long): Boolean {
        val id = row.getString(MEDIA_ID)
        val pid = row.profileOf()
        val local = historyDao.getForSync(pid, id)
        if (!LastWriteWins.remoteWins(local?.updatedAt, remoteMs)) return false
        // O watch_history remoto não tem posição: vem do progresso local (ou do pull do progresso, a seguir).
        val progress = if (local == null) progressDao.get(pid, id) else null
        historyDao.upsert(
            WatchHistoryEntity(
                profileId = pid,
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
        .put(PROFILE_ID, e.profileId)
        .put(MEDIA_ID, e.mediaId)
        .put("position_ms", e.positionMs)
        .put("duration_ms", e.durationMs)
        .put("updated_at", SyncTime.toIso(e.updatedAt))
        .put("deleted", e.deleted)

    private suspend fun applyProgress(row: JSONObject, remoteMs: Long): Boolean {
        val id = row.getString(MEDIA_ID)
        val pid = row.profileOf()
        val local = progressDao.getForSync(pid, id)
        if (!LastWriteWins.remoteWins(local?.updatedAt, remoteMs)) return false
        val deleted = row.optBoolean("deleted", false)
        val position = row.optLong("position_ms", local?.positionMs ?: 0L)
        val duration = row.optLong("duration_ms", local?.durationMs ?: 0L)
        progressDao.upsert(PlaybackProgressEntity(pid, id, position, duration, remoteMs, deleted))
        if (!deleted) {
            val completed = duration > 0L && position >= duration - PlaybackProgressPolicy.END_GUARD_MS
            historyDao.applyRemotePosition(pid, id, position, duration, completed)
        }
        return true
    }

    private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    /** Linha sem `profile_id` (só um backend anterior aos perfis manda assim): perfil inicial. */
    private fun JSONObject.profileOf(): String = str(PROFILE_ID) ?: ProfileIds.MAIN

    companion object {
        const val FAVORITES = "favorites"
        const val PROGRESS = "playback_progress"
        const val HISTORY = "watch_history"
        const val PROFILES = "account_profiles"
        private const val PROFILE_ID = "profile_id"
        private const val MEDIA_ID = "media_id"
        private const val ITEM_CONFLICT = "user_id,profile_id,media_id"
        private const val PROFILE_CONFLICT = "user_id,profile_id"
        const val PAGE_SIZE = 500
        const val SKIP_BACKEND_OFF = "backend desligado"
        const val SKIP_FLAG_OFF = "flag sync=false"
        const val SKIP_NO_PREMIUM = "sem Premium"
        const val SKIP_NO_SESSION = "sem sessão"
        private const val EPOCH_ISO = "1970-01-01T00:00:00Z"
        private const val SYNCED_AT = "synced_at"
        /**
         * Marca do pull por synced_at numa chave própria: "<synced_at>|<profile_id>|<media_id>".
         * "@synced2": o cursor anterior aos perfis ("@synced") é ignorado e a primeira rodada baixa tudo.
         */
        private const val CURSOR_SUFFIX = "@synced2"
        private const val CURSOR_SEPARATOR = "|"
        /** Folga para transações que gravaram antes mas terminaram depois da última leitura. */
        const val PULL_OVERLAP_MS = 60_000L

        internal fun key(profileId: String, mediaId: String) = "$profileId|$mediaId"

        /**
         * Filtro do pull por (synced_at, profile_id[, media_id]) > cursor, no formato `or=(...)` do PostgREST.
         */
        internal fun pullFilter(at: String, afterProfile: String, afterId: String, profilesOnly: Boolean): String {
            val q = '"'
            val newer = "synced_at.gt.$q$at$q"
            val sameAtNextProfile = "and(synced_at.eq.$q$at$q,profile_id.gt.$q$afterProfile$q)"
            if (profilesOnly) return "($newer,$sameAtNextProfile)"
            val sameProfileNextId =
                "and(synced_at.eq.$q$at$q,profile_id.eq.$q$afterProfile$q,media_id.gt.$q$afterId$q)"
            return "($newer,$sameAtNextProfile,$sameProfileNextId)"
        }

        /** "chatId_messageId" → chatId; cartões de série ("series_<tmdbId>") não têm canal: 0. */
        internal fun channelIdOf(mediaId: String): Long = mediaId.substringBefore('_').toLongOrNull() ?: 0L

        /** Só URLs viajam: caminho de arquivo local (capa baixada pelo TDLib) não serve no outro aparelho. */
        internal fun remoteUrl(path: String?): Any =
            path?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: JSONObject.NULL
    }
}

/** Perfis que a sincronização lê e aplica (ProfileStore). */
interface ProfileSyncSource {
    fun changedSince(sinceMs: Long): List<ProfileRecord>
    fun applyRemote(record: ProfileRecord): Boolean
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
