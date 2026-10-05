package com.ntv2.app.feature.playback.presentation

import com.ntv2.app.core.multipart.MultiPartProgress
import com.ntv2.app.core.multipart.PartName
import com.ntv2.app.core.multipart.VirtualFileMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartsTextsTest {

    private val gb = 1024L * 1024L * 1024L
    private val mb = 1024L * 1024L
    private val map = VirtualFileMap(List(12) { 2 * gb })

    private fun snapshot(raw: List<Long>, current: Int) = MultiPartProgress.snapshot(map, raw, current)

    @Test
    fun `linha da parte atual traz numero total e bytes da parte`() {
        val s = snapshot(List(12) { 0L }.toMutableList().also { it[2] = 412 * mb }, current = 2)
        assertEquals("Parte 3/12 — ${formatBytes(412 * mb)} de ${formatBytes(2 * gb)}", PartsTexts.currentPartLine(s))
    }

    @Test
    fun `linha do filme todo conta as partes assistidas como feitas`() {
        val s = snapshot(List(12) { 0L }.toMutableList().also { it[2] = 1 * gb }, current = 2)   // 2 partes assistidas + 1 GB
        assertEquals("Filme todo: ${formatBytes(5 * gb)} de ${formatBytes(24 * gb)}", PartsTexts.wholeMovieLine(s))
    }

    @Test
    fun `a frente mostra a pre-carga ou diz que nao ha`() {
        val sem = snapshot(List(12) { 0L }, current = 0)
        val com = snapshot(List(12) { 0L }.toMutableList().also { it[1] = 2 * gb; it[2] = 300 * mb }, current = 0)
        assertEquals("nada baixado à frente", PartsTexts.aheadText(sem))
        assertEquals("à frente ${formatBytes(2 * gb + 300 * mb)}", PartsTexts.aheadText(com))
    }

    @Test
    fun `linha do painel de rede junta parte atual e a frente`() {
        val s = snapshot(List(12) { 0L }.toMutableList().also { it[4] = 1 * gb; it[5] = 2 * gb }, current = 4)
        assertEquals(
            "Parte 5/12 · atual ${formatBytes(1 * gb)} de ${formatBytes(2 * gb)} · à frente ${formatBytes(2 * gb)}",
            PartsTexts.networkLine(s)
        )
    }

    @Test
    fun `resumo do filme`() {
        assertEquals("12 partes · ${formatBytes(24 * gb)}", PartsTexts.summary(snapshot(List(12) { 0L }, 0)))
    }

    @Test
    fun `dica do indice cita a ultima parte so em filme dividido`() {
        assertTrue(PartsTexts.indexHint(null).contains("índice no fim do arquivo"))
        val dica = PartsTexts.indexHint(snapshot(List(12) { 0L }, 0))
        assertTrue(dica.contains("parte 12 de 12"))
    }

    // ---- nome sem o sufixo da parte

    @Test
    fun `nome de exibicao tira o sufixo da parte`() {
        assertEquals("F1 - O Filme (2025) [2160p].mkv", PartName.displayName("F1 - O Filme (2025) [2160p].mkv.part01of39"))
        assertEquals("Filme.mp4", PartName.displayName("Filme.mp4.part3of12"))
    }

    @Test
    fun `nome comum e nulo ficam como estao`() {
        assertEquals("Filme.mkv", PartName.displayName("Filme.mkv"))
        assertNull(PartName.displayName(null))
        assertEquals("Filme.mkv.part00of12", PartName.displayName("Filme.mkv.part00of12"))   // índice inválido: não é parte
    }
}
