package com.ntv2.app.core.backend.link

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendRequest
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.BackendSession
import com.ntv2.app.core.backend.BackendTokenStore
import com.ntv2.app.core.backend.BackendTokens
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLinkRepositoryTest {

    private class FakeServer(var claimResponse: BackendResponse = BackendResponse(200, """{"status":"linked"}""")) : HttpTransport {
        val requests = mutableListOf<BackendRequest>()
        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            val path = request.url.substringAfter("54321")
            return when {
                path.startsWith("/auth/v1/signup") ->
                    BackendResponse(200, """{"access_token":"t1","refresh_token":"r1","expires_in":3600,"user":{"id":"u1"}}""")
                path.endsWith("/rpc/link_create") ->
                    BackendResponse(200, """{"code":"K7P2QX","expires_at":"2026-10-18T03:10:00.123456+00:00"}""")
                path.endsWith("/rpc/link_status") ->
                    BackendResponse(200, """{"status":"linked","device_name":"Celular · Pixel"}""")
                path.endsWith("/rpc/link_claim") -> claimResponse
                path.endsWith("/rpc/account_devices") -> BackendResponse(200,
                    """[{"device_name":"Celular · Pixel","joined_at":"2026-10-18T03:00:00+00:00","this_device":false},
                        {"device_name":null,"joined_at":"2026-10-18T03:01:00+00:00","this_device":true}]""")
                path.endsWith("/rpc/link_leave") -> BackendResponse(200, """{"status":"left"}""")
                else -> BackendResponse(404, "")
            }
        }
    }

    private class MemoryTokens : BackendTokenStore {
        var tokens: BackendTokens? = null
        override suspend fun load() = tokens
        override suspend fun save(tokens: BackendTokens) { this.tokens = tokens }
        override suspend fun clear() { tokens = null }
    }

    private fun repo(server: FakeServer, changes: MutableList<String>): DeviceLinkRepository {
        val client = SupabaseClient(BackendConfig("http://local:54321", "anon"), server)
        return DeviceLinkRepository(client, BackendSession(client, MemoryTokens()), { "TV · AFTKM" }, onAccountChanged = { changes += "sync" }, now = { 1_000L })
    }

    @Test
    fun `codigo e normalizado como no servidor`() {
        assertEquals("K7P2QX", DeviceLinkRepository.normalizeCode(" k7p-2qx "))
        assertEquals("ABC", DeviceLinkRepository.normalizeCode("a0b1c"))  // 0 e 1 não existem no alfabeto
        assertEquals("ABCDEF", DeviceLinkRepository.normalizeCode("abcdefgh"))
    }

    @Test
    fun `criar codigo manda o nome do aparelho e le a validade`() = runTest {
        val server = FakeServer()
        val code = repo(server, mutableListOf()).createCode()
        assertEquals("K7P2QX", code.code)
        assertEquals("validade contada pelo relógio local", 1_000L + DeviceLinkRepository.CODE_TTL_MS, code.expiresAtMs)
        val body = JSONObject(server.requests.last().body!!)
        assertEquals("TV · AFTKM", body.getString("device_name"))
    }

    @Test
    fun `confirmar ou ser confirmado dispara a sincronizacao`() = runTest {
        val changes = mutableListOf<String>()
        val repository = repo(FakeServer(), changes)
        assertEquals(ClaimResult.LINKED, repository.claim("k7p 2qx"))
        assertEquals(LinkStatus.LINKED, repository.status("K7P2QX").status)
        assertEquals(listOf("sync", "sync"), changes)
    }

    @Test
    fun `resultados do claim`() = runTest {
        val changes = mutableListOf<String>()
        val server = FakeServer()
        val repository = repo(server, changes)
        assertEquals("codigo curto nem vai a rede", ClaimResult.INVALID, repository.claim("K7P"))
        assertTrue(server.requests.none { it.url.endsWith("/rpc/link_claim") })
        server.claimResponse = BackendResponse(200, """{"status":"expired"}""")
        assertEquals(ClaimResult.EXPIRED, repository.claim("K7P2QX"))
        server.claimResponse = BackendResponse(200, """{"status":"own_code"}""")
        assertEquals(ClaimResult.OWN_CODE, repository.claim("K7P2QX"))
        server.claimResponse = BackendResponse(429, """{"code":"RATE_LIMITED"}""", retryAfterSeconds = 30)
        assertEquals(ClaimResult.RATE_LIMITED, repository.claim("K7P2QX"))
        assertTrue("sem vínculo, sem sync", changes.isEmpty())
    }

    @Test
    fun `lista de aparelhos e sair da conta`() = runTest {
        val changes = mutableListOf<String>()
        val repository = repo(FakeServer(), changes)
        val devices = repository.devices()
        assertEquals(2, devices.size)
        assertEquals("Celular · Pixel", devices[0].name)
        assertEquals(null, devices[1].name)
        assertTrue(devices[1].thisDevice)
        assertTrue(repository.leave())
        assertEquals(listOf("sync"), changes)
    }
}
