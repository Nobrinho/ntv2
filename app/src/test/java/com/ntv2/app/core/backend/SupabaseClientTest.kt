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
}
