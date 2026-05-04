package com.ntv2.app.core.telegram.channels

import kotlinx.coroutines.delay

enum class TelegramChatType {
    Private,
    Group,
    Supergroup,
    Channel,
    Unknown
}

data class TelegramChatSummary(
    val id: Long,
    val title: String,
    val type: TelegramChatType,
    val avatarPath: String? = null,
    val canReadHistory: Boolean = true
)

interface TdlibChannelsGateway {
    suspend fun listChats(limit: Int = 200): List<TelegramChatSummary>
}

class FakeTdlibChannelsGateway : TdlibChannelsGateway {
    override suspend fun listChats(limit: Int): List<TelegramChatSummary> {
        delay(150)
        return listOf(
            TelegramChatSummary(10, "Canal Tecnologia", TelegramChatType.Channel),
            TelegramChatSummary(11, "Canal Filmes", TelegramChatType.Channel),
            TelegramChatSummary(12, "Grupo Família", TelegramChatType.Group),
            TelegramChatSummary(13, "Supergroup Séries", TelegramChatType.Supergroup),
            TelegramChatSummary(14, "Canal Documentários", TelegramChatType.Channel),
            TelegramChatSummary(15, "Canal Notícias", TelegramChatType.Channel)
        ).take(limit)
    }
}
