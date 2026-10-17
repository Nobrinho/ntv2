package com.ntv2.app.core.backend.catalog

import com.ntv2.app.core.backend.SupabaseClient
import org.json.JSONArray

/**
 * Flag `remote_search` de `app_config.flags`, lida direto e guardada em memória por [ttlMs].
 * Só desliga com `false` explícito: sem a chave, ou se a leitura falhar, vale ligada (e uma falha de
 * rede cai no índice local pelo fallback da busca de qualquer forma).
 *
 * TODO: trocar pela leitura do RemoteConfigRepository (sessão de reportes/config) quando ele entrar.
 */
class RemoteSearchFlag(
    private val client: SupabaseClient,
    private val ttlMs: Long = 30 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Volatile private var value: Boolean? = null
    @Volatile private var readAt = 0L

    suspend fun isEnabled(): Boolean {
        if (!client.config.enabled) return false
        value?.let { if (clock() - readAt < ttlMs) return it }
        val fresh = runCatching { parseRemoteSearchFlag(client.get("app_config?select=value&key=eq.flags")) }
            .getOrDefault(true)
        value = fresh
        readAt = clock()
        return fresh
    }
}

/** Resposta do PostgREST: `[{ "value": { "remote_search": true, ... } }]`. */
internal fun parseRemoteSearchFlag(json: String): Boolean {
    val row = JSONArray(json).optJSONObject(0) ?: return true
    return row.optJSONObject("value")?.optBoolean("remote_search", true) ?: true
}
