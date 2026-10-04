package com.ntv2.app.core.multipart

import com.ntv2.app.core.multipart.VirtualFileMap.Location
import com.ntv2.app.core.multipart.VirtualFileMap.Slice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VirtualFileMapTest {

    // Partes: [0,100) [100,150) [150,400)
    private val map = VirtualFileMap(listOf(100L, 50L, 250L))

    @Test
    fun `total e limites de cada parte`() {
        assertEquals(400L, map.totalSize)
        assertEquals(3, map.partCount)
        assertEquals(0L, map.partStart(0))
        assertEquals(100L, map.partStart(1))
        assertEquals(150L, map.partStart(2))
        assertEquals(100L, map.partEnd(0))
        assertEquals(400L, map.partEnd(2))
        assertEquals(50L, map.partSize(1))
    }

    @Test
    fun `locate acha parte e offset, inclusive nas bordas`() {
        assertEquals(Location(0, 0), map.locate(0))
        assertEquals(Location(0, 99), map.locate(99))
        assertEquals(Location(1, 0), map.locate(100))
        assertEquals(Location(1, 49), map.locate(149))
        assertEquals(Location(2, 0), map.locate(150))
        assertEquals(Location(2, 249), map.locate(399))
    }

    @Test
    fun `locate fora do arquivo e nulo`() {
        assertNull(map.locate(-1))
        assertNull(map.locate(400))
        assertNull(map.locate(10_000))
    }

    @Test
    fun `bytesLeftInPart`() {
        assertEquals(100L, map.bytesLeftInPart(0))
        assertEquals(1L, map.bytesLeftInPart(99))
        assertEquals(50L, map.bytesLeftInPart(100))
        assertEquals(1L, map.bytesLeftInPart(399))
        assertEquals(0L, map.bytesLeftInPart(400))
    }

    @Test
    fun `slice dentro de uma parte`() {
        assertEquals(listOf(Slice(0, 10, 20)), map.slice(10, 20))
    }

    @Test
    fun `slice que termina exatamente no fim da parte nao invade a proxima`() {
        assertEquals(listOf(Slice(0, 90, 10)), map.slice(90, 10))
    }

    @Test
    fun `slice que cruza varias partes`() {
        assertEquals(
            listOf(Slice(0, 90, 10), Slice(1, 0, 50), Slice(2, 0, 5)),
            map.slice(90, 65)
        )
    }

    @Test
    fun `slice comecando no inicio de uma parte`() {
        assertEquals(listOf(Slice(1, 0, 20)), map.slice(100, 20))
    }

    @Test
    fun `slice e cortado no fim do arquivo`() {
        assertEquals(listOf(Slice(2, 200, 50)), map.slice(350, 1_000))
    }

    @Test
    fun `slice vazio para comprimento nao positivo ou posicao fora`() {
        assertEquals(emptyList<Slice>(), map.slice(10, 0))
        assertEquals(emptyList<Slice>(), map.slice(10, -5))
        assertEquals(emptyList<Slice>(), map.slice(400, 10))
        assertEquals(emptyList<Slice>(), map.slice(-1, 10))
    }

    @Test
    fun `sliceToEnd cobre do ponto ate o fim`() {
        assertEquals(
            listOf(Slice(1, 40, 10), Slice(2, 0, 250)),
            map.sliceToEnd(140)
        )
        assertEquals(emptyList<Slice>(), map.sliceToEnd(400))
    }

    @Test
    fun `os slices de uma faixa somam o comprimento pedido`() {
        for (pos in listOf(0L, 99L, 100L, 149L, 150L, 300L)) {
            val len = 120L
            val expected = minOf(len, 400L - pos)
            assertEquals(expected, map.slice(pos, len).sumOf { it.length })
        }
    }

    @Test
    fun `locate bate com uma busca linear em todas as posicoes`() {
        val sizes = listOf(7L, 1L, 13L, 5L, 5L, 29L)
        val m = VirtualFileMap(sizes)
        var pos = 0L
        sizes.forEachIndexed { part, size ->
            for (off in 0 until size) {
                assertEquals(Location(part, off), m.locate(pos))
                pos++
            }
        }
        assertEquals(pos, m.totalSize)
    }

    @Test
    fun `uma parte so funciona`() {
        val single = VirtualFileMap(listOf(10L))
        assertEquals(Location(0, 9), single.locate(9))
        assertEquals(listOf(Slice(0, 3, 7)), single.sliceToEnd(3))
    }

    @Test
    fun `rejeita lista vazia e tamanhos nao positivos`() {
        assertThrows(IllegalArgumentException::class.java) { VirtualFileMap(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { VirtualFileMap(listOf(10L, 0L)) }
        assertThrows(IllegalArgumentException::class.java) { VirtualFileMap(listOf(-1L)) }
    }
}
