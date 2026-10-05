package com.ntv2.app.core.multipart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPartProgressTest {

    private val gb = 1024L * 1024L * 1024L

    // 4 partes de 2 GB (a última, 1 GB)
    private val map = VirtualFileMap(listOf(2 * gb, 2 * gb, 2 * gb, 1 * gb))

    @Test
    fun `no comeco so a parte 1 existe parcialmente`() {
        val s = MultiPartProgress.snapshot(map, listOf(gb / 2, 0, 0, 0), currentPart = 0)
        assertEquals(4, s.partCount)
        assertEquals(1, s.currentPartNumber)
        assertEquals(7 * gb, s.totalBytes)
        assertEquals(gb / 2, s.downloadedBytes)
        assertEquals(0.25f, s.currentPartFraction, 0.001f)
        assertEquals(0L, s.aheadBytes)
        assertEquals(
            listOf(PartStatus.CURRENT, PartStatus.PENDING, PartStatus.PENDING, PartStatus.PENDING),
            s.cells.map { it.status }
        )
    }

    @Test
    fun `partes anteriores contam como concluidas mesmo descartadas do disco`() {
        // Parte 3 em leitura; as partes 1 e 2 já foram assistidas e apagadas (0 byte no TDLib).
        val s = MultiPartProgress.snapshot(map, listOf(0, 0, gb, 0), currentPart = 2)
        assertEquals(2 * gb + 2 * gb + gb, s.downloadedBytes)                  // 4 GB assistidos + 1 GB da atual
        assertEquals(gb, s.currentPartDownloaded)
        assertEquals(listOf(PartStatus.WATCHED, PartStatus.WATCHED, PartStatus.CURRENT, PartStatus.PENDING),
            s.cells.map { it.status })
    }

    @Test
    fun `pre-carga da proxima parte aparece como a frente`() {
        val s = MultiPartProgress.snapshot(map, listOf(2 * gb, 2 * gb, 2 * gb, 700L * 1024 * 1024), currentPart = 0)
        assertEquals(4 * gb + 700L * 1024 * 1024, s.aheadBytes)                // partes 2, 3 e 4: só as DEPOIS da atual
        assertEquals(PartStatus.DOWNLOADED, s.cells[1].status)
        assertEquals(PartStatus.DOWNLOADED, s.cells[2].status)
        assertEquals(PartStatus.PARTIAL, s.cells[3].status)
        assertEquals(700f * 1024 * 1024 / gb, s.cells[3].fraction, 0.001f)
    }

    @Test
    fun `bytes acima do tamanho da parte nao passam de 100 por cento`() {
        val s = MultiPartProgress.snapshot(map, listOf(5 * gb, 0, 0, 0), currentPart = 0)
        assertEquals(2 * gb, s.downloadedBytes)
        assertEquals(1f, s.currentPartFraction, 0f)
    }

    @Test
    fun `parte atual fora da faixa e fixada ao intervalo`() {
        assertEquals(3, MultiPartProgress.snapshot(map, listOf(0, 0, 0, 0), currentPart = 99).currentPart)
        assertEquals(0, MultiPartProgress.snapshot(map, listOf(0, 0, 0, 0), currentPart = -4).currentPart)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `quantidade de partes diferente do mapa e erro`() {
        MultiPartProgress.snapshot(map, listOf(0, 0), currentPart = 0)
    }

    @Test
    fun `filme de 39 partes agrupa em ate 40 celulas e mantem a atual visivel`() {
        val grande = VirtualFileMap(List(39) { 2 * gb })
        val s = MultiPartProgress.snapshot(grande, List(39) { 0L }, currentPart = 20)
        assertEquals(39, s.cells.size)
        assertEquals(39, s.groupedCells().size)                                 // 39 <= 40: sem agrupar
        val g = MultiPartProgress.group(s.cells, maxCells = 10)
        assertEquals(10, g.size)
        assertEquals(1, g.count { it.status == PartStatus.CURRENT })
        assertEquals(PartStatus.CURRENT, g[20 / 4].status)                      // blocos de 4 partes
        assertTrue(g.first().status == PartStatus.WATCHED && g.last().status == PartStatus.PENDING)
    }

    @Test
    fun `agrupamento mistura baixadas e parciais com criterio`() {
        val celulas = listOf(
            PartCell(0, PartStatus.DOWNLOADED, 1f), PartCell(1, PartStatus.DOWNLOADED, 1f),
            PartCell(2, PartStatus.DOWNLOADED, 1f), PartCell(3, PartStatus.PARTIAL, 0.5f),
            PartCell(4, PartStatus.PENDING, 0f), PartCell(5, PartStatus.PENDING, 0f)
        )
        val g = MultiPartProgress.group(celulas, maxCells = 3)
        assertEquals(listOf(PartStatus.DOWNLOADED, PartStatus.PARTIAL, PartStatus.PENDING), g.map { it.status })
        assertEquals(0.75f, g[1].fraction, 0.001f)
    }

    // ---- contadores

    @Test
    fun `contador monotonico soma so os aumentos e ignora a queda do descarte`() {
        val c = MonotonicCounter()
        assertEquals(100L, c.feed(100))
        assertEquals(300L, c.feed(300))
        assertEquals(300L, c.feed(50))            // a parte foi apagada do disco: não anda para trás
        assertEquals(350L, c.feed(100))           // e o download seguinte volta a contar
        assertEquals(350L, c.feed(100))
    }

    @Test
    fun `contador da parte ativa conta a troca de parte como andamento`() {
        val c = ActivePartCounter()
        val inicio = c.feed(0, 1_000)
        assertEquals(inicio, c.feed(0, 1_000))                    // nada novo: parado
        val depois = c.feed(0, 1_500)
        assertEquals(inicio + 500, depois)
        val emenda = c.feed(1, 0)                                 // abriu a parte 2
        assertTrue(emenda > depois)
        assertEquals(emenda, c.feed(1, 0))                        // parte 2 sem bytes: de novo parado
        assertEquals(emenda + 200, c.feed(1, 200))
    }

    @Test
    fun `pre carga da proxima parte nao mascara travamento da parte atual`() {
        val c = ActivePartCounter()
        var ativo = c.feed(2, 800)
        repeat(5) { ativo = c.feed(2, 800) }                      // a parte 3 parou, a pré-carga da 4 (não entra) segue
        assertEquals(c.feed(2, 800), ativo)
    }

    // ---- parte em leitura

    @Test
    fun `estado da parte em leitura e por filme`() {
        val estado = MultiPartPlaybackState()
        assertEquals(0, estado.currentPart(10).value)
        estado.set(10, 4)
        estado.set(20, 7)
        assertEquals(4, estado.currentPart(10).value)
        assertEquals(7, estado.currentPart(20).value)
        estado.reset(10)
        assertEquals(0, estado.currentPart(10).value)
        assertEquals(7, estado.currentPart(20).value)
    }
}
