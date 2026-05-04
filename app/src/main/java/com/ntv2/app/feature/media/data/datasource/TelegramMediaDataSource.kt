package com.ntv2.app.feature.media.data.datasource

import com.ntv2.app.feature.media.domain.MediaItemSummary

interface TelegramMediaDataSource {
    suspend fun listChannelVideos(channelId: Long, channelTitle: String): List<MediaItemSummary>
}
