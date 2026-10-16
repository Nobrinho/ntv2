package com.ntv2.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoResolutionTest {

    @Test
    fun `altura de tela larga cai no formato certo`() {
        listOf(800, 804, 808, 816, 1080).forEach { assertEquals("$it", "FHD", VideoResolution.label(height = it)) }
        listOf(536, 544, 720).forEach { assertEquals("$it", "HD", VideoResolution.label(height = it)) }
        listOf(1600, 1606, 2160).forEach { assertEquals("$it", "4K", VideoResolution.label(height = it)) }
        assertEquals("FHD", VideoResolution.label(height = 1440))
    }

    @Test
    fun `SD inclui 576 PAL e alturas baixas`() {
        listOf(576, 480, 360, 240).forEach { assertEquals("$it", "SD", VideoResolution.label(height = it)) }
    }

    @Test
    fun `valor invalido nao gera selo`() {
        assertNull(VideoResolution.label(height = 133))
        assertNull(VideoResolution.label(height = 0))
        assertNull(VideoResolution.label())
    }

    @Test
    fun `largura conhecida manda mais que a altura`() {
        assertEquals("FHD", VideoResolution.label(width = 1920, height = 800))
        assertEquals("HD", VideoResolution.label(width = 1280, height = 544))
        assertEquals("4K", VideoResolution.label(width = 3840, height = 1606))
        assertEquals("SD", VideoResolution.label(width = 720, height = 576))
    }
}
