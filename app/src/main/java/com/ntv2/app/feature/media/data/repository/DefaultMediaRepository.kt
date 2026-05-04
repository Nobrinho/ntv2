package com.ntv2.app.feature.media.data.repository

import com.ntv2.app.feature.media.data.datasource.TelegramMediaDataSource
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.MediaRepository

class DefaultMediaRepository(
    private val mediaDataSource: TelegramMediaDataSource
) : MediaRepository {
    override suspend fun fetchChannelVideos(channelId: Long, channelTitle: String): List<MediaItemSummary> {
        return mediaDataSource.listChannelVideos(channelId = channelId, channelTitle = channelTitle)
    }
}
