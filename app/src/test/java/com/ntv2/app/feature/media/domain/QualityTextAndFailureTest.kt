package com.ntv2.app.feature.media.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityTextAndFailureTest {

    @Test
    fun `altura vem do numero seguido de p`() {
        assertEquals(2160, QualityText.heightOf("2160p, BluRay, Remux, HDR"))
        assertEquals(1080, QualityText.heightOf("1080p WEB-DL"))
        assertEquals(720, QualityText.heightOf("720P"))
        assertEquals(480, QualityText.heightOf("480p"))
    }

    @Test
    fun `4k e uhd valem 2160 e fhd vale 1080`() {
        assertEquals(2160, QualityText.heightOf("4K HDR"))
        assertEquals(2160, QualityText.heightOf("UHD BluRay"))
        assertEquals(1080, QualityText.heightOf("Full HD"))
        assertEquals(1080, QualityText.heightOf("FHD"))
    }

    @Test
    fun `o numero tem prioridade sobre as palavras`() {
        assertEquals(1080, QualityText.heightOf("1080p UHD upscale"))
    }

    @Test
    fun `texto sem resolucao devolve zero`() {
        assertEquals(0, QualityText.heightOf(null))
        assertEquals(0, QualityText.heightOf(""))
        assertEquals(0, QualityText.heightOf("BluRay, Remux, HDR"))
        assertEquals(0, QualityText.heightOf("Qualidade não informada"))
        assertEquals(0, QualityText.heightOf("5p"))                              // não é resolução
        assertEquals(0, QualityText.heightOf("999999p"))
    }

    @Test
    fun `nao confunde palavras que contem 4k ou numero com p`() {
        assertEquals(0, QualityText.heightOf("Remux 24kbps"))
        assertEquals(0, QualityText.heightOf("DUAL 5.1 Atmos"))
    }

    // ---- mensagem quando as partes não estão todas no canal

    @Test
    fun `partes ausentes viram texto claro`() {
        val texto = MultiPartPrepareResult.Incomplete(missing = listOf(3, 5), total = 12).failureText()
        assertEquals("Faltam as partes 3 e 5 de 12: este filme ainda está sendo enviado ao canal.", texto)
    }

    @Test
    fun `uma parte ausente e muitas partes ausentes`() {
        assertEquals(
            "Falta a parte 12 de 12: este filme ainda está sendo enviado ao canal.",
            MultiPartPrepareResult.Incomplete(listOf(12), 12).failureText()
        )
        assertEquals(
            "Faltam 8 partes de 12: este filme ainda está sendo enviado ao canal.",
            MultiPartPrepareResult.Incomplete((1..8).toList(), 12).failureText()
        )
    }

    @Test
    fun `falha ao localizar e pronto`() {
        assertEquals(
            "Não consegui localizar as partes deste filme no canal. Confira a conexão e tente de novo.",
            MultiPartPrepareResult.Failed.failureText()
        )
        assertNull(MultiPartPrepareResult.Ready.failureText())
    }
}
