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

/** [retryAfterSeconds]: cabeçalho Retry-After (vem com o 429 do rate limit do backend). */
data class BackendResponse(val code: Int, val body: String, val retryAfterSeconds: Long? = null)

class BackendException(val code: Int, message: String) : Exception(message) {
    /** 429: o backend limitou a frequência desta rota (ver PLANO-SEGURANCA-CUSTO.md). */
    val isRateLimited: Boolean get() = code == 429

    /** 403 BLOCKED: o administrador bloqueou este aparelho (painel de admin). */
    val isBlocked: Boolean get() = code == 403 && message.orEmpty().contains("BLOCKED")
}

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
            val retryAfter = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
            return BackendResponse(code, body, retryAfter)
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
    private val maxAttempts: Int = 2,
    private val now: () -> Long = System::currentTimeMillis
) {
    /**
     * Rota → até quando não chamar de novo, depois de um 429. Enquanto isso, falha na hora sem ir à
     * rede: o app não insiste num limite que já estourou (e não gasta requisição à toa).
     */
    private val blockedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Fornece o access token do usuário; null = usa a anon key. Ligado pelo [BackendSession]. */
    var tokenProvider: suspend () -> String? = { null }

    /**
     * 401 com token de usuário (ex.: o usuário anônimo foi apagado no servidor): descarta a sessão.
     * Ligado pelo [BackendSession]; a chamada é repetida uma vez com a sessão nova.
     */
    var onUnauthorized: suspend () -> Unit = {}

    /** authenticated = false: leitura pública (ex.: app_config) sem criar a sessão anônima. */
    suspend fun get(path: String, authenticated: Boolean = true): String =
        send("GET", "/rest/v1/$path", null, authenticated = authenticated)

    suspend fun post(path: String, body: String, prefer: String? = null): String =
        send("POST", "/rest/v1/$path", body, prefer)

    suspend fun rpc(function: String, body: String): String =
        send("POST", "/rest/v1/rpc/$function", body)

    /** Edge Function (ex.: pix): POST com o token do usuário. */
    suspend fun function(name: String, body: String): String =
        send("POST", "/functions/v1/$name", body)

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
        val route = method + " " + path.substringBefore('?')
        blockedUntil[route]?.let { until ->
            if (now() < until) throw BackendException(429, "limite de requisições: aguardando Retry-After ($route)")
            blockedUntil.remove(route, until)
        }
        val token = if (authenticated) tokenProvider() else null
        try {
            return execute(method, path, route, body, prefer, token)
        } catch (e: BackendException) {
            if (e.code != 401 || token == null) throw e
            onUnauthorized()
            val fresh = tokenProvider() ?: throw e
            return execute(method, path, route, body, prefer, fresh)
        }
    }

    private suspend fun execute(
        method: String,
        path: String,
        route: String,
        body: String?,
        prefer: String?,
        token: String?
    ): String {
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
                if (response.code == 429) {
                    val waitS = (response.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_S).coerceIn(1, MAX_RETRY_AFTER_S)
                    blockedUntil[route] = now() + waitS * 1000
                }
                // Aparelho bloqueado: não insiste na rota por um tempo (o bloqueio é revisto pelo admin).
                if (error.isBlocked) blockedUntil[route] = now() + BLOCKED_PAUSE_MS
                // 5xx tenta de novo; 4xx é erro do pedido, não adianta repetir.
                if (response.code < 500) throw error
                last = error
            }
            throw last ?: IllegalStateException("falha sem causa")
        }
    }

    private companion object {
        const val DEFAULT_RETRY_AFTER_S = 60L
        const val MAX_RETRY_AFTER_S = 300L
        const val BLOCKED_PAUSE_MS = 10 * 60_000L
    }
}
