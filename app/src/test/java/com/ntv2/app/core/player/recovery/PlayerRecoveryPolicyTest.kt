package com.ntv2.app.core.player.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRecoveryPolicyTest {

    private fun policy(bad: List<Long> = emptyList()) = PlayerRecoveryPolicy().apply { startMedia("m") { bad } }

    @Test
    fun `falha do hardware grava o ponto e liga o software`() {
        val p = policy()
        val action = p.onDecoderError(positionMs = 215_000L, durationMs = 7_000_000L, videoHeight = 800, usingSoftware = false)
        assertEquals(RecoveryAction.RestartDecoder(215_000L, useSoftware = true, newBadPositionMs = 215_000L), action)
        assertEquals(listOf(215_000L), p.badPositions)
        assertTrue(p.softwareWanted(212_000L, 800))
    }

    @Test
    fun `falha ja no software pula o trecho com pulo crescente`() {
        val p = policy()
        val first = p.onDecoderError(919_000L, 7_000_000L, 800, usingSoftware = true) as RecoveryAction.RestartDecoder
        val second = p.onDecoderError(926_000L, 7_000_000L, 800, usingSoftware = true) as RecoveryAction.RestartDecoder
        assertEquals(921_000L, first.positionMs)
        assertEquals(2_000L, first.skippedMs)
        assertEquals(930_000L, second.positionMs)
        assertNull(first.newBadPositionMs)
        assertTrue(p.badPositions.isEmpty())
    }

    @Test
    fun `pulo nunca passa do fim do video`() {
        val p = policy()
        val action = p.onDecoderError(9_500L, durationMs = 10_000L, videoHeight = 800, usingSoftware = true)
        assertEquals(9_000L, (action as RecoveryAction.RestartDecoder).positionMs)
    }

    @Test
    fun `4K nao grava ponto nem liga software`() {
        val p = policy()
        val action = p.onFreeze(82_000L, videoHeight = 2160, usingSoftware = false) as RecoveryAction.RestartDecoder
        assertFalse(action.useSoftware)
        assertNull(action.newBadPositionMs)
        assertTrue(p.badPositions.isEmpty())
    }

    @Test
    fun `erros do decodificador esgotam e desistem`() {
        val p = policy()
        repeat(PlayerRecoveryPolicy.MAX_DECODER_ERRORS) { p.onDecoderError(it * 100_000L, 0L, 800, usingSoftware = true) }
        assertEquals(RecoveryAction.GiveUp, p.onDecoderError(0L, 0L, 800, usingSoftware = true))
    }

    @Test
    fun `4K que o hardware recusa sempre no mesmo ponto desiste cedo`() {
        val p = policy()
        assertTrue(p.onDecoderError(0L, 7_000_000L, videoHeight = 1606, usingSoftware = false) is RecoveryAction.RestartDecoder)
        assertTrue(p.onDecoderError(0L, 7_000_000L, videoHeight = 1606, usingSoftware = false) is RecoveryAction.RestartDecoder)
        assertEquals(RecoveryAction.Unsupported, p.onDecoderError(0L, 7_000_000L, videoHeight = 1606, usingSoftware = false))
    }

    @Test
    fun `4K que falha em pontos diferentes continua recriando`() {
        val p = policy()
        repeat(4) {
            val action = p.onDecoderError(it * 60_000L, 7_000_000L, videoHeight = 2160, usingSoftware = false)
            assertTrue(action is RecoveryAction.RestartDecoder)
        }
    }

    @Test
    fun `ate 1080p falha no mesmo ponto vai para o software em vez de desistir`() {
        val p = policy()
        repeat(3) {
            assertTrue(p.onDecoderError(0L, 7_000_000L, videoHeight = 1080, usingSoftware = false) is RecoveryAction.RestartDecoder)
        }
    }

    @Test
    fun `congelamentos esgotados nao fazem nada`() {
        val p = policy()
        repeat(PlayerRecoveryPolicy.MAX_FREEZES) { p.onFreeze(it * 100_000L, 800, usingSoftware = true) }
        assertEquals(RecoveryAction.None, p.onFreeze(0L, 800, usingSoftware = true))
    }

    @Test
    fun `rede reabre com espera crescente e desiste no limite`() {
        val p = policy()
        assertEquals(RecoveryAction.Reopen(5_000L, 2_000L), p.onIoError(5_000L))
        assertEquals(RecoveryAction.Reopen(5_000L, 4_000L), p.onIoError(5_000L))
        assertEquals(RecoveryAction.Reopen(5_000L, 6_000L), p.onIoError(5_000L))
        assertEquals(RecoveryAction.GiveUp, p.onIoError(5_000L))
    }

    @Test
    fun `um minuto tocando bem zera os limites`() {
        val p = policy()
        repeat(PlayerRecoveryPolicy.MAX_IO_ERRORS) { p.onIoError(0L) }
        repeat(59) { p.onPlayingSecond(healthy = true) }
        p.onNotPlaying() // interrompe: recomeça a contagem
        repeat(59) { p.onPlayingSecond(healthy = true) }
        assertEquals(RecoveryAction.GiveUp, p.onIoError(0L))
        p.onPlayingSecond(healthy = true)
        assertTrue(p.onIoError(0L) is RecoveryAction.Reopen)
    }

    @Test
    fun `rajada de descarte respeita o intervalo minimo`() {
        val p = policy()
        assertTrue(p.onDropBurst(10_000L, nowMs = 1_000L, usingSoftware = false) is RecoveryAction.RestartDecoder)
        assertEquals(RecoveryAction.None, p.onDropBurst(20_000L, nowMs = 20_000L, usingSoftware = false))
        assertTrue(p.onDropBurst(40_000L, nowMs = 31_000L, usingSoftware = false) is RecoveryAction.RestartDecoder)
    }

    @Test
    fun `outro video zera tudo e carrega os pontos lembrados`() {
        val p = policy()
        repeat(PlayerRecoveryPolicy.MAX_IO_ERRORS) { p.onIoError(0L) }
        assertFalse(p.startMedia("m") { error("mesmo vídeo não recarrega") })
        assertTrue(p.startMedia("outro") { listOf(82_500L) })
        assertEquals(listOf(82_500L), p.badPositions)
        assertTrue(p.onIoError(0L) is RecoveryAction.Reopen)
        assertTrue(p.softwareWanted(80_000L, 0))
        assertFalse(p.softwareWanted(100_000L, 0))
    }
}
