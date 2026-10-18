package com.ntv2.app.core.backend.premium

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumRepositoryTest {

    private class FakeServer : HttpTransport {
        val requests = mutableListOf<BackendRequest>()
        var premium = false
        var pixResponse = BackendResponse(200,
            """{"payment_id":"p1","qr_code":"000201pix","qr_code_base64":"iVBOR","amount_cents":990,"plan_name":"Mensal","expires_at":"2026-10-18T12:30:00Z"}""")
        var checkStatus = "pending"

        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            val path = request.url.substringAfter("54321")
            return when {
                path.startsWith("/auth/v1/signup") ->
                    BackendResponse(200, """{"access_token":"t1","refresh_token":"r1","expires_in":3600,"user":{"id":"u1"}}""")
                path.endsWith("/rpc/premium_status") -> BackendResponse(200, JSONObject()
                    .put("premium", premium)
                    .put("premium_until", if (premium) "2026-11-17T14:28:21.840977+00:00" else JSONObject.NULL)
                    .put("plans", org.json.JSONArray("""[{"id":"monthly","name":"Mensal","price_cents":990,"days":30},
                        {"id":"yearly","name":"Anual","price_cents":8990,"days":365}]"""))
                    .toString())
                path.endsWith("/functions/v1/pix") -> {
                    val body = JSONObject(request.body!!)
                    if (body.getString("action") == "create") pixResponse
                    else {
                        if (checkStatus == "approved") premium = true
                        BackendResponse(200, """{"status":"$checkStatus"}""")
                    }
                }
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

    private fun repo(server: FakeServer, changes: MutableList<String> = mutableListOf(), clock: () -> Long = { 1_000L }) =
        SupabaseClient(BackendConfig("http://local:54321", "anon"), server).let { client ->
            PremiumRepository(client, BackendSession(client, MemoryTokens()), onPremiumChanged = { changes += "sync" }, now = clock)
        }

    @Test
    fun `status traz planos e validade`() = runTest {
        val server = FakeServer().apply { premium = true }
        val status = repo(server).refresh()
        assertTrue(status.premium)
        assertEquals(listOf("monthly", "yearly"), status.plans.map { it.id })
        assertEquals(8990, status.plans[1].priceCents)
        assertTrue((status.premiumUntilMs ?: 0) > 0)
    }

    @Test
    fun `cobranca pix com validade contada pelo relogio local`() = runTest {
        val server = FakeServer()
        val r = repo(server).createPix("monthly")
        r as PixResult.Created
        assertEquals("000201pix", r.charge.qrCode)
        assertEquals(990, r.charge.amountCents)
        assertEquals(1_000L + PremiumRepository.PIX_TTL_MS, r.charge.expiresAtMs)
        val body = JSONObject(server.requests.last { it.url.endsWith("/functions/v1/pix") }.body!!)
        assertEquals("monthly", body.getString("plan_id"))
    }

    @Test
    fun `erros da cobranca viram mensagens`() = runTest {
        val server = FakeServer()
        val r = repo(server)
        server.pixResponse = BackendResponse(503, """{"error":"payments_unavailable"}""")
        assertEquals(PixResult.Unavailable, r.createPix("monthly"))
        server.pixResponse = BackendResponse(400, """{"error":"too_many_payments"}""")
        assertEquals(PixResult.TooMany, r.createPix("monthly"))
        server.pixResponse = BackendResponse(502, """{"error":"payment_failed"}""")
        assertEquals(PixResult.Failed, r.createPix("monthly"))
    }

    @Test
    fun `aprovado atualiza o status e dispara a sincronizacao`() = runTest {
        val server = FakeServer()
        val changes = mutableListOf<String>()
        val r = repo(server, changes)
        assertFalse(r.refresh().premium)
        assertEquals(PaymentState.PENDING, r.check("p1"))
        server.checkStatus = "approved"
        assertEquals(PaymentState.APPROVED, r.check("p1"))
        assertTrue(r.status.value!!.premium)
        assertEquals(listOf("sync"), changes)
    }

    @Test
    fun `isPremium usa cache e nao vai a rede toda vez`() = runTest {
        val server = FakeServer().apply { premium = true }
        var clock = 0L
        val r = repo(server, clock = { clock })
        assertTrue(r.isPremium())
        val calls = server.requests.count { it.url.endsWith("/rpc/premium_status") }
        clock = 60_000
        assertTrue(r.isPremium())
        assertEquals(calls, server.requests.count { it.url.endsWith("/rpc/premium_status") })
        clock = 11 * 60_000L
        server.premium = false
        assertFalse("cache vencido consulta de novo", r.isPremium())
    }

    @Test
    fun `preco formatado`() {
        assertEquals("R$ 9,90", PremiumRepository.formatPrice(990))
        assertEquals("R$ 89,90", PremiumRepository.formatPrice(8990))
    }
}
