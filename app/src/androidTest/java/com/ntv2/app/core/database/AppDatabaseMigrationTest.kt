package com.ntv2.app.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ntv2.app.core.database.entity.FavoriteEntity
import com.ntv2.app.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Atualização do app com o banco de uma versão antiga: os dados precisam sobreviver. Cria o banco
 * como ele era em cada versão (SQL igual ao que o Room gerava, tirado do histórico das entidades),
 * põe dados e abre com [AppDatabase.build] — a MESMA configuração do app. O Room valida o schema ao
 * abrir: uma migração errada ou faltando falha aqui (antes o app apagava o banco em silêncio).
 *
 * Usa um arquivo próprio: não mexe no banco real do app instalado.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun v1_preservaCanais() {
        createDb(version = 1, schema = V1) { db ->
            db.execSQL("INSERT INTO selected_channels (channelId, title) VALUES (10, 'Filmes')")
        }
        withUpgradedDb { database ->
            val channels = database.selectedChannelDao().observeAll().first()
            assertEquals(listOf(10L), channels.map { it.channelId })
            assertEquals("Filmes", channels.single().title)
            assertNull(channels.single().avatarPath)
            // Tabelas das versões seguintes existem e funcionam.
            assertEquals(emptyList<Any>(), database.playbackProgressDao().getAll())
            assertEquals(emptyList<Any>(), database.favoriteDao().observeAll().first())
        }
    }

    @Test
    fun v2_preservaProgresso() {
        createDb(version = 2, schema = V2) { db ->
            db.execSQL("INSERT INTO selected_channels (channelId, title) VALUES (10, 'Filmes')")
            db.execSQL(
                "INSERT INTO playback_progress (mediaId, positionMs, durationMs, updatedAt) " +
                    "VALUES ('m1', 82000, 7200000, 1000)"
            )
        }
        withUpgradedDb { database ->
            val progress = database.playbackProgressDao().get("m1")
            assertEquals(82_000L, progress?.positionMs)
            assertEquals(1, database.selectedChannelDao().observeAll().first().size)
        }
    }

    @Test
    fun v3_preservaTudoEAceitaMinhaListaEHistorico() {
        createDb(version = 3, schema = V3) { db ->
            db.execSQL("INSERT INTO selected_channels (channelId, title, avatarPath) VALUES (10, 'Filmes', '/a.jpg')")
            db.execSQL(
                "INSERT INTO playback_progress (mediaId, positionMs, durationMs, updatedAt) " +
                    "VALUES ('m1', 82000, 7200000, 1000)"
            )
        }
        withUpgradedDb { database ->
            assertEquals("/a.jpg", database.selectedChannelDao().observeAll().first().single().avatarPath)
            assertEquals(82_000L, database.playbackProgressDao().get("m1")?.positionMs)
            // Tabelas criadas pela migração 3→4 aceitam dados pelo DAO (tipos compatíveis).
            database.favoriteDao().upsert(
                FavoriteEntity("m1", 10L, "Filmes", "Linha de Frente", null, null, 7200, null, null, 2000L)
            )
            database.watchHistoryDao().upsert(
                WatchHistoryEntity("m1", 10L, "Filmes", "Linha de Frente", null, null, 7_200_000L, 82_000L, false, null, 3000L)
            )
            assertEquals(true, database.favoriteDao().isFavorite("m1"))
            assertEquals(82_000L, database.watchHistoryDao().get("m1")?.lastPositionMs)
        }
    }

    @Test
    fun v4_preservaPersonalizacaoEPreencheUpdatedAtEDeleted() {
        createDb(version = 4, schema = V4) { db ->
            db.execSQL(
                "INSERT INTO favorites (mediaId, channelId, channelTitle, title, posterPath, thumbnailPath, " +
                    "durationSeconds, tmdbId, genres, addedAt) VALUES ('m1', 10, 'Filmes', 'Linha de Frente', " +
                    "NULL, NULL, 7200, '42', 'Drama', 2000)"
            )
            db.execSQL(
                "INSERT INTO watch_history (mediaId, channelId, channelTitle, title, posterPath, thumbnailPath, " +
                    "durationMs, lastPositionMs, completed, genres, updatedAt) VALUES ('m1', 10, 'Filmes', " +
                    "'Linha de Frente', NULL, NULL, 7200000, 82000, 0, NULL, 3000)"
            )
            db.execSQL(
                "INSERT INTO playback_progress (mediaId, positionMs, durationMs, updatedAt) " +
                    "VALUES ('m1', 82000, 7200000, 1000)"
            )
        }
        withUpgradedDb { database ->
            val fav = database.favoriteDao().getForSync("m1")!!
            assertEquals(2000L, fav.updatedAt) // preenchido com addedAt
            assertEquals(false, fav.deleted)
            assertEquals(true, database.favoriteDao().isFavorite("m1"))
            assertEquals(3000L, database.watchHistoryDao().get("m1")?.updatedAt)
            assertEquals(82_000L, database.playbackProgressDao().get("m1")?.positionMs)

            // Tombstone: some das consultas da UI, mas continua para a sincronização.
            database.favoriteDao().markDeleted("m1", 5000L)
            assertEquals(false, database.favoriteDao().isFavorite("m1"))
            assertEquals(emptyList<Any>(), database.favoriteDao().observeAll().first())
            assertEquals(true, database.favoriteDao().getForSync("m1")?.deleted)
            assertEquals(listOf("m1"), database.favoriteDao().changedSince(4000L).map { it.mediaId })

            database.watchHistoryDao().markAllDeleted(6000L)
            assertEquals(emptyList<Any>(), database.watchHistoryDao().getAll())
            database.playbackProgressDao().markDeleted("m1", 6000L)
            assertNull(database.playbackProgressDao().get("m1"))
            assertEquals(true, database.playbackProgressDao().getForSync("m1")?.deleted)
        }
    }

    @Test
    fun bancoNovoAbreNaVersaoAtual() {
        withUpgradedDb { database ->
            assertEquals(emptyList<Any>(), database.watchHistoryDao().getAll())
            assertEquals(5, database.openHelper.readableDatabase.version)
        }
    }

    private fun createDb(version: Int, schema: List<String>, seed: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = schema.forEach(db::execSQL)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper -> seed(helper.writableDatabase) }
    }

    private fun withUpgradedDb(block: suspend (AppDatabase) -> Unit) {
        val database = AppDatabase.build(context, dbName)
        try {
            runBlocking { block(database) }
        } finally {
            database.close()
        }
    }

    private companion object {
        // Tabelas como o Room criava em cada versão (entidades nos commits 4f175b5, f286728, b1992d7).
        val SELECTED_CHANNELS_V1 =
            "CREATE TABLE IF NOT EXISTS `selected_channels` (`channelId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, PRIMARY KEY(`channelId`))"
        val PLAYBACK_PROGRESS_V2 =
            "CREATE TABLE IF NOT EXISTS `playback_progress` (`mediaId` TEXT NOT NULL, " +
                "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaId`))"
        val SELECTED_CHANNELS_V3 =
            "CREATE TABLE IF NOT EXISTS `selected_channels` (`channelId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `avatarPath` TEXT, PRIMARY KEY(`channelId`))"

        val V1 = listOf(SELECTED_CHANNELS_V1)
        val V2 = listOf(SELECTED_CHANNELS_V1, PLAYBACK_PROGRESS_V2)
        val V3 = listOf(SELECTED_CHANNELS_V3, PLAYBACK_PROGRESS_V2)

        // v4 (app/schemas/.../4.json): Minha lista e histórico.
        val FAVORITES_V4 =
            "CREATE TABLE IF NOT EXISTS `favorites` (`mediaId` TEXT NOT NULL, `channelId` INTEGER NOT NULL, " +
                "`channelTitle` TEXT NOT NULL, `title` TEXT NOT NULL, `posterPath` TEXT, `thumbnailPath` TEXT, " +
                "`durationSeconds` INTEGER NOT NULL, `tmdbId` TEXT, `genres` TEXT, `addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaId`))"
        val WATCH_HISTORY_V4 =
            "CREATE TABLE IF NOT EXISTS `watch_history` (`mediaId` TEXT NOT NULL, `channelId` INTEGER NOT NULL, " +
                "`channelTitle` TEXT NOT NULL, `title` TEXT NOT NULL, `posterPath` TEXT, `thumbnailPath` TEXT, " +
                "`durationMs` INTEGER NOT NULL, `lastPositionMs` INTEGER NOT NULL, `completed` INTEGER NOT NULL, " +
                "`genres` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))"
        val V4 = listOf(SELECTED_CHANNELS_V3, PLAYBACK_PROGRESS_V2, FAVORITES_V4, WATCH_HISTORY_V4)
    }
}
