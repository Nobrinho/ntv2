package com.ntv2.app.core.backend.sync

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTimeAndSchedulerTest {

    // ---- Datas ----

    @Test
    fun `epoch ms vira ISO UTC e volta igual`() {
        val ms = 1_792_240_496_789L
        val iso = SyncTime.toIso(ms)
        assertTrue(iso, iso.endsWith("Z"))
        assertEquals("2026-10-17T12:34:56.789Z", iso)
        assertEquals(ms, SyncTime.toEpochMs(iso))
        assertEquals("1970-01-01T00:00:00Z", SyncTime.toIso(0L))
    }

    @Test
    fun `le o formato do PostgREST com offset e microssegundos`() {
        assertEquals(1_792_240_496_789L, SyncTime.toEpochMs("2026-10-17T12:34:56.789123+00:00"))
        assertEquals(1_792_240_496_000L, SyncTime.toEpochMs("2026-10-17T12:34:56+00:00"))
        // Outro fuso: convertido para o mesmo instante.
        assertEquals(1_792_240_496_000L, SyncTime.toEpochMs("2026-10-17T09:34:56-03:00"))
        assertNull(SyncTime.toEpochMs("ontem"))
        assertNull(SyncTime.toEpochMs(null))
    }

    // ---- LWW ----

    @Test
    fun `remoto so vence se for estritamente mais novo`() {
        assertTrue(LastWriteWins.remoteWins(localUpdatedAt = null, remoteUpdatedAt = 1L))
        assertTrue(LastWriteWins.remoteWins(localUpdatedAt = 1L, remoteUpdatedAt = 2L))
        assertFalse(LastWriteWins.remoteWins(localUpdatedAt = 2L, remoteUpdatedAt = 2L))
        assertFalse(LastWriteWins.remoteWins(localUpdatedAt = 3L, remoteUpdatedAt = 2L))
    }

    @Test
    fun `canal derivado do mediaId e capa so com URL`() {
        assertEquals(-100123L, PersonalizationSyncer.channelIdOf("-100123_456"))
        assertEquals(0L, PersonalizationSyncer.channelIdOf("series_1399"))
        assertEquals("https://x/y.jpg", PersonalizationSyncer.remoteUrl("https://x/y.jpg"))
        assertEquals(org.json.JSONObject.NULL, PersonalizationSyncer.remoteUrl("/data/a.jpg"))
        assertEquals(org.json.JSONObject.NULL, PersonalizationSyncer.remoteUrl(null))
    }

    // ---- Agendador ----

    private fun TestScope.scheduler(runs: MutableList<Long>, fail: Boolean = false) = PersonalizationSyncScheduler(
        scope = backgroundScope,
        runSync = {
            runs += testScheduler.currentTime
            if (fail) error("sem rede")
        },
        editDebounceMs = 3_000L,
        progressIntervalMs = 60_000L,
        settledDelayMs = 12_000L
    )

    @Test
    fun `ticks de progresso viram no maximo uma rodada por minuto`() = runTest {
        val runs = mutableListOf<Long>()
        val s = scheduler(runs)
        // Um tick a cada 10 s durante 3 min.
        repeat(18) {
            s.onLocalChange(LocalChange.PROGRESS)
            advanceTimeBy(10_000L)
        }
        runCurrent()
        // Ticks durante a espera são ignorados; o primeiro depois da rodada abre a próxima.
        assertEquals(listOf(60_000L, 130_000L), runs)
    }

    @Test
    fun `edicoes seguidas viram uma rodada so (debounce)`() = runTest {
        val runs = mutableListOf<Long>()
        val s = scheduler(runs)
        s.onLocalChange(LocalChange.EDIT)
        advanceTimeBy(1_000L)
        s.onLocalChange(LocalChange.EDIT)
        advanceTimeBy(1_000L)
        s.onLocalChange(LocalChange.EDIT)
        advanceTimeBy(10_000L)
        assertEquals(listOf(5_000L), runs)
    }

    @Test
    fun `edicao e pausa antecipam o progresso agrupado`() = runTest {
        val runs = mutableListOf<Long>()
        val s = scheduler(runs)
        s.onLocalChange(LocalChange.PROGRESS)       // agendaria para 60 s
        advanceTimeBy(5_000L)
        s.onLocalChange(LocalChange.PLAYBACK_SETTLED) // pausou: 5 + 12 = 17 s
        advanceTimeBy(100_000L)
        assertEquals(listOf(17_000L), runs)
    }

    @Test
    fun `requestNow roda ja e falha nao derruba o agendador`() = runTest {
        val runs = mutableListOf<Long>()
        val s = scheduler(runs, fail = true)
        s.requestNow()
        runCurrent()
        assertEquals(listOf(0L), runs)
        s.onLocalChange(LocalChange.PROGRESS)
        advanceTimeBy(61_000L)
        assertEquals(listOf(0L, 60_000L), runs)
    }
}
