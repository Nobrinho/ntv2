package com.ntv2.app.core.player.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class StallPolicyTest {

    // Limites do watchdog durante a reprodução.
    private fun StallPolicy.waitTick(
        t: Long,
        bytes: Long,
        networkReady: Boolean = true,
        storageBlocked: Boolean = false,
        refreshAfterMs: Long? = 6_000L
    ) = tick(t, waiting = true, downloadedBytes = bytes, networkReady = networkReady, storageBlocked = storageBlocked,
        restartAfterMs = 12_000L, failAfterMs = 20_000L, refreshAfterMs = refreshAfterMs)

    @Test
    fun `parado reconecta, reinicia e depois desiste`() {
        val s = StallPolicy(nowMs = 0L, initialBytes = 100L)
        assertEquals(StallAction.None, s.waitTick(5_000L, 100L))
        assertEquals(StallAction.RefreshNetwork, s.waitTick(6_000L, 100L))
        assertEquals(StallAction.None, s.waitTick(7_000L, 100L))
        assertEquals(StallAction.Restart(1), s.waitTick(12_000L, 100L))
        assertEquals(StallAction.RefreshNetwork, s.waitTick(18_000L, 100L))
        assertEquals(StallAction.Restart(2), s.waitTick(24_000L, 100L))
        // Reinícios esgotados: ainda reconecta uma vez e, passado o limite, desiste.
        assertEquals(StallAction.RefreshNetwork, s.waitTick(40_000L, 100L))
        assertEquals(StallAction.FailStalled(100L), s.waitTick(45_000L, 100L))
    }

    @Test
    fun `total baixado caindo para zero conta como progresso`() {
        // Cópia local recriada (janela de disco): com ">" parecia parado e virava "Telegram parou".
        val s = StallPolicy(nowMs = 0L, initialBytes = 900_000_000L)
        repeat(30) { i -> assertEquals(StallAction.None, s.waitTick(i * 1_000L + 1_000L, i * 1_000_000L)) }
    }

    @Test
    fun `rede reconectando nao conta como parado`() {
        val s = StallPolicy(nowMs = 0L, initialBytes = 100L)
        repeat(60) { i -> assertEquals(StallAction.None, s.waitTick(i * 1_000L, 100L, networkReady = false)) }
    }

    @Test
    fun `sem espaco nao culpa a rede e vira erro de armazenamento`() {
        val s = StallPolicy(nowMs = 0L, initialBytes = 100L)
        assertEquals(StallAction.None, s.waitTick(6_000L, 100L, storageBlocked = true))
        assertEquals(StallAction.None, s.waitTick(15_000L, 100L, storageBlocked = true))
        assertEquals(StallAction.FailLowStorage, s.waitTick(20_000L, 100L, storageBlocked = true))
    }

    @Test
    fun `tocando bem por um minuto zera os reinicios`() {
        val s = StallPolicy(nowMs = 0L, initialBytes = 100L)
        s.waitTick(6_000L, 100L)
        s.waitTick(12_000L, 100L) // reinício 1
        assertEquals(1, s.restarts)
        s.tick(13_000L, waiting = false, downloadedBytes = 200L, networkReady = true, storageBlocked = false,
            restartAfterMs = 12_000L, failAfterMs = 20_000L, refreshAfterMs = 6_000L)
        s.tick(73_000L, waiting = false, downloadedBytes = 300L, networkReady = true, storageBlocked = false,
            restartAfterMs = 12_000L, failAfterMs = 20_000L, refreshAfterMs = 6_000L)
        assertEquals(0, s.restarts)
    }

    @Test
    fun `sem reconexao antecipada no inicio do video`() {
        val s = StallPolicy(nowMs = 0L, initialBytes = -1L)
        assertEquals(StallAction.None, s.waitTick(6_000L, -1L, refreshAfterMs = null))
        assertEquals(StallAction.Restart(1), s.waitTick(12_000L, -1L, refreshAfterMs = null))
    }
}
