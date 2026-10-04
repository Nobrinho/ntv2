package com.ntv2.app.core.multipart

import com.ntv2.app.core.multipart.MultiPartCursor.Companion.UNSET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPartCursorTest {

    // Partes: [0,100) [100,150) [150,400)
    private val map = VirtualFileMap(listOf(100L, 50L, 250L))

    @Test
    fun `comeca na parte e no offset certos`() {
        val cursor = MultiPartCursor(map, 120)
        assertEquals(1, cursor.part)
        assertEquals(20L, cursor.offsetInPart)
        assertEquals(30L, cursor.bytesToPartEnd)
    }

    @Test
    fun `posicao no inicio exato de uma parte pertence a ela`() {
        val cursor = MultiPartCursor(map, 100)
        assertEquals(1, cursor.part)
        assertEquals(0L, cursor.offsetInPart)
    }

    @Test
    fun `posicao fora do arquivo e rejeitada`() {
        assertThrows(IllegalArgumentException::class.java) { MultiPartCursor(map, 400) }
        assertThrows(IllegalArgumentException::class.java) { MultiPartCursor(map, -1) }
    }

    @Test
    fun `faixa aberta pede a parte como aberta`() {
        val cursor = MultiPartCursor(map, 10)
        assertEquals(UNSET, cursor.lengthForCurrentPart())
        assertEquals(UNSET, cursor.remaining)
    }

    @Test
    fun `faixa fixa dentro da parte pede so o que falta`() {
        val cursor = MultiPartCursor(map, 10, length = 30)
        assertEquals(30L, cursor.lengthForCurrentPart())
    }

    @Test
    fun `faixa fixa que cruza a emenda pede so ate o fim da parte`() {
        val cursor = MultiPartCursor(map, 90, length = 80)
        assertEquals(10L, cursor.lengthForCurrentPart())
        assertEquals(80L, cursor.remaining)
    }

    @Test
    fun `faixa fixa maior que o arquivo e cortada no fim`() {
        val cursor = MultiPartCursor(map, 350, length = 1_000)
        assertEquals(50L, cursor.remaining)
    }

    @Test
    fun `consumir avanca a posicao e reduz o que falta`() {
        val cursor = MultiPartCursor(map, 90, length = 80)
        cursor.consume(10)
        assertEquals(100L, cursor.position)
        assertEquals(70L, cursor.remaining)
        assertEquals(0L, cursor.bytesToPartEnd)
    }

    @Test
    fun `nao deixa consumir alem do fim da parte`() {
        val cursor = MultiPartCursor(map, 90)
        assertThrows(IllegalArgumentException::class.java) { cursor.consume(11) }
    }

    @Test
    fun `so pode avancar no fim exato da parte`() {
        val cursor = MultiPartCursor(map, 90)
        assertFalse(cursor.canAdvance)
        cursor.consume(10)
        assertTrue(cursor.canAdvance)
    }

    @Test
    fun `faixa fixa que acaba na emenda esta concluida e nao avanca`() {
        val cursor = MultiPartCursor(map, 90, length = 10)
        cursor.consume(10)
        assertTrue(cursor.isDone)
        assertFalse(cursor.canAdvance)
    }

    @Test
    fun `fim da ultima parte conclui a leitura`() {
        val cursor = MultiPartCursor(map, 390)
        cursor.consume(10)
        assertTrue(cursor.isDone)
        assertTrue(cursor.isLastPart)
        assertFalse(cursor.canAdvance)
    }

    @Test
    fun `avancar devolve a parte de dois passos atras para descartar`() {
        val cursor = MultiPartCursor(map, 0)
        cursor.consume(100)
        assertNull(cursor.advance()) // 0 -> 1: nada dois passos atras
        cursor.consume(50)
        assertEquals(0, cursor.advance()) // 1 -> 2: a parte 0 pode sair
        assertEquals(2, cursor.part)
        assertEquals(0L, cursor.offsetInPart)
    }

    @Test
    fun `avancar fora da emenda e erro`() {
        val cursor = MultiPartCursor(map, 10)
        assertThrows(IllegalStateException::class.java) { cursor.advance() }
    }

    @Test
    fun `pre-baixa a proxima parte quando faltam ate N bytes`() {
        val cursor = MultiPartCursor(map, 0)
        assertNull(cursor.partToPrefetch(30)) // faltam 100
        cursor.consume(70)
        assertEquals(1, cursor.partToPrefetch(30)) // faltam exatamente 30
        cursor.consume(20)
        assertEquals(1, cursor.partToPrefetch(30))
    }

    @Test
    fun `na ultima parte nao ha o que pre-baixar`() {
        val cursor = MultiPartCursor(map, 390)
        assertNull(cursor.partToPrefetch(1_000))
    }

    @Test
    fun `faixa fixa terminada nao pre-baixa`() {
        val cursor = MultiPartCursor(map, 90, length = 10)
        cursor.consume(10)
        assertNull(cursor.partToPrefetch(1_000))
    }
}
