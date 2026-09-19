package com.ntv2.app.feature.media.data.repository

import com.ntv2.app.feature.media.data.datasource.TelegramMediaDataSource
import com.ntv2.app.feature.media.domain.MediaPage
import com.ntv2.app.feature.media.domain.MediaRepository

class DefaultMediaRepository(
    private val mediaDataSource: TelegramMediaDataSource
) : MediaRepository {
    override suspend fun fetchChannelVideos(
        channelId: Long,
        channelTitle: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage {
        return mediaDataSource.listChannelVideos(
            channelId = channelId,
            channelTitle = channelTitle,
            fromMessageId = fromMessageId,
            limit = limit
        )
    }

    override suspend fun searchChannelVideos(
        channelId: Long,
        channelTitle: String,
        query: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage {
        return mediaDataSource.searchChannelVideos(
            channelId = channelId,
            channelTitle = channelTitle,
            query = query,
            fromMessageId = fromMessageId,
            limit = limit
        )
    }

    override suspend fun fetchNewerChannelVideos(
        channelId: Long,
        channelTitle: String,
        newerThanMessageId: Long,
        limit: Int
    ): MediaPage = mediaDataSource.listNewerChannelVideos(channelId, channelTitle, newerThanMessageId, limit)

    override suspend fun getVideoByMessage(
        channelId: Long,
        channelTitle: String,
        messageId: Long
    ) = mediaDataSource.getVideoByMessage(channelId, channelTitle, messageId)
}
