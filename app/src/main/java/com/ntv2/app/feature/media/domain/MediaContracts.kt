package com.ntv2.app.feature.media.domain

import kotlinx.coroutines.flow.Flow

data class MediaItemSummary(
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val caption: String?,
    val fileName: String?,
    val durationSeconds: Int,
    val thumbnailPath: String?,
    val fileId: Int,
    val width: Int = 0,
    val height: Int = 0
)

data class MediaPage(
    val items: List<MediaItemSummary>,
    // Cursor para a próxima página (fromMessageId do TDLib). 0 => não há mais resultados.
    val nextCursor: Long
)

interface MediaRepository {
    suspend fun fetchChannelVideos(
        channelId: Long,
        channelTitle: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage

    suspend fun searchChannelVideos(
        channelId: Long,
        channelTitle: String,
        query: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage
}
