package com.ntv2.app.core.telegram.tdlib

import com.ntv2.app.core.telegram.tdlib.MessagePageMerge.RawPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMessagesTest {

    @Test
    fun `documento mkv com mime de video e video`() {
        assertTrue(isVideoDocument("Lake Mungo 2009 1080p.mkv", "video/x-matroska"))
        assertTrue(isVideoDocument("filme.bin", "video/mp4"))
    }

    @Test
    fun `mime generico decide pela extensao`() {
        assertTrue(isVideoDocument("Filme.2009.MKV", "application/octet-stream"))
        assertTrue(isVideoDocument("filme.mkv", ""))
        assertFalse(isVideoDocument("legenda.srt", "application/octet-stream"))
    }

    @Test
    fun `mime explicito de outro tipo vence a extensao`() {
        assertFalse(isVideoDocument("pacote.mkv", "application/zip"))
        assertFalse(isVideoDocument("capa.jpg", "image/jpeg"))
    }

    @Test
    fun `older junta as duas buscas em ordem quando ambas acabaram`() {
        val sel = MessagePageMerge.older(
            listOf(RawPage(listOf(90, 70), 0), RawPage(listOf(80, 60), 0)),
            limit = 10
        )
        assertEquals(listOf<Long>(90, 80, 70, 60), sel.ids)
        assertEquals(0L, sel.nextFromMessageId)
    }

    @Test
    fun `older adia o que passa do ponto onde a busca nao esgotada parou`() {
        // Vídeos pararam em 70 (há mais abaixo); documentos acabaram em 40.
        val sel = MessagePageMerge.older(
            listOf(RawPage(listOf(90, 70), 70), RawPage(listOf(80, 40), 0)),
            limit = 10
        )
        // 40 fica para a próxima página: vídeos entre 70 e 40 ainda não foram vistos.
        assertEquals(listOf<Long>(90, 80, 70), sel.ids)
        assertEquals(70L, sel.nextFromMessageId)
    }

    @Test
    fun `older corta no limite e continua do ultimo entregue`() {
        val sel = MessagePageMerge.older(
            listOf(RawPage(listOf(90, 70, 50), 0), RawPage(listOf(80, 60), 0)),
            limit = 3
        )
        assertEquals(listOf<Long>(90, 80, 70), sel.ids)
        assertEquals(70L, sel.nextFromMessageId)
    }

    @Test
    fun `older com busca nao esgotada e vazia usa o cursor dela`() {
        val sel = MessagePageMerge.older(
            listOf(RawPage(emptyList(), 55), RawPage(listOf(80, 40), 0)),
            limit = 10
        )
        assertEquals(listOf<Long>(80), sel.ids)
        assertEquals(55L, sel.nextFromMessageId)
    }

    @Test
    fun `newer pega os mais proximos da ancora sem pular`() {
        // Âncora 50. Vídeos trouxeram até 70 (pode haver mais acima); documentos até 100.
        val sel = MessagePageMerge.newer(
            listOf(RawPage(listOf(70, 60, 50), 0), RawPage(listOf(100, 65, 55), 0)),
            anchor = 50,
            limit = 10
        )
        assertEquals(listOf<Long>(70, 65, 60, 55), sel.ids)
    }

    @Test
    fun `newer respeita o limite a partir da ancora`() {
        val sel = MessagePageMerge.newer(
            listOf(RawPage(listOf(90, 70, 60), 0), RawPage(listOf(95, 80, 55), 0)),
            anchor = 50,
            limit = 2
        )
        assertEquals(listOf<Long>(60, 55), sel.ids)
    }
}
