package com.ntv2.app.core.backend.telemetry

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendRequest
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryLoggerTest {

    private class FakeTransport(var fail: Boolean = false) : HttpTransport {
        val requests = mutableListOf<BackendRequest>()
        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            if (fail) throw java.io.IOException("offline")
            val n = JSONObject(request.body!!).getJSONArray("events").length()
            return BackendResponse(200, n.toString())
        }
        fun batchSizes() = requests.map { JSONObject(it.body!!).getJSONArray("events").length() }
    }

    private val enabled = BackendConfig(url = "http://local:54321", anonKey = "anon")

    private fun logger(
        transport: FakeTransport,
        config: BackendConfig = enabled,
        flag: () -> Boolean = { true },
        maxBuffered: Int = 1_000
    ) = TelemetryLogger(
        client = SupabaseClient(config, transport, maxAttempts = 1),
        telemetryEnabled = flag,
        appVersion = "0.4.24",
        deviceModel = "Amazon AFTKM",
        now = { 0L },
        maxBuffered = maxBuffered
    )

    @Test
    fun `flush envia em lotes de no maximo 100 pela rpc log_events`() = runTest {
        val transport = FakeTransport()
        val t = logger(transport)
        repeat(250) { t.log(TelemetryEvents.MEDIA_OPEN, mapOf("media_id" to "c_$it")) }

        assertEquals(250, t.flush())
        assertEquals(listOf(100, 100, 50), transport.batchSizes())
        assertEquals(0, t.pendingCount)
        assertTrue(transport.requests.all { it.url.endsWith("/rest/v1/rpc/log_events") })

        val first = JSONObject(transport.requests[0].body!!).getJSONArray("events").getJSONObject(0)
        assertEquals("media_open", first.getString("name"))
        assertEquals("c_0", first.getJSONObject("props").getString("media_id"))
        assertEquals("0.4.24", first.getString("app_version"))
        assertEquals("Amazon AFTKM", first.getString("device_model"))
        assertEquals("1970-01-01T00:00:00Z", first.getString("at"))
    }

    @Test
    fun `falha devolve o lote ao buffer e o proximo flush reenvia na ordem`() = runTest {
        val transport = FakeTransport(fail = true)
        val t = logger(transport)
        t.log(TelemetryEvents.APP_OPEN, emptyMap())
        t.log(TelemetryEvents.SEARCH_EMPTY, mapOf("q" to "xyz"))

        assertEquals(0, t.flush())
        assertEquals(2, t.pendingCount)

        transport.fail = false
        assertEquals(2, t.flush())
        val events = JSONObject(transport.requests.last().body!!).getJSONArray("events")
        assertEquals("app_open", events.getJSONObject(0).getString("name"))
        assertEquals("search_empty", events.getJSONObject(1).getString("name"))
        assertEquals("xyz", events.getJSONObject(1).getJSONObject("props").getString("q"))
    }

    @Test
    fun `buffer tem teto e descarta os mais antigos`() = runTest {
        val transport = FakeTransport()
        val t = logger(transport, maxBuffered = 3)
        repeat(5) { t.log("e$it", emptyMap()) }
        assertEquals(3, t.pendingCount)
        t.flush()
        val events = JSONObject(transport.requests.single().body!!).getJSONArray("events")
        assertEquals("e2", events.getJSONObject(0).getString("name"))
    }

    @Test
    fun `no-op com backend desligado ou flag telemetry false`() = runTest {
        val transport = FakeTransport()
        val disabled = logger(transport, config = BackendConfig("", ""))
        disabled.log(TelemetryEvents.APP_OPEN, emptyMap())
        assertEquals(0, disabled.pendingCount)
        assertEquals(0, disabled.flush())

        var flag = true
        val flagged = logger(transport, flag = { flag })
        flagged.log(TelemetryEvents.APP_OPEN, emptyMap())
        flag = false
        flagged.log(TelemetryEvents.APP_OPEN, emptyMap())
        assertEquals(1, flagged.pendingCount)
        // Flag desligou antes do envio: descarta o que havia.
        assertEquals(0, flagged.flush())
        assertEquals(0, flagged.pendingCount)
        assertTrue(transport.requests.isEmpty())
    }
}
