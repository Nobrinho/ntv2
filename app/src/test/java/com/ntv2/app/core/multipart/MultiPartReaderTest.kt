package com.ntv2.app.core.multipart

import com.ntv2.app.core.multipart.MultiPartCursor.Companion.UNSET
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class MultiPartReaderTest {

    // Filme lógico em partes de tamanhos dados; o conteúdo é determinístico por posição global.
    private class Fixture(sizes: List<Long>, private val brokenPart: Int? = null) {
        val map = VirtualFileMap(sizes)
        val content = ByteArray(map.totalSize.toInt()) { (it * 31 + 7).toByte() }
        val opened = mutableListOf<Triple<Int, Long, Long>>()
        val prefetched = mutableListOf<Int>()
        val discarded = mutableListOf<Int>()
        var closedStreams = 0

        fun reader(position: Long, length: Long = UNSET, ahead: Long = 20L) = MultiPartReader(
            map = map,
            startPosition = position,
            length = length,
            prefetchAheadBytes = ahead,
            openPart = { part, offset, partLength ->
                opened += Triple(part, offset, partLength)
                stream(part, offset, partLength)
            },
            prefetchPart = { prefetched += it },
            discardPart = { discarded += it }
        )

        // Imita o data source de arquivo parcial: lê até o fim da parte (ou até o limite pedido).
        private fun stream(part: Int, offset: Long, partLength: Long): PartStream {
            val start = map.partStart(part) + offset
            val end = if (partLength == UNSET) map.partEnd(part) else start + partLength
            val limit = if (part == brokenPart) end - 5 else end // parte "mentirosa": acaba antes
            var cursor = start
            return object : PartStream {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (cursor >= limit) return MultiPartReader.END
                    val n = minOf(length.toLong(), limit - cursor).toInt()
                    System.arraycopy(content, cursor.toInt(), buffer, offset, n)
                    cursor += n
                    return n
                }

                override fun close() {
                    closedStreams++
                }
            }
        }
    }

    private fun MultiPartReader.readAll(bufferSize: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n == MultiPartReader.END) return out.toByteArray()
            out.write(buffer, 0, n)
        }
    }

    private val threeParts = listOf(100L, 50L, 250L)

    @Test
    fun `le o filme inteiro igual ao original atravessando as emendas`() {
        for (bufferSize in listOf(1, 7, 64, 100, 1_000)) {
            val f = Fixture(threeParts)
            assertArrayEquals("buffer=$bufferSize", f.content, f.reader(0).readAll(bufferSize))
        }
    }

    @Test
    fun `comeca no meio de uma parte e segue ate o fim`() {
        val f = Fixture(threeParts)
        val lido = f.reader(120).readAll(33)
        assertArrayEquals(f.content.copyOfRange(120, 400), lido)
        assertEquals(Triple(1, 20L, UNSET), f.opened.first())
    }

    @Test
    fun `comeca exatamente no inicio de uma parte`() {
        val f = Fixture(threeParts)
        val lido = f.reader(150).readAll(64)
        assertArrayEquals(f.content.copyOfRange(150, 400), lido)
        assertEquals(listOf(Triple(2, 0L, UNSET)), f.opened)
    }

    @Test
    fun `faixa fixa que cruza a emenda le so o pedido`() {
        val f = Fixture(threeParts)
        val reader = f.reader(90, length = 80)
        assertEquals(80L, reader.remaining)
        val lido = reader.readAll(16)
        assertArrayEquals(f.content.copyOfRange(90, 170), lido)
        // parte 0: pede só até o fim dela; parte 1: inteira; parte 2: o que sobrou
        assertEquals(
            listOf(Triple(0, 90L, 10L), Triple(1, 0L, 50L), Triple(2, 0L, 20L)),
            f.opened
        )
    }

    @Test
    fun `faixa fixa dentro de uma parte nao toca na seguinte`() {
        val f = Fixture(threeParts)
        f.reader(10, length = 30).readAll(8)
        assertEquals(listOf(Triple(0, 10L, 30L)), f.opened)
        assertTrue(f.prefetched.isEmpty())
    }

    @Test
    fun `nunca le alem do fim do filme`() {
        val f = Fixture(threeParts)
        val reader = f.reader(390, length = 1_000)
        assertArrayEquals(f.content.copyOfRange(390, 400), reader.readAll(64))
        assertEquals(MultiPartReader.END, reader.read(ByteArray(8), 0, 8))
    }

    @Test
    fun `leitura nunca devolve bytes de duas partes de uma vez`() {
        val f = Fixture(threeParts)
        val reader = f.reader(95)
        val buffer = ByteArray(1_000)
        assertEquals(5, reader.read(buffer, 0, buffer.size)) // só até o fim da parte 0
        assertEquals(50, reader.read(buffer, 0, buffer.size)) // a parte 1 inteira
    }

    @Test
    fun `pre-baixa cada parte seguinte uma unica vez quando se aproxima do fim`() {
        val f = Fixture(threeParts)
        f.reader(0, ahead = 30).readAll(10)
        assertEquals(listOf(1, 2), f.prefetched)
    }

    @Test
    fun `nao pre-baixa nada longe do fim da parte`() {
        val f = Fixture(listOf(1_000L, 1_000L))
        val reader = f.reader(0, ahead = 100)
        reader.read(ByteArray(10), 0, 10)
        assertTrue(f.prefetched.isEmpty())
    }

    @Test
    fun `descarta a parte de dois passos atras a cada emenda`() {
        val f = Fixture(listOf(10L, 10L, 10L, 10L, 10L))
        f.reader(0).readAll(4)
        // emendas 0->1 (nada), 1->2 (0), 2->3 (1), 3->4 (2)
        assertEquals(listOf(0, 1, 2), f.discarded)
    }

    @Test
    fun `comecar no meio descarta so depois da primeira emenda`() {
        val f = Fixture(listOf(10L, 10L, 10L, 10L, 10L))
        f.reader(35).readAll(4)
        // só as emendas 3->4 acontecem a partir daqui: descarta a parte 2
        assertEquals(listOf(2), f.discarded)
    }

    @Test
    fun `parte que acaba antes do esperado e erro e nao pula dados`() {
        val f = Fixture(threeParts, brokenPart = 0)
        val reader = f.reader(0)
        val erro = assertThrows(PartUnavailableException::class.java) { reader.readAll(16) }
        assertEquals(1, erro.part)
        assertEquals(3, erro.total)
        assertTrue(erro.message!!.contains("Parte 1 de 3 indisponível"))
    }

    @Test
    fun `fechar fecha a parte aberta`() {
        val f = Fixture(threeParts)
        val reader = f.reader(0)
        reader.close()
        assertEquals(1, f.closedStreams)
    }

    @Test
    fun `fecha cada parte ao trocar`() {
        val f = Fixture(listOf(10L, 10L, 10L))
        val reader = f.reader(0)
        reader.readAll(4)
        reader.close()
        assertEquals(3, f.closedStreams)
    }

    @Test
    fun `posicao fora do filme nao abre nada`() {
        val f = Fixture(threeParts)
        assertThrows(IllegalArgumentException::class.java) { f.reader(400) }
        assertTrue(f.opened.isEmpty())
    }

    @Test
    fun `leitura de zero bytes devolve zero`() {
        val f = Fixture(threeParts)
        val reader = f.reader(0)
        assertEquals(0, reader.read(ByteArray(4), 0, 0))
    }
}
