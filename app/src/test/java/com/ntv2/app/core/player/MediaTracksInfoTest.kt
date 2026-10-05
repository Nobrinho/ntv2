package com.ntv2.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTracksInfoTest {

    @Test
    fun `video sem nenhuma trilha aceita e nao suportado`() {
        assertTrue(MediaTracksInfo.isVideoUnsupported(listOf(false)))
        assertTrue(MediaTracksInfo.isVideoUnsupported(listOf(false, false)))
    }

    @Test
    fun `basta uma trilha de video aceita`() {
        assertFalse(MediaTracksInfo.isVideoUnsupported(listOf(true)))
        assertFalse(MediaTracksInfo.isVideoUnsupported(listOf(false, true)))
    }

    @Test
    fun `sem trilha de video nao e aviso (so audio ou ainda carregando)`() {
        assertFalse(MediaTracksInfo.isVideoUnsupported(emptyList()))
    }

    @Test
    fun `rotulo do formato`() {
        assertEquals("Dolby Vision 4K", MediaTracksInfo(videoMimeType = "video/dolby-vision", videoHeight = 2160).videoFormatLabel)
        assertEquals("HEVC 1080p", MediaTracksInfo(videoMimeType = "video/hevc", videoHeight = 1080).videoFormatLabel)
        assertEquals("H.264 720p", MediaTracksInfo(videoMimeType = "video/avc", videoHeight = 720).videoFormatLabel)
        assertEquals("AV1", MediaTracksInfo(videoMimeType = "video/av01").videoFormatLabel)
        assertEquals("foo", MediaTracksInfo(videoMimeType = "video/foo").videoFormatLabel)
        assertEquals("4K", MediaTracksInfo(videoHeight = 2160).videoFormatLabel)
        assertNull(MediaTracksInfo().videoFormatLabel)
    }
}
