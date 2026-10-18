package com.ntv2.app.core.backend

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SupabaseClientTest {

    private val config = BackendConfig(url = "http://local:54321/", anonKey = "anon")

    private class FakeTransport(vararg responses: BackendResponse) : HttpTransport {
        val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<BackendRequest>()
        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            return queue.removeFirst()
        }
    }

    private class MemoryStore(var tokens: BackendTokens? = null) : BackendTokenStore {
        override suspend fun load() = tokens
        override suspend fun save(tokens: BackendTokens) { this.tokens = tokens }
        override suspend fun clear() { tokens = null }
    }

    private fun authBody(id: String, access: String) =
        """{"access_token":"$access","refresh_token":"r-$access","expires_in":3600,"user":{"id":"$id"}}"""

    @Test
    fun `rpc monta url e cabecalhos com a anon key sem sessao`() = runTest {
        val transport = FakeTransport(BackendResponse(200, "{}"))
        val client = SupabaseClient(config, transport)
        client.rpc("submit_report", "{}")
        val req = transport.requests.single()
        assertEquals("http://local:54321/rest/v1/rpc/submit_report", req.url)
        assertEquals("anon", req.headers["apikey"])
        assertEquals("Bearer anon", req.headers["Authorization"])
    }

    @Test
    fun `4xx falha sem repetir e 5xx repete`() = runTest {
        val bad = FakeTransport(BackendResponse(400, "x"), BackendResponse(200, "ok"))
        try {
            SupabaseClient(config, bad).get("app_config")
            fail()
        } catch (e: BackendException) {
            assertEquals(400, e.code)
        }
        assertEquals(1, bad.requests.size)

        val flaky = FakeTransport(BackendResponse(503, ""), BackendResponse(200, "ok"))
        assertEquals("ok", SupabaseClient(config, flaky).get("app_config"))
        assertEquals(2, flaky.requests.size)
    }

    @Test
    fun `sessao cria usuario anonimo e reaproveita o token`() = runTest {
        val transport = FakeTransport(
            BackendResponse(200, authBody("u1", "a1")),
            BackendResponse(200, "[]")
        )
        val store = MemoryStore()
        val client = SupabaseClient(config, transport)
        val session = BackendSession(client, store, now = { 0L })
        client.get("favorites")
        assertTrue(transport.requests[0].url.endsWith("/auth/v1/signup"))
        assertEquals("Bearer a1", transport.requests[1].headers["Authorization"])
        assertEquals("u1", session.userId())
        assertEquals("u1", store.tokens?.userId)
    }

    @Test
    fun `sessao renova token perto de expirar e recria se o refresh for recusado`() = runTest {
        val store = MemoryStore(BackendTokens("u1", "old", "r-old", expiresAtMs = 10_000))
        val transport = FakeTransport(
            BackendResponse(401, "invalid"),
            BackendResponse(200, authBody("u2", "a2"))
        )
        val session = BackendSession(SupabaseClient(config, transport), store, now = { 0L })
        assertEquals("a2", session.accessToken())
        assertTrue(transport.requests[0].url.contains("grant_type=refresh_token"))
        assertEquals("u2", store.tokens?.userId)
    }

    @Test
    fun `backend desligado nao chama a rede`() = runTest {
        val transport = FakeTransport()
        val session = BackendSession(SupabaseClient(BackendConfig("", ""), transport), MemoryStore())
        assertEquals(null, session.userId())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `429 bloqueia so a rota ate o Retry-After passar`() = runTest {
        var clock = 0L
        val transport = FakeTransport(
            BackendResponse(429, "limite", retryAfterSeconds = 20),
            BackendResponse(200, "outra rota"),
            BackendResponse(200, "depois")
        )
        val client = SupabaseClient(config, transport, now = { clock })
        try { client.rpc("search_catalog", "{}"); fail() } catch (e: BackendException) { assertTrue(e.isRateLimited) }

        clock = 19_000
        try { client.rpc("search_catalog", "{}"); fail() } catch (e: BackendException) { assertTrue(e.isRateLimited) }
        assertEquals("nao foi a rede durante o bloqueio", 1, transport.requests.size)
        assertEquals("outra rota", client.rpc("log_events", "[]"))

        clock = 20_000
        assertEquals("depois", client.rpc("search_catalog", "{}"))
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `429 sem Retry-After espera 60 s e nao repete`() = runTest {
        var clock = 0L
        val transport = FakeTransport(BackendResponse(429, ""), BackendResponse(200, "ok"))
        val client = SupabaseClient(config, transport, now = { clock })
        try { client.get("app_config"); fail() } catch (e: BackendException) { assertEquals(429, e.code) }
        assertEquals("429 nao e repetido como 5xx", 1, transport.requests.size)
        clock = 59_999
        try { client.get("app_config"); fail() } catch (e: BackendException) { assertEquals(429, e.code) }
        clock = 60_000
        assertEquals("ok", client.get("app_config"))
    }

    @Test
    fun `401 com usuario apagado recria a sessao e repete uma vez`() = runTest {
        val store = MemoryStore(BackendTokens("velho", "a-velho", "r-velho", expiresAtMs = 3_600_000))
        val transport = FakeTransport(
            BackendResponse(401, """{"code":"USER_GONE"}"""),
            BackendResponse(200, authBody("novo", "a-novo")),
            BackendResponse(200, "ok")
        )
        val client = SupabaseClient(config, transport)
        val session = BackendSession(client, store, now = { 0L })
        assertEquals("ok", client.rpc("log_events", "[]"))
        assertEquals("Bearer a-velho", transport.requests[0].headers["Authorization"])
        assertTrue(transport.requests[1].url.endsWith("/auth/v1/signup"))
        assertEquals("Bearer a-novo", transport.requests[2].headers["Authorization"])
        assertEquals("novo", session.userId())
    }

    @Test
    fun `401 sem token de usuario nao entra em loop`() = runTest {
        val transport = FakeTransport(BackendResponse(401, "x"), BackendResponse(200, "nao deveria"))
        try { SupabaseClient(config, transport).get("app_config", authenticated = false); fail() }
        catch (e: BackendException) { assertEquals(401, e.code) }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `403 BLOCKED pausa a rota por 10 minutos`() = runTest {
        var clock = 0L
        val transport = FakeTransport(BackendResponse(403, """{"code":"BLOCKED"}"""), BackendResponse(200, "ok"))
        val client = SupabaseClient(config, transport, now = { clock })
        try { client.rpc("log_events", "[]"); fail() } catch (e: BackendException) { assertTrue(e.isBlocked) }
        clock = 9 * 60_000L
        try { client.rpc("log_events", "[]"); fail() } catch (e: BackendException) { }
        assertEquals("nao foi a rede durante a pausa", 1, transport.requests.size)
        clock = 10 * 60_000L
        assertEquals("ok", client.rpc("log_events", "[]"))
    }
}
