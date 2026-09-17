package com.ntv2.app.feature.media.domain


data class MediaItemSummary(
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val caption: String?,
    val fileName: String?,
    val durationSeconds: Int,
    val thumbnailPath: String?,
    val fileId: Int,
    val width: Int = 0,
    val height: Int = 0,
    /** Proporção (largura/altura) da capa exibida; 0 = desconhecida. */
    val coverAspectRatio: Float = 0f,
    val posterPath: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null,
    // Formato rico (canal próprio).
    val originalTitle: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    val ageRating: String? = null,
    val country: String? = null,
    val quality: String? = null,
    val studio: String? = null,
    val cast: List<com.ntv2.app.core.telegram.media.CastMemberMeta> = emptyList(),
    val trailerUrl: String? = null,
    val tmdbId: String? = null,
    val category: String? = null,
    val collection: String? = null,
    val tags: String? = null
)

data class MediaPage(
    val items: List<MediaItemSummary>,
    // Cursor para a próxima página (fromMessageId do TDLib). 0 => não há mais resultados.
    val nextCursor: Long
)

interface MediaRepository {
    suspend fun fetchChannelVideos(
        channelId: Long,
        channelTitle: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage

    suspend fun searchChannelVideos(
        channelId: Long,
        channelTitle: String,
        query: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage
}
