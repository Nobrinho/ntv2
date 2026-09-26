package com.ntv2.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um título na "Minha lista" do usuário. Guarda os campos denormalizados do card para renderizar a
 * trilha sem rede; a reprodução é resolvida depois pelo messageId embutido no [mediaId]
 * ("channelId_messageId"), igual aos cards do índice.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
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
