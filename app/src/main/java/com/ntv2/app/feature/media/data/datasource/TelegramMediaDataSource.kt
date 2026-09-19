package com.ntv2.app.feature.media.data.datasource

import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.MediaPage

interface TelegramMediaDataSource {
    suspend fun listChannelVideos(channelId: Long, channelTitle: String, fromMessageId: Long, limit: Int): MediaPage
    suspend fun searchChannelVideos(channelId: Long, channelTitle: String, query: String, fromMessageId: Long, limit: Int): MediaPage
    suspend fun listNewerChannelVideos(channelId: Long, channelTitle: String, newerThanMessageId: Long, limit: Int): MediaPage =
        MediaPage(emptyList(), 0L)
    suspend fun getVideoByMessage(channelId: Long, channelTitle: String, messageId: Long): MediaItemSummary?
}
