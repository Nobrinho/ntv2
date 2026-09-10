package com.ntv2.app.core.player.download

import com.ntv2.app.core.player.config.PlaybackTuning
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressiveDownloadPlannerTest {

    private val tuning = PlaybackTuning()
    private val planner = ProgressiveDownloadPlanner(tuning)
    private val expected = 100L * 1024 * 1024 // 100 MB

    @Test
    fun `alvo no inicio e a janela de leitura adiante`() {
        val target = planner.computeTargetEndBytes(currentPositionMs = 0, durationMs = 100_000, expectedBytes = expected)
        assertEquals(tuning.aheadWindowBytes, target)
    }

    @Test
    fun `alvo no meio soma janela ao byte proporcional`() {
        val target = planner.computeTargetEndBytes(currentPositionMs = 50_000, durationMs = 100_000, expectedBytes = expected)
        assertEquals(expected / 2 + tuning.aheadWindowBytes, target)
    }

    @Test
    fun `alvo perto do fim e limitado ao tamanho esperado`() {
        val target = planner.computeTargetEndBytes(currentPositionMs = 99_000, durationMs = 100_000, expectedBytes = expected)
        assertEquals(expected, target)
    }

    @Test
    fun `duracao invalida cai no preload inicial`() {
        val target = planner.computeTargetEndBytes(currentPositionMs = 1_000, durationMs = 0, expectedBytes = expected)
        assertEquals(tuning.initialPreloadBytes, target)
    }

    @Test
    fun `tamanho esperado invalido cai no preload inicial`() {
        val target = planner.computeTargetEndBytes(currentPositionMs = 1_000, durationMs = 100_000, expectedBytes = 0)
        assertEquals(tuning.initialPreloadBytes, target)
    }
}
