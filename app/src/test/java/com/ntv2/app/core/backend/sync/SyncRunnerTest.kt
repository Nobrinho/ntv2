package com.ntv2.app.core.backend.sync

import com.ntv2.app.core.backend.BackendException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SyncRunnerTest {

    private class Fixture(var result: () -> SyncResult = { SyncResult() }) {
        var clock = 1_000_000L
        val calls = mutableListOf<Boolean>()
        var saved = 0L
        val runner = SyncRunner(
            run = { forceFull -> calls += forceFull; result() },
            saveLastSuccess = { saved = it },
            now = { clock }
        )
    }

    @Test
    fun `sincronizar agora refaz tudo e conta o que chegou`() = runTest {
        val f = Fixture { SyncResult(pushed = mapOf("favorites" to 9), pulled = mapOf("favorites" to 2, "watch_history" to 1)) }
        val outcome = f.runner.syncNow()
        assertEquals(SyncOutcome.Done(sent = 9, received = 3), outcome)
        assertEquals(listOf(true), f.calls)
        assertEquals(1_000_000L, f.runner.status.value.lastSuccessAtMs)
        assertEquals(1_000_000L, f.saved)
        assertFalse(f.runner.status.value.running)
    }

    @Test
    fun `conta mudou forca tudo so na proxima rodada automatica`() = runTest {
        var fail = true
        val f = Fixture { if (fail) throw IOException("sem rede") else SyncResult() }
        f.runner.requestFullNext()
        runCatching { f.runner.auto() }
        fail = false
        f.runner.auto()
        f.runner.auto()
        assertEquals("falhou: mantem o pedido para a proxima", listOf(true, true, false), f.calls)
    }

    @Test
    fun `rodada automatica nao forca tudo e marca a ultima sincronizacao`() = runTest {
        val f = Fixture()
        f.runner.auto()
        assertEquals(listOf(false), f.calls)
        assertEquals(1_000_000L, f.runner.status.value.lastSuccessAtMs)
    }

    @Test
    fun `rodada pulada nao conta como sincronizada`() = runTest {
        val f = Fixture { SyncResult(skipped = PersonalizationSyncer.SKIP_NO_PREMIUM) }
        f.runner.auto()
        assertEquals(0L, f.runner.status.value.lastSuccessAtMs)
        assertEquals(SyncOutcome.PremiumRequired, f.runner.syncNow())
        f.clock += 60_000L
        f.result = { SyncResult(skipped = PersonalizationSyncer.SKIP_FLAG_OFF) }
        assertEquals(SyncOutcome.Disabled, f.runner.syncNow())
        assertEquals(0L, f.saved)
    }

    @Test
    fun `toque repetido antes de 10 s nao chama o servidor`() = runTest {
        val f = Fixture()
        f.runner.syncNow()
        f.clock += 9_000L
        assertEquals(SyncOutcome.TooSoon, f.runner.syncNow())
        assertEquals(1, f.calls.size)
        f.clock += 1_000L
        assertTrue(f.runner.syncNow() is SyncOutcome.Done)
        assertEquals(2, f.calls.size)
    }

    @Test
    fun `falhas viram mensagem e nao deixam o estado em andamento`() = runTest {
        val f = Fixture { throw IOException("sem rede") }
        assertEquals(SyncOutcome.Offline, f.runner.syncNow())
        assertFalse(f.runner.status.value.running)
        f.clock += 60_000L
        f.result = { throw BackendException(429, "RATE_LIMITED") }
        assertEquals(SyncOutcome.RateLimited, f.runner.syncNow())
        f.clock += 60_000L
        f.result = { throw BackendException(500, "erro") }
        assertEquals(SyncOutcome.Failed, f.runner.syncNow())
        assertEquals(0L, f.runner.status.value.lastSuccessAtMs)
    }

    @Test
    fun `texto da ultima sincronizacao`() {
        val now = 100L * 24 * 60 * 60_000L
        assertNull(SyncRunner.lastSyncText(0L, now))
        assertEquals("Última sincronização: agora mesmo", SyncRunner.lastSyncText(now - 20_000L, now))
        assertEquals("Última sincronização: há 3 min", SyncRunner.lastSyncText(now - 3 * 60_000L, now))
        assertEquals("Última sincronização: há 2 h", SyncRunner.lastSyncText(now - 2 * 60 * 60_000L, now))
        assertEquals("Última sincronização: há 1 dia", SyncRunner.lastSyncText(now - 25 * 60 * 60_000L, now))
        // Relógio do aparelho atrasado: nunca negativo.
        assertEquals("Última sincronização: agora mesmo", SyncRunner.lastSyncText(now + 5_000L, now))
    }

    @Test
    fun `mensagens do resultado`() {
        assertEquals("Tudo em dia.", SyncRunner.message(SyncOutcome.Done(sent = 4, received = 0)))
        assertEquals("1 item recebido. Tudo em dia.", SyncRunner.message(SyncOutcome.Done(sent = 0, received = 1)))
        assertEquals("3 itens recebidos. Tudo em dia.", SyncRunner.message(SyncOutcome.Done(sent = 0, received = 3)))
    }
}
