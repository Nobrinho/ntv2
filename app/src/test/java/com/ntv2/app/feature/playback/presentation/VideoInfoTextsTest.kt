package com.ntv2.app.feature.playback.presentation

import com.ntv2.app.core.player.MediaTrackOption
import com.ntv2.app.core.player.MediaTracksInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoInfoTextsTest {

    @Test
    fun `resolucao pela largura em tela larga`() {
        assertEquals("2160p (3840x2160)", VideoInfoTexts.resolution(3840, 2160))
        assertEquals("2160p (3840x1600)", VideoInfoTexts.resolution(3840, 1600))      // cinemascope
        assertEquals("1080p (1920x800)", VideoInfoTexts.resolution(1920, 800))
        assertEquals("720p (1280x720)", VideoInfoTexts.resolution(1280, 720))
        assertEquals("480p (854x480)", VideoInfoTexts.resolution(854, 480))
        assertNull(VideoInfoTexts.resolution(0, 0))
    }

    @Test
    fun `taxa de quadros com virgula e sem zeros sobrando`() {
        assertEquals("23,976 fps", VideoInfoTexts.frameRate(23.976f))
        assertEquals("24 fps", VideoInfoTexts.frameRate(24f))
        assertEquals("59,94 fps", VideoInfoTexts.frameRate(59.94f))
        assertNull(VideoInfoTexts.frameRate(0f))
    }

    @Test
    fun `linha de video completa de um HDR10`() {
        val tracks = MediaTracksInfo(
            videoWidth = 3840, videoHeight = 2160, videoFrameRate = 23.976f, videoMimeType = "video/hevc",
            videoHdr = "HDR10", videoBitDepth = 10
        )
        assertEquals("2160p (3840x2160) · HEVC 10-bit · HDR10 · 23,976 fps", VideoInfoTexts.videoLine(tracks))
    }

    @Test
    fun `dolby vision 7 tocando a camada base aparece na linha`() {
        val tracks = MediaTracksInfo(
            videoWidth = 3840, videoHeight = 2160, videoFrameRate = 24f, videoMimeType = "video/hevc",
            videoBitDepth = 10, dolbyVisionBaseLayerProfile = 7
        )
        assertEquals("2160p (3840x2160) · HEVC 10-bit · Dolby Vision 7 (camada base) · 24 fps", VideoInfoTexts.videoLine(tracks))
    }

    @Test
    fun `video comum de 8 bits nao diz bits`() {
        val tracks = MediaTracksInfo(videoWidth = 1920, videoHeight = 1080, videoMimeType = "video/avc", videoBitDepth = 8)
        assertEquals("1080p (1920x1080) · H.264", VideoInfoTexts.videoLine(tracks))
    }

    @Test
    fun `sem dados nao ha linha`() {
        assertNull(VideoInfoTexts.videoLine(MediaTracksInfo()))
        assertNull(VideoInfoTexts.audioLine(MediaTracksInfo()))
    }

    @Test
    fun `linha de audio junta formato canais e a faixa escolhida`() {
        val tracks = MediaTracksInfo(
            audioMimeType = "audio/ac3", audioChannels = 6,
            audios = listOf(MediaTrackOption("0:0", "Inglês", false), MediaTrackOption("0:1", "Português (BR)", true))
        )
        assertEquals("AC3 5.1 · Português (BR)", VideoInfoTexts.audioLine(tracks))
    }

    @Test
    fun `codecs de audio conhecidos e desconhecidos`() {
        fun linha(mime: String, canais: Int) = VideoInfoTexts.audioLine(MediaTracksInfo(audioMimeType = mime, audioChannels = canais))
        assertEquals("TrueHD 7.1", linha("audio/true-hd", 8))
        assertEquals("EAC3 5.1", linha("audio/eac3", 6))
        assertEquals("AAC 2.0", linha("audio/mp4a-latm", 2))
        assertEquals("XYZ 3ch", linha("audio/xyz", 3))
    }
}
