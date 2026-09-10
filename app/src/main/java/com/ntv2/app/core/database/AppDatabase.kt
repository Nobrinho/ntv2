package com.ntv2.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.SelectedChannelDao
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.SelectedChannelEntity

@Database(
    entities = [SelectedChannelEntity::class, PlaybackProgressEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedChannelDao(): SelectedChannelDao
    abstract fun playbackProgressDao(): PlaybackProgressDao

    companion object {
        // Preserva os canais selecionados ao adicionar a tabela de progresso de reprodução.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS playback_progress (" +
                        "mediaId TEXT NOT NULL PRIMARY KEY, " +
                        "positionMs INTEGER NOT NULL, " +
                        "durationMs INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }
    }
}
