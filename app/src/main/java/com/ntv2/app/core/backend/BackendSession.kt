package com.ntv2.app.core.backend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class BackendTokens(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long
)

interface BackendTokenStore {
    suspend fun load(): BackendTokens?
    suspend fun save(tokens: BackendTokens)
    suspend fun clear()
}

private val Context.backendDataStore by preferencesDataStore(name = "backend_session")

class DataStoreBackendTokenStore(private val context: Context) : BackendTokenStore {
    private val userIdKey = stringPreferencesKey("user_id")
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val expiresKey = longPreferencesKey("expires_at_ms")

    override suspend fun load(): BackendTokens? {
        val prefs = context.backendDataStore.data.first()
        return BackendTokens(
            userId = prefs[userIdKey] ?: return null,
            accessToken = prefs[accessKey] ?: return null,
            refreshToken = prefs[refreshKey] ?: return null,
            expiresAtMs = prefs[expiresKey] ?: 0L
        )
    }

    override suspend fun save(tokens: BackendTokens) {
        context.backendDataStore.edit {
            it[userIdKey] = tokens.userId
            it[accessKey] = tokens.accessToken
            it[refreshKey] = tokens.refreshToken
            it[expiresKey] = tokens.expiresAtMs
        }
    }

    override suspend fun clear() {
        context.backendDataStore.edit { it.clear() }
    }
}

/**
 * Sessão anônima do aparelho no Supabase Auth: cria na primeira chamada e renova antes de expirar.
 * Vincular a uma conta (e-mail/Google) fica para depois, preservando o mesmo user_id.
 */
class BackendSession(
    private val client: SupabaseClient,
    private val store: BackendTokenStore,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()

    init {
        client.tokenProvider = { accessToken() }
        client.onUnauthorized = { invalidate() }
    }

    /** Esquece a sessão atual; a próxima chamada cria uma sessão anônima nova. */
    suspend fun invalidate() = mutex.withLock { store.clear() }

    val enabled: Boolean get() = client.config.enabled

    suspend fun userId(): String? = if (enabled) current().userId else null

    suspend fun accessToken(): String? = if (enabled) current().accessToken else null

    private suspend fun current(): BackendTokens = mutex.withLock {
        val saved = store.load()
        if (saved != null && saved.expiresAtMs - now() >= REFRESH_MARGIN_MS) return@withLock saved
        val tokens = if (saved == null) signUp() else refresh(saved)
        store.save(tokens)
        tokens
    }

    private suspend fun signUp(): BackendTokens = parse(client.auth("signup", "{}"))

    private suspend fun refresh(saved: BackendTokens): BackendTokens = try {
        val body = JSONObject().put("refresh_token", saved.refreshToken).toString()
        parse(client.auth("token?grant_type=refresh_token", body))
    } catch (e: BackendException) {
        // Refresh recusado (ex.: banco resetado): começa uma sessão anônima nova.
        if (e.code in 400..499) signUp() else throw e
    }

    internal fun parse(body: String): BackendTokens {
        val json = JSONObject(body)
        return BackendTokens(
            userId = json.getJSONObject("user").getString("id"),
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtMs = now() + json.getLong("expires_in") * 1000
        )
    }

    private companion object {
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
