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
    version = 6,
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

        /**
         * Sincronização da personalização: `updatedAt` (epoch ms) e `deleted` (tombstone) nas três
         * tabelas sincronizadas. Favoritos antigos ganham `updatedAt = addedAt`; progresso e histórico
         * já tinham `updatedAt`. Nada é apagado: as linhas existentes ficam com `deleted = 0`.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach(db::execSQL)
            }
        }

        // ALTER TABLE ... NOT NULL exige DEFAULT no SQLite; as entidades declaram o mesmo default.
        internal val MIGRATION_4_5_SQL: List<String> = listOf(
            "ALTER TABLE favorites ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
            "UPDATE favorites SET updatedAt = addedAt",
            "ALTER TABLE favorites ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE playback_progress ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE watch_history ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
        )

        /**
         * Perfis: favoritos, progresso e histórico ganham `profileId` e a chave passa a (profileId, mediaId).
         * O SQLite não troca a chave primária com ALTER TABLE: cada tabela é recriada e tudo que existia
         * vai para o perfil inicial "p_main" (o mesmo padrão do backend).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach(db::execSQL)
            }
        }

        internal val MIGRATION_5_6_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS `favorites_v6` (`profileId` TEXT NOT NULL DEFAULT 'p_main', " +
                "`mediaId` TEXT NOT NULL, `channelId` INTEGER NOT NULL, `channelTitle` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `posterPath` TEXT, `thumbnailPath` TEXT, `durationSeconds` INTEGER NOT NULL, " +
                "`tmdbId` TEXT, `genres` TEXT, `addedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                "`deleted` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`profileId`, `mediaId`))",
            "INSERT INTO favorites_v6 (profileId, mediaId, channelId, channelTitle, title, posterPath, thumbnailPath, " +
                "durationSeconds, tmdbId, genres, addedAt, updatedAt, deleted) " +
                "SELECT 'p_main', mediaId, channelId, channelTitle, title, posterPath, thumbnailPath, " +
                "durationSeconds, tmdbId, genres, addedAt, updatedAt, deleted FROM favorites",
            "DROP TABLE favorites",
            "ALTER TABLE favorites_v6 RENAME TO favorites",

            "CREATE TABLE IF NOT EXISTS `playback_progress_v6` (`profileId` TEXT NOT NULL DEFAULT 'p_main', " +
                "`mediaId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`profileId`, `mediaId`))",
            "INSERT INTO playback_progress_v6 (profileId, mediaId, positionMs, durationMs, updatedAt, deleted) " +
                "SELECT 'p_main', mediaId, positionMs, durationMs, updatedAt, deleted FROM playback_progress",
            "DROP TABLE playback_progress",
            "ALTER TABLE playback_progress_v6 RENAME TO playback_progress",

            "CREATE TABLE IF NOT EXISTS `watch_history_v6` (`profileId` TEXT NOT NULL DEFAULT 'p_main', " +
                "`mediaId` TEXT NOT NULL, `channelId` INTEGER NOT NULL, `channelTitle` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `posterPath` TEXT, `thumbnailPath` TEXT, `durationMs` INTEGER NOT NULL, " +
                "`lastPositionMs` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `genres` TEXT, " +
                "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`profileId`, `mediaId`))",
            "INSERT INTO watch_history_v6 (profileId, mediaId, channelId, channelTitle, title, posterPath, thumbnailPath, " +
                "durationMs, lastPositionMs, completed, genres, updatedAt, deleted) " +
                "SELECT 'p_main', mediaId, channelId, channelTitle, title, posterPath, thumbnailPath, " +
                "durationMs, lastPositionMs, completed, genres, updatedAt, deleted FROM watch_history",
            "DROP TABLE watch_history",
            "ALTER TABLE watch_history_v6 RENAME TO watch_history"
        )

        /** Todas as migrações, em ordem. Toda versão nova do banco acrescenta a sua aqui. */
        val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
