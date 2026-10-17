package com.ntv2.app.feature.media.data.report

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendRequest
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.config.RemoteConfig
import com.ntv2.app.core.backend.telemetry.Telemetry
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaReporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val report = MediaReport(
        reason = "other",
        reasonLabel = "Outro problema",
        title = "Filme",
        year = 2020,
        channelName = "Canal",
        mediaId = "-100_42",
        note = "áudio some aos 20 min"
    )

    private class FakeTransport(var responder: (BackendRequest) -> BackendResponse) : HttpTransport {
        val requests = mutableListOf<BackendRequest>()
        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            return responder(request)
        }
    }

    private class FakeDm(var result: Boolean = true) : DirectMessageSender {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun send(recipient: String, text: String): Boolean {
            sent += recipient to text
            return result
        }
    }

    private class MemoryStore : PendingReportStore {
        var items: List<MediaReport> = emptyList()
        override suspend fun load() = items
        override suspend fun save(reports: List<MediaReport>) { items = reports }
    }

    private val enabled = BackendConfig(url = "http://local:54321", anonKey = "anon")

    private fun reporter(
        transport: FakeTransport?,
        dm: FakeDm,
        store: PendingReportStore = MemoryStore(),
        events: MutableList<String> = mutableListOf()
    ) = MediaReporter(
        client = transport?.let { SupabaseClient(enabled, it) } ?: SupabaseClient(BackendConfig("", "")),
        directMessage = dm,
        pending = store,
        config = { RemoteConfig(reportRecipient = "AdminRemoto") },
        telemetry = Telemetry { name, props -> events += "$name:${props["reason"]}" },
        deviceInfo = { "Fake TV" },
        appVersion = "1.0"
    )

    @Test
    fun `backend ligado envia pela rpc com nota e nao usa a DM`() = runTest {
        val transport = FakeTransport { BackendResponse(200, """{"status":"created","id":7}""") }
        val dm = FakeDm()
        val events = mutableListOf<String>()
        val outcome = reporter(transport, dm, events = events).send(report)

        assertEquals(ReportOutcome.Sent, outcome)
        assertTrue(dm.sent.isEmpty())
        val req = transport.requests.single()
        assertTrue(req.url.endsWith("/rest/v1/rpc/submit_report"))
        val p = JSONObject(req.body!!).getJSONObject("p")
        assertEquals("-100_42", p.getString("media_id"))
        assertEquals("other", p.getString("reason"))
        assertEquals("áudio some aos 20 min", p.getString("note"))
        assertEquals(2020, p.getInt("year"))
        assertEquals("Canal", p.getString("channel_name"))
        assertEquals("Fake TV", p.getString("device"))
        assertEquals("1.0", p.getString("app_version"))
        assertEquals(listOf("report_sent:other"), events)
    }

    @Test
    fun `duplicado e limite chegam como resultado sem fallback nem fila`() = runTest {
        val store = MemoryStore()
        val dm = FakeDm()
        val transport = FakeTransport { BackendResponse(200, """{"status":"duplicate","id":7}""") }
        val events = mutableListOf<String>()
        val r = reporter(transport, dm, store, events)
        assertEquals(ReportOutcome.Duplicate, r.send(report))

        transport.responder = { BackendResponse(200, """{"status":"rate_limited"}""") }
        assertEquals(ReportOutcome.RateLimited, r.send(report))

        assertTrue(dm.sent.isEmpty())
        assertTrue(store.items.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `rpc falhando cai na DM para o destinatario da config remota`() = runTest {
        val transport = FakeTransport { BackendResponse(503, "fora do ar") }
        val dm = FakeDm(result = true)
        val outcome = reporter(transport, dm).send(report)

        assertEquals(ReportOutcome.Sent, outcome)
        val (to, text) = dm.sent.single()
        assertEquals("AdminRemoto", to)
        assertTrue(text.contains("Outro problema"))
        assertTrue(text.contains("áudio some aos 20 min"))
        assertTrue(text.contains("msg 42"))
    }

    @Test
    fun `backend desligado usa so a DM como antes`() = runTest {
        val dm = FakeDm(result = true)
        assertEquals(ReportOutcome.Sent, reporter(null, dm).send(report))
        assertEquals(1, dm.sent.size)
    }

    @Test
    fun `nada saiu vai para a fila e e reenviado depois`() = runTest {
        val store = MemoryStore()
        val transport = FakeTransport { throw java.io.IOException("sem rede") }
        val dm = FakeDm(result = false)
        val r = reporter(transport, dm, store)

        assertEquals(ReportOutcome.Failed, r.send(report))
        // Mesmo reporte de novo não duplica na fila.
        assertEquals(ReportOutcome.Failed, r.send(report))
        assertEquals(listOf(report), store.items)

        // Ainda sem rede: continua na fila.
        assertEquals(0, r.flushPending())
        assertEquals(1, store.items.size)

        transport.responder = { BackendResponse(200, """{"status":"created","id":1}""") }
        assertEquals(1, r.flushPending())
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `fila em arquivo guarda e le os reportes`() = runTest {
        val file = java.io.File(tmp.root, "sub/pending.json")
        val store = FilePendingReportStore(file)
        assertTrue(store.load().isEmpty())
        val semNota = report.copy(mediaId = "-100_1", note = null, year = null)
        store.save(listOf(report, semNota))

        val loaded = FilePendingReportStore(file).load()
        assertEquals(listOf(report, semNota), loaded)
        assertNull(loaded[1].note)

        store.save(emptyList())
        assertFalse(file.exists())
    }

    @Test
    fun `429 do backend vira RateLimited sem DM e sem fila`() = runTest {
        val transport = FakeTransport { BackendResponse(429, """{"code":"RATE_LIMITED"}""", retryAfterSeconds = 30) }
        val dm = FakeDm()
        val store = MemoryStore()
        val outcome = reporter(transport, dm, store).send(report)
        assertEquals(ReportOutcome.RateLimited, outcome)
        assertTrue("a DM furaria o limite", dm.sent.isEmpty())
        assertTrue(store.items.isEmpty())
    }
}
