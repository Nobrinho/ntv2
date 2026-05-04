package com.ntv2.app.feature.channels.data.datasource

import com.ntv2.app.feature.channels.domain.ChannelSummary

interface TelegramChannelsDataSource {
    suspend fun listEligibleChannels(): List<ChannelSummary>
}
