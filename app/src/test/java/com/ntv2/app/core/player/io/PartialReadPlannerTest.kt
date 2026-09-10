package com.ntv2.app.core.player.io

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialReadPlannerTest {

    private val unset = C.LENGTH_UNSET.toLong()

    @Test
    fun `le a partir da frente quando ha bytes contiguos`() {
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 100,
            readPosition = 0,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(50), plan)
    }

    @Test
    fun `limita a leitura ao que esta disponivel contiguamente`() {
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 30,
            readPosition = 0,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(30), plan)
    }

    @Test
    fun `moov no fim - le quando a fronteira contigua cobre a posicao alta`() {
        // ExoPlayer busca o indice do MKV perto do fim; se a fronteira contígua o cobre, lê.
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 2_000_000_020,
            readPosition = 2_000_000_000,
            bytesRemaining = unset,
            requestedLength = 64,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(20), plan)
    }

    @Test
    fun `aguarda quando a posicao esta alem da fronteira e o download nao terminou`() {
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 10,
            readPosition = 10,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Wait, plan)
    }

    @Test
    fun `fim de entrada quando concluido e nada mais contiguo a partir da posicao`() {
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 100,
            readPosition = 100,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = true
        )
        assertEquals(PartialReadPlanner.Plan.EndOfInput, plan)
    }

    @Test
    fun `respeita o limite restante do dataspec`() {
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 1000,
            readPosition = 0,
            bytesRemaining = 12,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(12), plan)
    }

    @Test
    fun `posicao esparsa alem da fronteira contigua aguarda mesmo com total baixado maior`() {
        // Total baixado pode ser esparso (regiões soltas). A decisão usa só a fronteira contígua:
        // posição 500 além da fronteira 100 => aguarda, não lê lixo.
        val plan = PartialReadPlanner.plan(
            contiguousReadableEnd = 100,
            readPosition = 500,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertTrue(plan is PartialReadPlanner.Plan.Wait)
    }
}
