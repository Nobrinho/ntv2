package com.ntv2.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ntv2.app.core.database.dao.FavoriteDao
import com.ntv2.app.core.database.dao.PlaybackProgressDao
import com.ntv2.app.core.database.dao.SelectedChannelDao
import com.ntv2.app.core.database.dao.WatchHistoryDao
import com.ntv2.app.core.database.entity.FavoriteEntity
import com.ntv2.app.core.database.entity.PlaybackProgressEntity
import com.ntv2.app.core.database.entity.SelectedChannelEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity

@Database(
    entities = [
        SelectedChannelEntity::class,
        PlaybackProgressEntity::class,
        FavoriteEntity::class,
        WatchHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedChannelDao(): SelectedChannelDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao

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

        // Adiciona o caminho do avatar do canal (para exibir a foto no picker).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE selected_channels ADD COLUMN avatarPath TEXT")
            }
        }

        // Personalização: Minha lista (favorites) e Histórico/Continuar assistindo (watch_history).
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorites (" +
                        "mediaId TEXT NOT NULL PRIMARY KEY, " +
                        "channelId INTEGER NOT NULL, " +
                        "channelTitle TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "posterPath TEXT, " +
                        "thumbnailPath TEXT, " +
                        "durationSeconds INTEGER NOT NULL, " +
                        "tmdbId TEXT, " +
                        "genres TEXT, " +
                        "addedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watch_history (" +
                        "mediaId TEXT NOT NULL PRIMARY KEY, " +
                        "channelId INTEGER NOT NULL, " +
                        "channelTitle TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "posterPath TEXT, " +
                        "thumbnailPath TEXT, " +
                        "durationMs INTEGER NOT NULL, " +
                        "lastPositionMs INTEGER NOT NULL, " +
                        "completed INTEGER NOT NULL, " +
                        "genres TEXT, " +
                        "updatedAt INTEGER NOT NULL)"
                )
            }
        }
    }
}
