package com.ntv2.app.core.backend.sync

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendRequest
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.BackendSession
import com.ntv2.app.core.backend.BackendTokenStore
import com.ntv2.app.core.backend.BackendTokens
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.database.InMemoryFavoriteDao
import com.ntv2.app.core.database.InMemoryHistoryDao
import com.ntv2.app.core.database.InMemoryProgressDao
import com.ntv2.app.core.library.FavoriteMedia
import com.ntv2.app.core.library.RoomUserLibraryRepository
import com.ntv2.app.core.player.progress.RoomPlaybackProgressStore
import com.ntv2.app.core.player.progress.WatchedMediaMeta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class PersonalizationSyncerTest {

    /**
     * Servidor falso com o comportamento do contrato: upsert por (user_id, media_id) com o trigger
     * keep_newest (ignora updated_at mais velho), pull por updated_at > x em ordem crescente com limit.
     */
    private class FakeServer(var syncFlag: Boolean? = true) : HttpTransport {
        val tables = mutableMapOf<String, MutableMap<String, JSONObject>>()
        val requests = mutableListOf<BackendRequest>()
        private var users = 0
        /** Conta de cada usuário (vínculo de aparelhos); sem entrada, a conta é o próprio usuário. */
        val accounts = mutableMapOf<String, String>()

        fun rows(table: String): Map<String, JSONObject> = tables[table].orEmpty()

        override fun execute(request: BackendRequest): BackendResponse {
            requests += request
            val path = request.url.substringAfter("54321")
            if (path.startsWith("/auth/v1/signup")) {
                users++
                return BackendResponse(
                    200,
                    """{"access_token":"t$users","refresh_token":"r$users","expires_in":3600,"user":{"id":"u$users"}}"""
                )
            }
            val rest = path.removePrefix("/rest/v1/")
            val table = rest.substringBefore('?')
            if (table == "rpc/current_account") {
                val uid = "u" + request.headers["Authorization"].orEmpty().substringAfter("Bearer t")
                return BackendResponse(200, "\"${accounts[uid] ?: uid}\"")
            }
            val query = rest.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
            if (table == "app_config") {
                val flags = syncFlag?.let { JSONObject().put("sync", it) }
                val body = if (flags == null) "[]" else JSONArray().put(JSONObject().put("key", "flags").put("value", flags)).toString()
                return BackendResponse(200, body)
            }
            val store = tables.getOrPut(table) { linkedMapOf() }
            return when (request.method) {
                "POST" -> {
                    val incoming = JSONArray(request.body)
                    for (i in 0 until incoming.length()) {
                        val row = incoming.getJSONObject(i)
                        val id = row.getString("media_id")
                        val saved = store[id]
                        // keep_newest: escrita mais velha que a salva é ignorada.
                        if (saved != null && ms(row) < ms(saved)) continue
                        val merged = saved?.let { JSONObject(it.toString()) } ?: JSONObject()
                        row.keys().forEach { k -> merged.put(k, row.get(k)) }
                        store[id] = merged
                    }
                    BackendResponse(201, "")
                }
                else -> {
                    val gt = SyncTime.toEpochMs(query.getValue("updated_at").removePrefix("gt."))!!
                    val limit = query.getValue("limit").toInt()
                    val page = store.values.filter { ms(it) > gt }.sortedBy { ms(it) }.take(limit)
                    BackendResponse(200, JSONArray(page).toString())
                }
            }
        }

        private fun ms(row: JSONObject) = SyncTime.toEpochMs(row.getString("updated_at"))!!
    }

    private class MemoryTokens : BackendTokenStore {
        var tokens: BackendTokens? = null
        override suspend fun load() = tokens
        override suspend fun save(tokens: BackendTokens) { this.tokens = tokens }
        override suspend fun clear() { tokens = null }
    }

    private class InMemorySyncMarks : SyncMarks {
        private var user: String? = null
        val pushed = mutableMapOf<String, Long>()
        val pulled = mutableMapOf<String, String>()
        override suspend fun userId(): String? = user
        override suspend fun reset(userId: String) { user = userId; pushed.clear(); pulled.clear() }
        override suspend fun pushedUpTo(table: String): Long = pushed[table] ?: 0L
        override suspend fun setPushedUpTo(table: String, epochMs: Long) { pushed[table] = epochMs }
        override suspend fun pulledUpTo(table: String): String? = pulled[table]
        override suspend fun setPulledUpTo(table: String, iso: String) { pulled[table] = iso }
    }

    /** Um aparelho: Room em memória + repositórios reais + syncer. Mesma sessão = mesmo usuário. */
    private class Device(server: FakeServer, tokens: MemoryTokens, var clock: Long = 1_000_000L, pageSize: Int = 500) {
        val favorites = InMemoryFavoriteDao()
        val progress = InMemoryProgressDao()
        val history = InMemoryHistoryDao()
        val marks = InMemorySyncMarks()
        val client = SupabaseClient(BackendConfig("http://local:54321", "anon"), server)
        val session = BackendSession(client, tokens)
        val library = RoomUserLibraryRepository(favorites, now = { clock })
        val store = RoomPlaybackProgressStore(progress, history, now = { clock })
        val syncer = PersonalizationSyncer(
            client, session, favorites, progress, history, marks,
            syncFlag = AppConfigSyncFlag(client, ttlMs = 0L),
            pageSize = pageSize
        )
    }

    private fun fav(id: String, poster: String? = "https://image.tmdb.org/p/$id.jpg") = FavoriteMedia(
        mediaId = id, channelId = 0L, channelTitle = "Filmes", title = "Filme $id",
        posterPath = poster, thumbnailPath = null, durationSeconds = 7200, tmdbId = "42", genres = "Drama",
        addedAt = 0L
    )

    private fun meta(id: String) = WatchedMediaMeta(
        mediaId = id, channelId = -100L, channelTitle = "Filmes", title = "Filme $id",
        posterPath = null, thumbnailPath = null, durationSeconds = 7200, genres = null
    )

    @Test
    fun `favorito do celular aparece na TV e a remocao tambem viaja`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens)
        val tv = Device(server, tokens, clock = 1_000_000L)

        phone.library.add(fav("-100_7"))
        phone.syncer.sync()
        val pushed = server.rows("favorites").getValue("-100_7")
        assertEquals("u1", pushed.getString("user_id"))
        assertEquals(SyncTime.toIso(1_000_000L), pushed.getString("updated_at"))
        assertFalse(pushed.getBoolean("deleted"))

        tv.syncer.sync()
        val onTv = tv.library.observeFavorites().first().single()
        assertEquals("Filme -100_7", onTv.title)
        assertEquals(-100L, onTv.channelId) // derivado do mediaId
        assertEquals("https://image.tmdb.org/p/-100_7.jpg", onTv.posterPath)

        tv.clock = 2_000_000L
        tv.library.remove("-100_7")
        tv.syncer.sync()
        assertTrue(server.rows("favorites").getValue("-100_7").getBoolean("deleted"))

        phone.syncer.sync()
        assertFalse(phone.library.isFavorite("-100_7"))
        assertTrue(phone.favorites.rows.getValue("-100_7").deleted)
    }

    @Test
    fun `LWW - alteracao local mais nova nao e sobrescrita pelo remoto mais velho`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens, clock = 1_000L)
        val tv = Device(server, tokens, clock = 5_000L)

        phone.library.add(fav("a"))   // t=1000
        tv.library.add(fav("a"))      // t=5000 (TV ainda não sincronizou)
        tv.library.remove("a")        // removido na TV em t=5000

        phone.syncer.sync()           // servidor: favorito (1000)
        tv.syncer.sync()              // TV sobe o tombstone (5000) e não aplica o favorito velho
        assertFalse(tv.library.isFavorite("a"))
        assertTrue(server.rows("favorites").getValue("a").getBoolean("deleted"))

        phone.syncer.sync()           // celular recebe o tombstone, mais novo
        assertFalse(phone.library.isFavorite("a"))

        // E o servidor ignora um push mais velho (keep_newest).
        phone.favorites.upsert(phone.favorites.rows.getValue("a").copy(deleted = false, updatedAt = 2_000L))
        phone.marks.pushed.clear()
        phone.syncer.sync()
        assertTrue(server.rows("favorites").getValue("a").getBoolean("deleted"))
    }

    @Test
    fun `progresso e historico chegam no outro aparelho com a posicao`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens)
        val tv = Device(server, tokens)

        phone.store.recordOpened(meta("-100_9"))
        phone.clock += 1_000
        phone.store.onProgress("-100_9", positionMs = 30 * 60_000L, durationMs = 7_200_000L)
        phone.syncer.sync()

        val remote = server.rows("playback_progress").getValue("-100_9")
        assertEquals(30 * 60_000L, remote.getLong("position_ms"))

        tv.syncer.sync()
        assertEquals(30 * 60_000L, tv.store.resumePositionMs("-100_9", 7_200_000L))
        val cont = tv.store.continueWatching()
        assertEquals(listOf("-100_9"), cont.map { it.mediaId })
        assertEquals(30 * 60_000L, cont.single().lastPositionMs)
        assertEquals("Filmes", cont.single().channelTitle)
    }

    @Test
    fun `limpar historico propaga tombstones`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens)
        val tv = Device(server, tokens)
        phone.store.recordOpened(meta("-100_1"))
        phone.store.recordOpened(meta("-100_2"))
        phone.syncer.sync()
        tv.syncer.sync()
        assertEquals(2, tv.store.history().size)

        phone.clock += 10_000
        phone.store.clearHistory()
        phone.syncer.sync()
        tv.syncer.sync()
        assertTrue(tv.store.history().isEmpty())
    }

    @Test
    fun `push so manda o que mudou desde a ultima rodada e nao devolve o que veio do servidor`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens)
        val tv = Device(server, tokens)
        phone.library.add(fav("a"))

        assertEquals(1, phone.syncer.sync().pushed[PersonalizationSyncer.FAVORITES])
        assertEquals(0, phone.syncer.sync().pushed[PersonalizationSyncer.FAVORITES])

        val first = tv.syncer.sync()
        assertEquals(1, first.pulled[PersonalizationSyncer.FAVORITES])
        // O que a TV recebeu não volta ao servidor, e a mesma linha não é baixada de novo.
        val second = tv.syncer.sync()
        assertEquals(0, second.pushed[PersonalizationSyncer.FAVORITES])
        assertEquals(0, second.pulled[PersonalizationSyncer.FAVORITES])
    }

    @Test
    fun `pull pagina ate esgotar`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens, pageSize = 2)
        val tv = Device(server, tokens, pageSize = 2)
        repeat(5) { i ->
            phone.clock += 1_000
            phone.library.add(fav("-100_$i"))
        }
        phone.syncer.sync()
        assertEquals(5, server.rows("favorites").size)
        // Push também em lotes do tamanho da página.
        assertEquals(3, server.requests.count { it.method == "POST" && it.url.contains("/favorites?") })

        assertEquals(5, tv.syncer.sync().pulled[PersonalizationSyncer.FAVORITES])
        assertEquals(5, tv.library.observeFavorites().first().size)
    }

    @Test
    fun `pull usa o updated_at do servidor como marca e codifica na url`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val tv = Device(server, tokens)
        server.tables["favorites"] = linkedMapOf(
            "-100_3" to JSONObject()
                .put("media_id", "-100_3").put("title", "Remoto").put("poster_url", JSONObject.NULL)
                .put("channel_name", "Canal").put("tmdb_id", JSONObject.NULL).put("year", 2020)
                .put("updated_at", "2026-10-17T12:00:00.123456+00:00").put("deleted", false)
        )
        tv.syncer.sync()
        val local = tv.favorites.rows.getValue("-100_3")
        assertEquals(SyncTime.toEpochMs("2026-10-17T12:00:00.123Z"), local.updatedAt)
        assertNull(local.posterPath)
        assertNull(local.tmdbId)
        assertEquals("2026-10-17T12:00:00.123456+00:00", tv.marks.pulled["favorites"])
        tv.syncer.sync()
        val lastGet = server.requests.last { it.method == "GET" && it.url.contains("/favorites?") }
        assertTrue(lastGet.url, lastGet.url.contains("updated_at=gt.2026-10-17T12%3A00%3A00.123456%2B00%3A00"))
    }

    @Test
    fun `capa local nao sobe, so URL`() = runTest {
        val server = FakeServer()
        val phone = Device(server, MemoryTokens())
        phone.library.add(fav("a", poster = "/data/user/0/tdlib/photo.jpg"))
        phone.syncer.sync()
        assertTrue(server.rows("favorites").getValue("a").isNull("poster_url"))
    }

    @Test
    fun `flag sync=false ou backend desligado nao fazem nada`() = runTest {
        val server = FakeServer(syncFlag = false)
        val phone = Device(server, MemoryTokens())
        phone.library.add(fav("a"))
        assertEquals("flag sync=false", phone.syncer.sync().skipped)
        assertTrue(server.rows("favorites").isEmpty())
        assertTrue(server.requests.none { it.url.contains("/auth/") })

        val offServer = FakeServer()
        val offClient = SupabaseClient(BackendConfig("", ""), offServer)
        val off = PersonalizationSyncer(
            offClient, BackendSession(offClient, MemoryTokens()),
            InMemoryFavoriteDao(), InMemoryProgressDao(), InMemoryHistoryDao(), InMemorySyncMarks()
        )
        assertEquals("backend desligado", off.sync().skipped)
        assertTrue(offServer.requests.isEmpty())
    }

    @Test
    fun `flag ausente no app_config conta como ligada`() = runTest {
        val server = FakeServer(syncFlag = null)
        val phone = Device(server, MemoryTokens())
        phone.library.add(fav("a"))
        assertNull(phone.syncer.sync().skipped)
        assertEquals(1, server.rows("favorites").size)
    }

    @Test
    fun `sessao anonima nova recomeca as marcas`() = runTest {
        val server = FakeServer()
        val tokens = MemoryTokens()
        val phone = Device(server, tokens)
        phone.library.add(fav("a"))
        phone.syncer.sync()
        assertEquals(1, server.rows("favorites").size)

        // Servidor resetado: outra sessão (u2). Tudo sobe de novo para o novo usuário.
        server.tables.clear()
        tokens.tokens = null
        assertEquals(1, phone.syncer.sync().pushed[PersonalizationSyncer.FAVORITES])
        assertEquals("u2", server.rows("favorites").getValue("a").getString("user_id"))
    }

    @Test
    fun `aparelho vinculado a outra conta sobe tudo de novo com o dono da conta`() = runTest {
        val server = FakeServer()
        val tv = Device(server, MemoryTokens())
        tv.library.add(fav("-100_1"))
        tv.library.add(fav("-100_2"))
        tv.syncer.sync()
        assertEquals("u1", server.rows("favorites").getValue("-100_1").getString("user_id"))
        val before = server.requests.count { it.method == "POST" && it.url.contains("/favorites?") }

        // Celular (u9) confirmou o código: a TV agora pertence à conta dele.
        server.accounts["u1"] = "u9"
        tv.syncer.sync()
        assertEquals("marcas zeradas: sobe tudo de novo", before + 1,
            server.requests.count { it.method == "POST" && it.url.contains("/favorites?") })
        assertEquals("u9", server.rows("favorites").getValue("-100_2").getString("user_id"))
        assertEquals("u9", tv.marks.userId())
    }

    @Test
    fun `sem Premium nao sincroniza`() = runTest {
        val server = FakeServer()
        val tv = Device(server, MemoryTokens())
        val syncer = PersonalizationSyncer(
            tv.client, tv.session, tv.favorites, tv.progress, tv.history, tv.marks,
            syncFlag = AppConfigSyncFlag(tv.client, ttlMs = 0L),
            premiumCheck = { false }
        )
        tv.library.add(fav("-100_1"))
        assertEquals("sem Premium", syncer.sync().skipped)
        assertTrue(server.requests.none { it.url.contains("/favorites") })
    }
}
