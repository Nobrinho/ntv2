package com.ntv2.app.feature.playback.presentation

import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerMetaLineTest {

    @Test
    fun `junta qualidade e audio selecionado`() {
        val tracks = MediaTracksInfo(
            videoWidth = 1920,
            videoHeight = 800,
            audios = listOf(
                MediaTrackOption("0:0", "Português (AC3 5.1)", isSelected = true),
                MediaTrackOption("0:1", "Original (EAC3)", isSelected = false)
            )
        )
        assertEquals("FHD · Português (AC3 5.1)", playerMetaLine(tracks))
    }

    @Test
    fun `so qualidade quando nao ha audio selecionado`() {
        assertEquals("4K", playerMetaLine(MediaTracksInfo(videoWidth = 3840, videoHeight = 2160)))
    }

    @Test
    fun `nada conhecido vira null`() {
        assertNull(playerMetaLine(MediaTracksInfo()))
    }
}
