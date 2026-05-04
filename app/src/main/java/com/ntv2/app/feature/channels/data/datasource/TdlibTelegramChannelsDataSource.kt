package com.ntv2.app.feature.channels.data.datasource

import com.ntv2.app.core.telegram.channels.TdlibChannelsGateway
import com.ntv2.app.core.telegram.channels.TelegramChatType
import com.ntv2.app.feature.channels.domain.ChannelSummary

class TdlibTelegramChannelsDataSource(
    private val tdlibChannelsGateway: TdlibChannelsGateway
) : TelegramChannelsDataSource {
    override suspend fun listEligibleChannels(): List<ChannelSummary> {
        return tdlibChannelsGateway
            .listChats(limit = 500)
            .asSequence()
            .filter { chat ->
                chat.canReadHistory &&
                    (chat.type == TelegramChatType.Channel || chat.type == TelegramChatType.Supergroup)
            }
            .map { chat ->
                ChannelSummary(
                    id = chat.id,
                    title = chat.title,
                    avatarPath = chat.avatarPath
                )
            }
            .toList()
    }
}
