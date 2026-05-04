package com.ntv2.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ntv2.app.core.database.dao.SelectedChannelDao
import com.ntv2.app.core.database.entity.SelectedChannelEntity

@Database(
    entities = [SelectedChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedChannelDao(): SelectedChannelDao
}
