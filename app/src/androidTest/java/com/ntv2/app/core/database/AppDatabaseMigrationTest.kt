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
    fun bancoNovoAbreNaVersaoAtual() {
        withUpgradedDb { database ->
            assertEquals(emptyList<Any>(), database.watchHistoryDao().getAll())
            assertEquals(4, database.openHelper.readableDatabase.version)
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
    }
}
