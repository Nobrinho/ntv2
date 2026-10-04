package com.ntv2.app.core.multipart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartGrouperTest {

    private data class Item(val id: Int, val name: String)

    private fun group(vararg items: Item) = PartGrouper.group(items.toList()) { it.name }

    @Test
    fun `agrupa as partes de um arquivo em ordem de indice`() {
        val result = group(
            Item(3, "f.mkv.part03of03"),
            Item(1, "f.mkv.part01of03"),
            Item(2, "f.mkv.part02of03")
        )
        val g = result.groups.single()
        assertEquals("f.mkv", g.baseName)
        assertEquals(3, g.total)
        assertTrue(g.isComplete)
        assertEquals(listOf(1, 2, 3), g.parts.map { it.id })
        assertEquals(1, g.first?.id)
        assertTrue(result.standalone.isEmpty())
    }

    @Test
    fun `itens que nao sao partes ficam avulsos na ordem original`() {
        val result = group(
            Item(1, "a.mp4"),
            Item(2, "f.mkv.part01of02"),
            Item(3, "b.mkv"),
            Item(4, "f.mkv.part02of02")
        )
        assertEquals(listOf(1, 3), result.standalone.map { it.id })
        assertEquals(1, result.groups.size)
    }

    @Test
    fun `grupo incompleto informa as partes que faltam`() {
        val g = group(
            Item(1, "f.mkv.part01of05"),
            Item(4, "f.mkv.part04of05")
        ).groups.single()
        assertFalse(g.isComplete)
        assertEquals(listOf(2, 3, 5), g.missing)
        assertEquals(listOf(1, 4), g.parts.map { it.id })
    }

    @Test
    fun `sem a parte 1 nao ha first`() {
        val g = group(Item(2, "f.mkv.part02of02")).groups.single()
        assertNull(g.first)
    }

    @Test
    fun `mesmo nome com totais diferentes sao grupos diferentes`() {
        val result = group(
            Item(1, "f.mkv.part01of02"),
            Item(2, "f.mkv.part01of03")
        )
        assertEquals(listOf(2, 3), result.groups.map { it.total })
    }

    @Test
    fun `arquivos diferentes ficam em grupos diferentes na ordem da primeira aparicao`() {
        val result = group(
            Item(1, "b.mkv.part01of02"),
            Item(2, "a.mkv.part01of02"),
            Item(3, "b.mkv.part02of02")
        )
        assertEquals(listOf("b.mkv", "a.mkv"), result.groups.map { it.baseName })
        assertEquals(listOf(1, 3), result.groups[0].parts.map { it.id })
    }

    @Test
    fun `indice repetido vale o primeiro da lista`() {
        val g = group(
            Item(10, "f.mkv.part01of02"),
            Item(5, "f.mkv.part01of02"),
            Item(11, "f.mkv.part02of02")
        ).groups.single()
        assertEquals(10, g.first?.id)
        assertTrue(g.isComplete)
    }

    @Test
    fun `lista vazia nao gera nada`() {
        val result = PartGrouper.group(emptyList<Item>()) { it.name }
        assertTrue(result.groups.isEmpty())
        assertTrue(result.standalone.isEmpty())
    }
}
