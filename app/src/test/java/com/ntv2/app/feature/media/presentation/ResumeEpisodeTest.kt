package com.ntv2.app.feature.media.presentation

import com.ntv2.app.feature.media.domain.MediaItemSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeEpisodeTest {
    private fun ep(id: String) = MediaItemSummary(
        mediaId = id, channelId = 1L, channelTitle = "c", title = id, caption = null, fileName = null,
        durationSeconds = 1200, thumbnailPath = null, fileId = 0
    )
    private val eps = listOf(ep("t1e1"), ep("t1e2"), ep("t3e1"), ep("t3e2"))

    @Test fun `sem nada visto vai ao primeiro`() =
        assertEquals("t1e1", resumeEpisode(eps, emptyMap())?.mediaId)

    @Test fun `continua no ultimo episodio comecado, mesmo com anteriores nao vistos`() =
        assertEquals("t3e1", resumeEpisode(eps, mapOf("t3e1" to 0.05f))?.mediaId)

    @Test fun `ultimo terminado leva ao seguinte`() =
        assertEquals("t3e2", resumeEpisode(eps, mapOf("t1e1" to 1f, "t3e1" to 0.95f))?.mediaId)

    @Test fun `fim da serie volta ao primeiro nao visto`() =
        assertEquals("t1e2", resumeEpisode(eps, mapOf("t1e1" to 1f, "t3e2" to 1f))?.mediaId)
}

class ResumeEpisodeRecencyTest {
    private fun ep(id: String) = MediaItemSummary(
        mediaId = id, channelId = 1L, channelTitle = "c", title = id, caption = null, fileName = null,
        durationSeconds = 1200, thumbnailPath = null, fileId = 0
    )
    private val eps = listOf(ep("t1e1"), ep("t3e1"), ep("t5e1"))

    @Test fun `visto por ultimo ganha da ordem da serie`() =
        assertEquals("t3e1", resumeEpisode(eps, mapOf("t3e1" to 0.1f, "t5e1" to 0.1f), "t3e1")?.mediaId)
}
