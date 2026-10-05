package com.ntv2.app.core.multipart

import com.ntv2.app.core.player.recovery.StallAction
import com.ntv2.app.core.player.recovery.StallPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O defeito que estes testes guardam: o estado do player só olhava a parte 1; quando ela terminava de baixar o
 * app achava que o FILME estava completo e o watchdog (reiniciar/falhar download parado) parava de agir nas
 * partes 2..N. Aqui o mesmo cálculo que o coordinator usa (agregado + contador da parte ativa) alimenta o
 * [StallPolicy] de verdade.
 */
class MultiPartWatchdogTest {

    private val gb = 1024L * 1024L * 1024L
    private val map = VirtualFileMap(List(4) { 2 * gb })

    private fun StallPolicy.waiting(t: Long, bytes: Long) = tick(
        t, waiting = true, downloadedBytes = bytes, networkReady = true, storageBlocked = false,
        restartAfterMs = 12_000L, failAfterMs = 20_000L, refreshAfterMs = 6_000L
    )

    @Test
    fun `parte 1 completa nao faz o filme parecer completo`() {
        val s = MultiPartProgress.snapshot(map, listOf(2 * gb, 0, 0, 0), currentPart = 0)
        assertEquals(2 * gb, s.downloadedBytes)
        assertFalse("o filme inteiro ainda não baixou", s.downloadedBytes >= s.totalBytes)
    }

    @Test
    fun `so e completo quando todas as partes estao completas`() {
        val s = MultiPartProgress.snapshot(map, List(4) { 2 * gb }, currentPart = 3)
        assertTrue(s.downloadedBytes >= s.totalBytes)
        // Partes já assistidas e apagadas contam como feitas: na última parte, com ela cheia, o filme "completa".
        val depoisDeApagar = MultiPartProgress.snapshot(map, listOf(0, 0, 0, 2 * gb), currentPart = 3)
        assertTrue(depoisDeApagar.downloadedBytes >= depoisDeApagar.totalBytes)
    }

    @Test
    fun `travar na parte 3 reinicia mesmo com a parte 1 completa`() {
        val policy = StallPolicy(nowMs = 0L, initialBytes = 0L)
        val active = ActivePartCounter()
        // Lendo a parte 3 (índice 2): ela parou em 500 MB e nada mais chega.
        var inicio = active.feed(2, 500L * 1024 * 1024)
        assertEquals(StallAction.None, policy.waiting(1_000L, inicio))
        val acoes = mutableListOf<StallAction>()
        var t = 2_000L
        while (t <= 14_000L) {
            acoes += policy.waiting(t, active.feed(2, 500L * 1024 * 1024))        // sem bytes novos
            t += 1_000L
        }
        assertTrue("o watchdog tem de pedir o reinício", StallAction.Restart(1) in acoes)
    }

    @Test
    fun `pre carga da parte seguinte nao esconde o travamento da atual`() {
        val policy = StallPolicy(nowMs = 0L, initialBytes = 0L)
        val active = ActivePartCounter()
        val agregado = MonotonicCounter()
        var proxima = 0L
        active.feed(2, 800L)
        val acoes = mutableListOf<StallAction>()
        var t = 1_000L
        while (t <= 13_000L) {
            proxima += 50L * 1024 * 1024                                              // a parte 4 baixa sem parar
            agregado.feed(800L + proxima)                                             // o agregado cresce...
            acoes += policy.waiting(t, active.feed(2, 800L))                          // ...a parte atual não
            t += 1_000L
        }
        assertTrue("o agregado crescia, mas a parte lida parou", StallAction.Restart(1) in acoes)
        assertTrue(agregado.feed(800L + proxima) > 0L)
    }

    @Test
    fun `trocar de parte e andamento e nao dispara reinicio`() {
        val policy = StallPolicy(nowMs = 0L, initialBytes = 0L)
        val active = ActivePartCounter()
        assertEquals(StallAction.None, policy.waiting(5_000L, active.feed(0, 1_000L)))
        assertEquals(StallAction.None, policy.waiting(10_000L, active.feed(1, 0L)))   // emenda: abriu a parte 2
        assertEquals(StallAction.None, policy.waiting(15_000L, active.feed(1, 4_000L)))
    }

    @Test
    fun `descartar a parte assistida nao vira velocidade negativa nem some o proximo download`() {
        val contador = MonotonicCounter()
        var bruto = 0L
        var total = 0L
        repeat(10) { bruto += 100L; total = contador.feed(bruto) }
        assertEquals(1_000L, total)
        bruto = 0L                                  // a parte foi apagada na emenda
        assertEquals(1_000L, contador.feed(bruto))
        bruto += 300L                               // a parte seguinte baixou 300 bytes
        assertEquals(1_300L, contador.feed(bruto))
    }
}
