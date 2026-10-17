package com.ntv2.app.core.backend.telemetry

import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.backendWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Ponto de registro de eventos usado pelas telas e pelo player (no-op nos testes). */
fun interface Telemetry {
    fun log(name: String, props: Map<String, Any?>)

    companion object {
        val NoOp = Telemetry { _, _ -> }
    }
}

/** Nomes fixos do contrato (ntv2-backend/CONTRATO.md). */
object TelemetryEvents {
    const val APP_OPEN = "app_open"
    const val MEDIA_OPEN = "media_open"
    const val PLAYBACK_ERROR = "playback_error"
    const val DECODER_FALLBACK = "decoder_fallback"
    const val SEARCH_EMPTY = "search_empty"
    const val REPORT_SENT = "report_sent"
}

/**
 * Guarda os eventos em memória e envia em lotes pela RPC `log_events` (até [batchSize] por chamada),
 * a cada [flushIntervalMs] e quando o app vai para segundo plano. Sem backend, ou com a flag
 * `telemetry` desligada, descarta tudo na hora. Falha de rede devolve o lote ao buffer (com teto).
 */
class TelemetryLogger(
    private val client: SupabaseClient,
    /** Lido a cada evento: a flag remota pode mudar depois do início. */
    private val telemetryEnabled: () -> Boolean,
    private val appVersion: String,
    private val deviceModel: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val batchSize: Int = MAX_BATCH,
    private val maxBuffered: Int = MAX_BUFFERED,
    private val flushIntervalMs: Long = FLUSH_INTERVAL_MS
) : Telemetry {
    private val lock = Any()
    private val buffer = ArrayDeque<JSONObject>()
    private val flushMutex = Mutex()
    private var timerJob: Job? = null
    private var scope: CoroutineScope? = null

    private val enabled: Boolean
        get() = client.config.enabled && runCatching(telemetryEnabled).getOrDefault(false)

    val pendingCount: Int get() = synchronized(lock) { buffer.size }

    override fun log(name: String, props: Map<String, Any?>) {
        if (!enabled) return
        val event = JSONObject()
            .put("name", name)
            .put("props", JSONObject().apply { props.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } })
            .put("app_version", appVersion)
            .put("device_model", deviceModel)
            .put("at", java.time.Instant.ofEpochMilli(now()).toString())
        val full = synchronized(lock) {
            buffer.addLast(event)
            while (buffer.size > maxBuffered) buffer.removeFirst()
            buffer.size >= batchSize
        }
        // Lote cheio: não espera o timer.
        if (full) scope?.launch { flush() }
    }

    /** Liga o envio periódico no [scope] (chamar uma vez, na abertura do app). */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    /** App foi para segundo plano: envia o que houver sem esperar o timer. */
    fun onBackground() {
        scope?.launch { flush() }
    }

    /** Envia todo o buffer em lotes. Retorna quantos eventos o servidor confirmou. */
    suspend fun flush(): Int = flushMutex.withLock {
        var sent = 0
        while (true) {
            if (!enabled) {
                synchronized(lock) { buffer.clear() }
                break
            }
            val batch = synchronized(lock) {
                val n = minOf(batchSize, buffer.size)
                List(n) { buffer.removeFirst() }
            }
            if (batch.isEmpty()) break
            val ok = runCatching {
                val body = JSONObject().put("events", JSONArray(batch)).toString()
                client.rpc("log_events", body)
            }.onFailure { backendWarn(TAG, "falha ao enviar ${batch.size} eventos: ${it.message}") }
                .isSuccess
            if (!ok) {
                // Devolve na frente (ordem preservada); o próximo ciclo tenta de novo.
                synchronized(lock) {
                    batch.asReversed().forEach { buffer.addFirst(it) }
                    while (buffer.size > maxBuffered) buffer.removeFirst()
                }
                break
            }
            sent += batch.size
        }
        sent
    }

    companion object {
        const val MAX_BATCH = 100
        const val MAX_BUFFERED = 1_000
        const val FLUSH_INTERVAL_MS = 30_000L
        private const val TAG = "NtvTelemetry"
    }
}
