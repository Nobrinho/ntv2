package com.ntv2.app.core.library

import com.ntv2.app.core.database.dao.FavoriteDao
import com.ntv2.app.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Um título salvo na "Minha lista" do usuário. */
data class FavoriteMedia(
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val posterPath: String?,
    val thumbnailPath: String?,
    val durationSeconds: Int,
    val tmdbId: String?,
    val genres: String?,
    val addedAt: Long
)

/** "Minha lista": favoritar/desfavoritar e observar a lista. Único por dispositivo. */
interface UserLibraryRepository {
    /** Minha lista, mais recente primeiro. */
    fun observeFavorites(): Flow<List<FavoriteMedia>>

    /** Ids favoritados — para marcar o coração nos cards/detalhes. */
    fun observeFavoriteIds(): Flow<Set<String>>

    suspend fun isFavorite(mediaId: String): Boolean

    suspend fun add(media: FavoriteMedia)

    suspend fun remove(mediaId: String)

    /** Alterna o estado e devolve o novo (true = agora favoritado). */
    suspend fun toggle(media: FavoriteMedia): Boolean
}

class RoomUserLibraryRepository(
    private val dao: FavoriteDao
) : UserLibraryRepository {

    override fun observeFavorites(): Flow<List<FavoriteMedia>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        dao.observeIds().map { it.toSet() }

    override suspend fun isFavorite(mediaId: String): Boolean = dao.isFavorite(mediaId)

    override suspend fun add(media: FavoriteMedia) = dao.upsert(media.toEntity())

    override suspend fun remove(mediaId: String) = dao.delete(mediaId)

    override suspend fun toggle(media: FavoriteMedia): Boolean =
        if (dao.isFavorite(media.mediaId)) {
            dao.delete(media.mediaId)
            false
        } else {
            dao.upsert(media.toEntity())
            true
        }

    private fun FavoriteEntity.toModel() = FavoriteMedia(
        mediaId = mediaId,
        channelId = channelId,
        channelTitle = channelTitle,
        title = title,
        posterPath = posterPath,
        thumbnailPath = thumbnailPath,
        durationSeconds = durationSeconds,
        tmdbId = tmdbId,
        genres = genres,
        addedAt = addedAt
    )

    private fun FavoriteMedia.toEntity() = FavoriteEntity(
        mediaId = mediaId,
        channelId = channelId,
        channelTitle = channelTitle,
        title = title,
        posterPath = posterPath,
        thumbnailPath = thumbnailPath,
        durationSeconds = durationSeconds,
        tmdbId = tmdbId,
        genres = genres,
        addedAt = if (addedAt > 0L) addedAt else System.currentTimeMillis()
    )
}
