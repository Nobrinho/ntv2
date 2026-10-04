package com.ntv2.app.core.multipart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartNameTest {

    @Test
    fun `interpreta o nome canonico`() {
        assertEquals(PartName("Filme.2020.mkv", 3, 11), PartName.parse("Filme.2020.mkv.part03of11"))
        assertEquals(PartName("a.mp4", 1, 2), PartName.parse("a.mp4.part01of02"))
    }

    @Test
    fun `aceita sem zeros, caixa mista e espacos nas pontas`() {
        assertEquals(PartName("a.mkv", 1, 3), PartName.parse("a.mkv.part1of3"))
        assertEquals(PartName("a.mkv", 2, 3), PartName.parse("  a.mkv.PART02OF03 "))
    }

    @Test
    fun `rejeita o que nao e parte`() {
        assertNull(PartName.parse(null))
        assertNull(PartName.parse(""))
        assertNull(PartName.parse("Filme.mkv"))
        assertNull(PartName.parse(".part01of02"))
        assertNull(PartName.parse("Filme.mkv.part01"))
        assertNull(PartName.parse("Filme.mkv.001"))
    }

    @Test
    fun `rejeita indice e total invalidos`() {
        assertNull(PartName.parse("a.mkv.part00of05"))
        assertNull(PartName.parse("a.mkv.part06of05"))
        assertNull(PartName.parse("a.mkv.part01of01")) // um arquivo só não é dividido
        assertNull(PartName.parse("a.mkv.part01of00"))
        assertNull(PartName.parse("a.mkv.part01of99999999999999999999"))
    }

    @Test
    fun `usa o ultimo sufixo part quando o nome tem outro no meio`() {
        assertEquals(PartName("x.part01of02.mkv", 2, 4), PartName.parse("x.part01of02.mkv.part02of04"))
    }

    @Test
    fun `format usa no minimo dois digitos e cresce com o total`() {
        assertEquals("a.mkv.part01of09", PartName.format("a.mkv", 1, 9))
        assertEquals("a.mkv.part03of11", PartName.format("a.mkv", 3, 11))
        assertEquals("a.mkv.part007of120", PartName.format("a.mkv", 7, 120))
    }

    @Test
    fun `format e parse sao inversos`() {
        for (total in listOf(2, 9, 10, 99, 100, 250)) {
            for (index in listOf(1, total / 2 + 1, total)) {
                val name = PartName("Filme (2020) [4K].mkv", index, total)
                assertEquals(name, PartName.parse(name.fileName))
            }
        }
    }
}
