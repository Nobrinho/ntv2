package com.ntv2.app.core.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** URL + anon key do projeto Supabase. Vazios = backend desligado (o app funciona como antes). */
data class BackendConfig(val url: String, val anonKey: String) {
    val enabled: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}

data class BackendRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?
)

data class BackendResponse(val code: Int, val body: String)

class BackendException(val code: Int, message: String) : Exception(message)

/** Transporte HTTP trocável nos testes. */
fun interface HttpTransport {
    fun execute(request: BackendRequest): BackendResponse
}

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000
) : HttpTransport {
    override fun execute(request: BackendRequest): BackendResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = request.method
            request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (request.body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return BackendResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Cliente mínimo do Supabase (PostgREST + RPC + Auth) sem SDK: HttpURLConnection + org.json.
 * Contrato das rotas: ntv2-backend/CONTRATO.md.
 */
class SupabaseClient(
    val config: BackendConfig,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val maxAttempts: Int = 2
) {
    /** Fornece o access token do usuário; null = usa a anon key. Ligado pelo [BackendSession]. */
    var tokenProvider: suspend () -> String? = { null }

    suspend fun get(path: String): String = send("GET", "/rest/v1/$path", null)

    suspend fun post(path: String, body: String, prefer: String? = null): String =
        send("POST", "/rest/v1/$path", body, prefer)

    suspend fun rpc(function: String, body: String): String =
        send("POST", "/rest/v1/rpc/$function", body)

    /** Rotas de auth: sempre com a anon key (sem token do usuário). */
    suspend fun auth(path: String, body: String): String =
        send("POST", "/auth/v1/$path", body, authenticated = false)

    private suspend fun send(
        method: String,
        path: String,
        body: String?,
        prefer: String? = null,
        authenticated: Boolean = true
    ): String {
        check(config.enabled) { "Backend desligado" }
        val token = if (authenticated) tokenProvider() else null
        val headers = buildMap {
            put("apikey", config.anonKey)
            put("Authorization", "Bearer ${token ?: config.anonKey}")
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            if (prefer != null) put("Prefer", prefer)
        }
        val request = BackendRequest(method, config.url.trimEnd('/') + path, headers, body)
        return withContext(Dispatchers.IO) {
            var last: Exception? = null
            for (attempt in 1..maxAttempts) {
                val response = try {
                    transport.execute(request)
                } catch (e: java.io.IOException) {
                    last = e
                    continue
                }
                if (response.code in 200..299) return@withContext response.body
                val error = BackendException(response.code, "HTTP ${response.code}: ${response.body.take(200)}")
                // 5xx tenta de novo; 4xx é erro do pedido, não adianta repetir.
                if (response.code < 500) throw error
                last = error
            }
            throw last ?: IllegalStateException("falha sem causa")
        }
    }
}
