package com.ntv2.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)
