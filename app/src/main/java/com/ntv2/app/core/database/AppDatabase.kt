package com.ntv2.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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
    // Schema em app/schemas (no git): a cada versão nova, o JSON gerado é a referência da migração.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedChannelDao(): SelectedChannelDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        const val NAME = "ntv2.db"

        /**
         * Único ponto de construção do banco (app e testes de migração usam o mesmo).
         *
         * SEM fallbackToDestructiveMigration: antes, se uma migração faltasse ou falhasse numa
         * atualização, o Room apagava o banco em silêncio e o usuário perdia Minha lista, histórico e
         * "continuar assistindo". Agora toda versão nova PRECISA de uma migração em [ALL_MIGRATIONS]
         * (coberta por AppDatabaseMigrationTest). Só o downgrade (instalar versão mais antiga por cima)
         * recria o banco, para não travar o app.
         */
        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

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

        /** Todas as migrações, em ordem. Toda versão nova do banco acrescenta a sua aqui. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
