package com.ntv2.app.feature.media.presentation.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewPolicyTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun `extrai o id do link watch`() {
        assertEquals(id, YouTubeIds.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extrai o id do link mobile com outros parametros`() {
        assertEquals(id, YouTubeIds.videoId("https://m.youtube.com/watch?feature=x&v=dQw4w9WgXcQ&t=10"))
    }

    @Test
    fun `extrai o id do link curto`() {
        assertEquals(id, YouTubeIds.videoId("https://youtu.be/dQw4w9WgXcQ?si=a"))
    }

    @Test
    fun `extrai o id do link embed`() {
        assertEquals(id, YouTubeIds.videoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `devolve null para links invalidos`() {
        assertNull(YouTubeIds.videoId("https://vimeo.com/123"))
        assertNull(YouTubeIds.videoId("https://www.youtube.com/watch?v="))
        assertNull(YouTubeIds.videoId("https://youtu.be/curto"))
        assertNull(YouTubeIds.videoId(null))
        assertNull(YouTubeIds.videoId("não é url"))
    }

    private fun policy(
        videoId: String? = id,
        userEnabled: Boolean = true,
        remoteEnabled: Boolean = true,
        showCovers: Boolean = true,
        dataSaver: Boolean = false,
        failedIds: Set<String> = emptySet(),
    ) = shouldPreviewTrailer(videoId, userEnabled, remoteEnabled, showCovers, dataSaver, failedIds)

    @Test
    fun `tudo ligado exibe a previa`() {
        assertTrue(policy())
    }

    @Test
    fun `cada condicao desligada sozinha desliga a previa`() {
        assertFalse(policy(videoId = null))
        assertFalse(policy(userEnabled = false))
        assertFalse(policy(remoteEnabled = false))
        assertFalse(policy(showCovers = false))
        assertFalse(policy(dataSaver = true))
        assertFalse(policy(failedIds = setOf(id)))
    }

    @Test
    fun `falhas registradas ficam no conjunto`() {
        TrailerFailures.add("abcdefghijk")
        assertTrue("abcdefghijk" in TrailerFailures.ids)
    }
}
