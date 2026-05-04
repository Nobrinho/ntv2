package com.ntv2.app.feature.media.data.datasource

import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.feature.media.domain.MediaItemSummary

class TdlibTelegramMediaDataSource(
    private val tdlibMediaGateway: TdlibMediaGateway
) : TelegramMediaDataSource {

    override suspend fun listChannelVideos(channelId: Long, channelTitle: String): List<MediaItemSummary> {
        return tdlibMediaGateway.listVideoMessages(chatId = channelId, limit = 300).map { video ->
            MediaItemSummary(
                mediaId = video.mediaId,
                channelId = channelId,
                channelTitle = channelTitle,
                title = video.title,
                caption = video.caption,
                fileName = video.fileName,
                durationSeconds = video.durationSeconds,
                thumbnailPath = video.thumbnailPath,
                fileId = video.fileId
            )
        }
    }
}
