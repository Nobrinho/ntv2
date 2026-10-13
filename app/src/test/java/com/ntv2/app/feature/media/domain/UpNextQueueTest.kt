package com.ntv2.app.feature.media.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpNextQueueTest {
    private fun ep(id: String) = UpNextEpisode(
        mediaId = id, fileId = id.hashCode(), title = id, channelName = "c",
        durationSeconds = 60, fileName = null, thumbnailPath = null
    )

    @Test
    fun `encadeia a serie inteira a partir do episodio atual`() = runBlocking {
        val queue = UpNextQueue()
        queue.registerChain("e1", listOf("e2", "e3", "e4")) { ep(it) }

        assertEquals("e2", queue.nextAfter("e1")?.mediaId)
        assertEquals("e3", queue.nextAfter("e2")?.mediaId)
        assertEquals("e4", queue.nextAfter("e3")?.mediaId)
        assertNull(queue.nextAfter("e4"))
    }

    @Test
    fun `para no primeiro episodio que nao resolve`() = runBlocking {
        val queue = UpNextQueue()
        queue.registerChain("e1", listOf("e2", "e3", "e4")) { if (it == "e3") null else ep(it) }

        assertEquals("e2", queue.nextAfter("e1")?.mediaId)
        assertNull(queue.nextAfter("e2"))
        assertNull(queue.nextAfter("e3"))
    }

    @Test
    fun `ultimo episodio fica sem proximo mesmo com registro antigo`() = runBlocking {
        val queue = UpNextQueue()
        queue.put("e2", ep("antigo"))
        queue.registerChain("e1", listOf("e2")) { ep(it) }

        assertEquals("e2", queue.nextAfter("e1")?.mediaId)
        assertNull(queue.nextAfter("e2"))
    }
}
