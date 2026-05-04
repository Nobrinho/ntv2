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
    val fileId: Int
)

interface MediaRepository {
    suspend fun fetchChannelVideos(channelId: Long, channelTitle: String): List<MediaItemSummary>
}
