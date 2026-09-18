package com.ntv2.app.feature.media.data.datasource

import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.feature.media.domain.MediaItemSummary
import com.ntv2.app.feature.media.domain.MediaPage

class TdlibTelegramMediaDataSource(
    private val tdlibMediaGateway: TdlibMediaGateway
) : TelegramMediaDataSource {

    override suspend fun listChannelVideos(channelId: Long, channelTitle: String, fromMessageId: Long, limit: Int): MediaPage {
        val page = tdlibMediaGateway.listVideoMessages(chatId = channelId, fromMessageId = fromMessageId, limit = limit)
        return MediaPage(
            items = page.videos.map { video -> video.toSummary(channelId, channelTitle) },
            nextCursor = page.nextFromMessageId
        )
    }

    override suspend fun searchChannelVideos(channelId: Long, channelTitle: String, query: String, fromMessageId: Long, limit: Int): MediaPage {
        val page = tdlibMediaGateway.searchVideoMessages(chatId = channelId, query = query, fromMessageId = fromMessageId, limit = limit)
        return MediaPage(
            items = page.videos.map { video -> video.toSummary(channelId, channelTitle) },
            nextCursor = page.nextFromMessageId
        )
    }

    override suspend fun getVideoByMessage(channelId: Long, channelTitle: String, messageId: Long): MediaItemSummary? =
        tdlibMediaGateway.getVideoByMessage(chatId = channelId, messageId = messageId)?.toSummary(channelId, channelTitle)

    private fun com.ntv2.app.core.telegram.media.TelegramVideoMessage.toSummary(
        channelId: Long,
        channelTitle: String
    ): MediaItemSummary = MediaItemSummary(
        mediaId = mediaId,
        channelId = channelId,
        channelTitle = channelTitle,
        title = title,
        caption = caption,
        fileName = fileName,
        durationSeconds = durationSeconds,
        thumbnailPath = thumbnailPath,
        fileId = fileId,
        width = width,
        height = height,
        coverAspectRatio = coverAspectRatio,
        posterPath = posterPath,
        synopsis = synopsis,
        year = year,
        director = director,
        audio = audio,
        genres = genres,
        originalTitle = originalTitle,
        backdropPath = backdropPath,
        rating = rating,
        ageRating = ageRating,
        country = country,
        quality = quality,
        studio = studio,
        cast = cast,
        trailerUrl = trailerUrl,
        tmdbId = tmdbId,
        category = category,
        collection = collection,
        tags = tags
    )
}
