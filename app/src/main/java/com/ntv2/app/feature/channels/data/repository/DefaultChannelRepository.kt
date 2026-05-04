package com.ntv2.app.feature.channels.data.repository

import com.ntv2.app.core.database.dao.SelectedChannelDao
import com.ntv2.app.core.database.entity.SelectedChannelEntity
import com.ntv2.app.feature.channels.data.datasource.TelegramChannelsDataSource
import com.ntv2.app.feature.channels.domain.ChannelRepository
import com.ntv2.app.feature.channels.domain.ChannelSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultChannelRepository(
    private val telegramChannelsDataSource: TelegramChannelsDataSource,
    private val selectedChannelDao: SelectedChannelDao
) : ChannelRepository {

    override suspend fun fetchEligibleChannels(): List<ChannelSummary> {
        return telegramChannelsDataSource.listEligibleChannels()
    }

    override fun observeSelectedChannelIds(): Flow<Set<Long>> {
        return selectedChannelDao.observeAll().map { items ->
            items.map { it.channelId }.toSet()
        }
    }

    override fun observeSelectedChannels(): Flow<List<ChannelSummary>> {
        return selectedChannelDao.observeAll().map { items ->
            items.map { entity ->
                ChannelSummary(
                    id = entity.channelId,
                    title = entity.title,
                    avatarPath = null
                )
            }
        }
    }

    override suspend fun persistSelectedChannels(selectedChannels: List<ChannelSummary>) {
        selectedChannelDao.clearAll()
        selectedChannelDao.upsertAll(
            selectedChannels.map { channel ->
                SelectedChannelEntity(
                    channelId = channel.id,
                    title = channel.title
                )
            }
        )
    }

    override suspend fun clearSelectedChannels() {
        selectedChannelDao.clearAll()
    }
}
