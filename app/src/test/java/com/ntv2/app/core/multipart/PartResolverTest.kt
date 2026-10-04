package com.ntv2.app.core.multipart

import com.ntv2.app.core.multipart.PartResolver.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartResolverTest {

    private fun cand(id: Long, name: String, size: Long = 100L) = Candidate(id, fileId = id.toInt(), fileName = name, sizeBytes = size)

    private fun anchor(name: String) = PartName.parse(name)!!

    @Test
    fun `junta as partes do filme ignorando mensagens de outros arquivos`() {
        val candidates = listOf(
            cand(10, "f.mkv.part01of03"),
            cand(11, "f.mkv.part02of03"),
            cand(12, "outro.mkv.part01of02"),
            cand(13, "f.mkv.part03of03"),
            cand(14, "legenda.srt"),
            cand(15, "f.mkv") // arquivo único com o mesmo nome-base não é parte
        )
        val group = PartResolver.resolve(anchor("f.mkv.part02of03"), 11, candidates)
        assertTrue(group.isComplete)
        assertEquals(listOf(10L, 11L, 13L), group.parts.map { it.messageId })
    }

    @Test
    fun `nao depende da ordem de upload`() {
        val candidates = listOf(
            cand(30, "f.mkv.part03of03"),
            cand(10, "f.mkv.part02of03"),
            cand(20, "f.mkv.part01of03")
        )
        val group = PartResolver.resolve(anchor("f.mkv.part03of03"), 30, candidates)
        assertTrue(group.isComplete)
        assertEquals(listOf(20L, 10L, 30L), group.parts.map { it.messageId })
    }

    @Test
    fun `parte faltando aparece em missing`() {
        val group = PartResolver.resolve(
            anchor("f.mkv.part02of04"), 11,
            listOf(cand(10, "f.mkv.part01of04"), cand(11, "f.mkv.part02of04"), cand(13, "f.mkv.part04of04"))
        )
        assertFalse(group.isComplete)
        assertEquals(listOf(3), group.missing)
    }

    @Test
    fun `mensagem repetida conta uma vez`() {
        val group = PartResolver.resolve(
            anchor("f.mkv.part01of02"), 10,
            listOf(cand(10, "f.mkv.part01of02"), cand(10, "f.mkv.part01of02"), cand(11, "f.mkv.part02of02"))
        )
        assertTrue(group.isComplete)
        assertEquals(2, group.parts.size)
    }

    @Test
    fun `reenvio de uma parte vale o mais proximo da ancora`() {
        val candidates = listOf(
            cand(500, "f.mkv.part02of02"), // reenvio distante
            cand(10, "f.mkv.part01of02"),
            cand(11, "f.mkv.part02of02")
        )
        val group = PartResolver.resolve(anchor("f.mkv.part01of02"), 10, candidates)
        assertEquals(11L, group.partsByIndex.getValue(2).messageId)
    }

    @Test
    fun `totais diferentes do mesmo nome sao arquivos diferentes`() {
        val group = PartResolver.resolve(
            anchor("f.mkv.part01of02"), 10,
            listOf(cand(10, "f.mkv.part01of02"), cand(11, "f.mkv.part02of03"), cand(12, "f.mkv.part02of02"))
        )
        assertTrue(group.isComplete)
        assertEquals(listOf(10L, 12L), group.parts.map { it.messageId })
    }

    @Test
    fun `sem candidatos devolve grupo vazio do tamanho esperado`() {
        val group = PartResolver.resolve(anchor("f.mkv.part01of03"), 10, emptyList())
        assertFalse(group.isComplete)
        assertEquals(listOf(1, 2, 3), group.missing)
    }
}
