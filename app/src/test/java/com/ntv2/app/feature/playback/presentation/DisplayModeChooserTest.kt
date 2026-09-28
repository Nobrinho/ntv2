package com.ntv2.app.feature.playback.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeChooserTest {

    // Modos reais da TV do teste (Fire TV AFTKM).
    private val modes = listOf(
        DisplayModeSpec(1, 1920, 1080, 60.000004f),
        DisplayModeSpec(2, 1920, 1080, 59.94f),
        DisplayModeSpec(4, 3840, 2160, 30.000002f),
        DisplayModeSpec(7, 3840, 2160, 24.000002f),
        DisplayModeSpec(8, 3840, 2160, 23.976f),
        DisplayModeSpec(11, 1920, 1080, 23.976f),
        DisplayModeSpec(12, 1920, 1080, 24.000002f)
    )
    private val current1080p60 = modes[1]

    @Test
    fun `video 4K sai em 4K na cadencia do video`() {
        val best = DisplayModeChooser.choose(current1080p60, modes, 3840, 1606, 23.976025f, matchFrameRate = true)
        assertEquals(8, best?.id)
    }

    @Test
    fun `video 4K sem cadencia compativel em 4K cai para casar a cadencia em 1080p`() {
        val onlyUhd30 = modes.filterNot { it.id == 7 || it.id == 8 }
        val best = DisplayModeChooser.choose(current1080p60, onlyUhd30, 3840, 2160, 23.976f, matchFrameRate = true)
        assertEquals(11, best?.id)
    }

    @Test
    fun `VP9 1080p no AFTKM so casa a cadencia`() {
        val best = DisplayModeChooser.choose(current1080p60, modes, 1920, 800, 23.976f, matchFrameRate = true)
        assertEquals(11, best?.id)
    }

    @Test
    fun `video 1080p fora do workaround nao mexe na tela`() {
        assertNull(DisplayModeChooser.choose(current1080p60, modes, 1920, 800, 23.976f, matchFrameRate = false))
    }

    @Test
    fun `ja no modo certo nao troca`() {
        assertNull(DisplayModeChooser.choose(modes[4], modes, 3840, 2160, 23.976f, matchFrameRate = false))
    }

    @Test
    fun `sem cadencia conhecida nao troca`() {
        assertNull(DisplayModeChooser.choose(current1080p60, modes, 3840, 2160, 0f, matchFrameRate = true))
    }
}
