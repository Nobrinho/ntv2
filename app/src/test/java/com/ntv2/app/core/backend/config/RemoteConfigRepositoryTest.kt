package com.ntv2.app.core.backend.config

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigRepositoryTest {

    private val body = """
        [
          {"key":"min_version_code","value":29},
          {"key":"report_recipient","value":"@Eminobre2"},
          {"key":"flags","value":{"remote_search":true,"sync":false,"telemetry":false}},
          {"key":"decoder_overrides","value":{"AFTKM":"software_fallback"}},
          {"key":"desconhecida","value":"x"}
        ]
    """.trimIndent()

    private class MemoryCache(var body: String? = null) : RemoteConfigCache {
        override suspend fun load() = body
        override suspend fun save(body: String) { this.body = body }
    }

    private val enabled = BackendConfig(url = "http://local:54321", anonKey = "anon")

    @Test
    fun `parse le todas as chaves do contrato`() {
        val config = RemoteConfig.parse(body)
        assertEquals(29L, config.minVersionCode)
        assertEquals("Eminobre2", config.reportRecipient)
        assertFalse(config.flag(RemoteConfig.FLAG_SYNC))
        assertTrue(config.flag(RemoteConfig.FLAG_REMOTE_SEARCH))
        assertFalse(config.telemetryEnabled)
        assertEquals(mapOf("AFTKM" to "software_fallback"), config.decoderOverrides)
    }

    @Test
    fun `parse mantem o padrao para chave ausente ou com tipo errado`() {
        val config = RemoteConfig.parse(
            """[{"key":"min_version_code","value":"abc"},{"key":"report_recipient","value":""},{"key":"flags","value":[1]}]"""
        )
        assertEquals(RemoteConfig(), config)
        assertEquals("Eminobre", config.reportRecipient)
        assertTrue(config.telemetryEnabled)
        assertTrue(config.flag("inexistente"))
        assertFalse(config.flag("inexistente", default = false))
        assertEquals(RemoteConfig(), RemoteConfig.parse("[]"))
    }

    @Test
    fun `refresh busca app_config e guarda no cache`() = runTest {
        val cache = MemoryCache()
        val urls = mutableListOf<String>()
        val client = SupabaseClient(enabled, HttpTransport { urls += it.url; BackendResponse(200, body) })
        val repo = RemoteConfigRepository(client, cache)

        assertEquals(29L, repo.refresh().minVersionCode)
        assertEquals("http://local:54321/rest/v1/app_config?select=key,value", urls.single())
        assertEquals(body, cache.body)
        assertEquals(29L, repo.config.value.minVersionCode)
    }

    @Test
    fun `falha de rede usa o cache salvo`() = runTest {
        val cache = MemoryCache(body)
        val client = SupabaseClient(enabled, HttpTransport { throw java.io.IOException("offline") })
        val repo = RemoteConfigRepository(client, cache)

        assertEquals("Eminobre2", repo.refresh().reportRecipient)
        assertEquals(29L, repo.get().minVersionCode)
    }

    @Test
    fun `backend desligado fica nos padroes sem chamar a rede nem o cache`() = runTest {
        val cache = MemoryCache(body)
        var calls = 0
        val client = SupabaseClient(BackendConfig("", ""), HttpTransport { calls++; BackendResponse(200, body) })
        val repo = RemoteConfigRepository(client, cache)

        assertEquals(RemoteConfig(), repo.refresh())
        assertEquals(RemoteConfig(), repo.get())
        assertEquals(0, calls)
    }
}
