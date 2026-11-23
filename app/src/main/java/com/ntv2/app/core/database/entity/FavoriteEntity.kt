package com.ntv2.app.core.database.entity

import com.ntv2.app.core.profiles.ProfileIds
import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Um título na "Minha lista" do usuário. Guarda os campos denormalizados do card para renderizar a
 * trilha sem rede; a reprodução é resolvida depois pelo messageId embutido no [mediaId]
 * ("channelId_messageId"), igual aos cards do índice.
 */
@Entity(tableName = "favorites", primaryKeys = ["profileId", "mediaId"])
data class FavoriteEntity(
    /** Perfil de "Quem está assistindo?" (ProfileStore); "p_main" é o perfil inicial. */
    @ColumnInfo(defaultValue = "p_main") val profileId: String = ProfileIds.MAIN,
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val posterPath: String?,
    val thumbnailPath: String?,
    val durationSeconds: Int,
    val tmdbId: String?,
    val genres: String?,
    val addedAt: Long,
    /** Última alteração (epoch ms): base do last-write-wins na sincronização. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = addedAt,
    /** Tombstone: removido da lista, mas guardado para propagar a remoção aos outros aparelhos. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false
)
