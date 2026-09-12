package com.ntv2.app.core.telegram.media

import kotlinx.coroutines.delay

data class TelegramVideoMessage(
    val mediaId: String,
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val caption: String?,
    val fileName: String?,
    val durationSeconds: Int,
    val thumbnailPath: String?,
    val fileId: Int,
    val width: Int = 0,
    val height: Int = 0,
    /** Pôster (foto do post) baixado; capa preferida sobre o frame do vídeo. */
    val posterPath: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null
)

data class TelegramVideoPage(
    val videos: List<TelegramVideoMessage>,
    // fromMessageId da próxima página; 0 => fim.
    val nextFromMessageId: Long
)

interface TdlibMediaGateway {
    suspend fun listVideoMessages(chatId: Long, fromMessageId: Long = 0L, limit: Int = 40): TelegramVideoPage
    suspend fun searchVideoMessages(chatId: Long, query: String, fromMessageId: Long = 0L, limit: Int = 40): TelegramVideoPage
}

class FakeTdlibMediaGateway : TdlibMediaGateway {
    override suspend fun listVideoMessages(chatId: Long, fromMessageId: Long, limit: Int): TelegramVideoPage {
        delay(180)
        val base = (chatId * 1000).toInt()
        val items = listOf(
            TelegramVideoMessage(
                mediaId = "${chatId}_1",
                chatId = chatId,
                messageId = base + 1L,
                title = "Documentário Completo ${chatId}",
                caption = "Versão remasterizada",
                fileName = "documentario_${chatId}.mp4",
                durationSeconds = 60 * 42,
                thumbnailPath = null,
                fileId = base + 1
            ),
            TelegramVideoMessage(
                mediaId = "${chatId}_2",
                chatId = chatId,
                messageId = base + 2L,
                title = "Episódio Especial ${chatId}",
                caption = "Temporada 1",
                fileName = "episodio_especial_${chatId}.mkv",
                durationSeconds = 60 * 28,
                thumbnailPath = null,
                fileId = base + 2
            ),
            TelegramVideoMessage(
                mediaId = "${chatId}_3",
                chatId = chatId,
                messageId = base + 3L,
                title = "Clipe Curto ${chatId}",
                caption = "Conteúdo curto",
                fileName = "clip_${chatId}.mp4",
                durationSeconds = 60 * 3,
                thumbnailPath = null,
                fileId = base + 3
            )
        )
        // Fake: página única, sem cursor.
        return TelegramVideoPage(videos = items.take(limit), nextFromMessageId = 0L)
    }

    override suspend fun searchVideoMessages(chatId: Long, query: String, fromMessageId: Long, limit: Int): TelegramVideoPage {
        val q = query.trim().lowercase()
        val page = listVideoMessages(chatId, fromMessageId, limit)
        if (q.isBlank()) return page
        return page.copy(
            videos = page.videos.filter { video ->
                video.title.lowercase().contains(q) ||
                    (video.caption?.lowercase()?.contains(q) == true) ||
                    (video.fileName?.lowercase()?.contains(q) == true)
            }
        )
    }
}
