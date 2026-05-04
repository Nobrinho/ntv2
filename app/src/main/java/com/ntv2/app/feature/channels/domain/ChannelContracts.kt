package com.ntv2.app.feature.channels.domain

import kotlinx.coroutines.flow.Flow

data class ChannelSummary(
    val id: Long,
    val title: String,
    val avatarPath: String?
)

interface ChannelRepository {
    suspend fun fetchEligibleChannels(): List<ChannelSummary>
    fun observeSelectedChannelIds(): Flow<Set<Long>>
    fun observeSelectedChannels(): Flow<List<ChannelSummary>>
    suspend fun persistSelectedChannels(selectedChannels: List<ChannelSummary>)
    suspend fun clearSelectedChannels()
}
