package com.ntv2.app.core.database.entity

import com.ntv2.app.core.profiles.ProfileIds
import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "playback_progress", primaryKeys = ["profileId", "mediaId"])
data class PlaybackProgressEntity(
    /** Perfil de "Quem está assistindo?" (ProfileStore); "p_main" é o perfil inicial. */
    @ColumnInfo(defaultValue = "p_main") val profileId: String = ProfileIds.MAIN,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Tombstone: removido, mas guardado para propagar a remoção aos outros aparelhos. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false
)
