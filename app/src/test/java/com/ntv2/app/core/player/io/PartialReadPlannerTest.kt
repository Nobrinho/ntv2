package com.ntv2.app.core.player.io

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PartialReadPlannerTest {

    private val unset = C.LENGTH_UNSET.toLong()

    @Test
    fun `le quando ha bytes contiguos a partir da posicao`() {
        val plan = PartialReadPlanner.plan(
            readableBytes = 100,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(50), plan)
    }

    @Test
    fun `limita a leitura ao que esta disponivel contiguamente`() {
        val plan = PartialReadPlanner.plan(
            readableBytes = 30,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(30), plan)
    }

    @Test
    fun `aguarda quando nao ha bytes na posicao e o download nao terminou`() {
        val plan = PartialReadPlanner.plan(
            readableBytes = 0,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Wait, plan)
    }

    @Test
    fun `fim de entrada quando concluido e nada mais contiguo a partir da posicao`() {
        val plan = PartialReadPlanner.plan(
            readableBytes = 0,
            bytesRemaining = unset,
            requestedLength = 50,
            isComplete = true
        )
        assertEquals(PartialReadPlanner.Plan.EndOfInput, plan)
    }

    @Test
    fun `respeita o limite restante do dataspec`() {
        val plan = PartialReadPlanner.plan(
            readableBytes = 1000,
            bytesRemaining = 12,
            requestedLength = 50,
            isComplete = false
        )
        assertEquals(PartialReadPlanner.Plan.Read(12), plan)
    }
}
