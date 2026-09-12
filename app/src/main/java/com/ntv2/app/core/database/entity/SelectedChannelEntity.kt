package com.ntv2.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "selected_channels")
data class SelectedChannelEntity(
    @PrimaryKey val channelId: Long,
    val title: String,
    val avatarPath: String? = null
)
