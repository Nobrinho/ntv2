package com.ntv2.app.core.backend.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.backendWarn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuração remota (tabela `app_config`, ver ntv2-backend/CONTRATO.md). Os padrões reproduzem o
 * comportamento de antes do backend: sem backend, o app continua exatamente igual.
 */
data class RemoteConfig(
    /** Abaixo disso a atualização vira obrigatória. 0 = sem mínimo. */
    val minVersionCode: Long = 0L,
    /** @ do Telegram que recebe os reportes enviados por DM (fallback). */
    val reportRecipient: String = DEFAULT_REPORT_RECIPIENT,
    /** Liga/desliga recursos (`remote_search`, `sync`, `telemetry`...). Ausente = padrão do chamador. */
    val flags: Map<String, Boolean> = emptyMap(),
    /** Modelo do aparelho → política de decodificador (ex.: `AFTKM` → `software_fallback`). Uso futuro. */
    val decoderOverrides: Map<String, String> = emptyMap()
) {
    fun flag(name: String, default: Boolean = true): Boolean = flags[name] ?: default

    val telemetryEnabled: Boolean get() = flag(FLAG_TELEMETRY)

    companion object {
        const val DEFAULT_REPORT_RECIPIENT = "Eminobre"
        const val FLAG_TELEMETRY = "telemetry"
        const val FLAG_SYNC = "sync"
        const val FLAG_REMOTE_SEARCH = "remote_search"

        /**
         * Lê a resposta de `GET app_config?select=key,value` (`[{key, value}]`). Chave ausente ou com
         * tipo errado fica no padrão, em vez de derrubar a config inteira.
         */
        fun parse(body: String, defaults: RemoteConfig = RemoteConfig()): RemoteConfig {
            val rows = JSONArray(body)
            val values = HashMap<String, Any?>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val key = row.optString("key").takeIf { it.isNotBlank() } ?: continue
                values[key] = row.opt("value")
            }
            return RemoteConfig(
                minVersionCode = (values["min_version_code"] as? Number)?.toLong()
                    ?: (values["min_version_code"] as? String)?.toLongOrNull()
                    ?: defaults.minVersionCode,
                reportRecipient = (values["report_recipient"] as? String)
                    ?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
                    ?: defaults.reportRecipient,
                flags = (values["flags"] as? JSONObject)?.let { obj ->
                    buildMap {
                        obj.keys().forEach { k -> (obj.opt(k) as? Boolean)?.let { put(k, it) } }
                    }
                } ?: defaults.flags,
                decoderOverrides = (values["decoder_overrides"] as? JSONObject)?.let { obj ->
                    buildMap {
                        obj.keys().forEach { k -> (obj.opt(k) as? String)?.let { put(k, it) } }
                    }
                } ?: defaults.decoderOverrides
            )
        }
    }
}

/** Cache da última resposta crua de `app_config` (trocável nos testes). */
interface RemoteConfigCache {
    suspend fun load(): String?
    suspend fun save(body: String)
}

private val Context.remoteConfigDataStore by preferencesDataStore(name = "remote_config")

class DataStoreRemoteConfigCache(private val context: Context) : RemoteConfigCache {
    private val bodyKey = stringPreferencesKey("app_config_body")

    override suspend fun load(): String? = context.remoteConfigDataStore.data.first()[bodyKey]

    override suspend fun save(body: String) {
        context.remoteConfigDataStore.edit { it[bodyKey] = body }
    }
}

class RemoteConfigRepository(
    private val client: SupabaseClient,
    private val cache: RemoteConfigCache,
    private val defaults: RemoteConfig = RemoteConfig()
) {
    private val state = MutableStateFlow(defaults)
    val config: StateFlow<RemoteConfig> = state.asStateFlow()

    private val mutex = Mutex()
    private var cacheLoaded = false

    /** Valor atual sem esperar rede: padrão até o cache ou a rede responderem. */
    val current: RemoteConfig get() = state.value

    /** Garante o cache lido (rápido, sem rede) e devolve a config conhecida. */
    suspend fun get(): RemoteConfig {
        loadCache()
        return state.value
    }

    /** Busca `app_config` no servidor; falha mantém o cache/padrão. No-op com backend desligado. */
    suspend fun refresh(): RemoteConfig {
        loadCache()
        if (!client.config.enabled) return state.value
        runCatching {
            val body = client.get("app_config?select=key,value")
            val parsed = RemoteConfig.parse(body, defaults)
            state.value = parsed
            cache.save(body)
        }.onFailure { backendWarn(TAG, "falha ao ler app_config: ${it.message}") }
        return state.value
    }

    private suspend fun loadCache() = mutex.withLock {
        if (cacheLoaded) return@withLock
        cacheLoaded = true
        if (!client.config.enabled) return@withLock
        runCatching { cache.load()?.let { state.value = RemoteConfig.parse(it, defaults) } }
    }

    private companion object {
        const val TAG = "NtvConfig"
    }
}
