package com.ntv2.app.core.catalog

import com.ntv2.app.core.backend.BackendConfig
import com.ntv2.app.core.backend.BackendException
import com.ntv2.app.core.backend.BackendRequest
import com.ntv2.app.core.backend.BackendResponse
import com.ntv2.app.core.backend.HttpTransport
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.sync.SyncTime
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncTest {

    private fun movie(tmdb: Long, title: String, msg: Long = tmdb * 10) =
        CatalogMerge.rowOf(
            KIND_MOVIE, NtvIds.movie(tmdb), null,
            JSONObject().put("tmdb_id", tmdb).put("title", title).put("video_message_id", msg).put("chat_id", -100L)
        )

    private fun item(kind: String, ntvId: String, at: String, data: String?) =
        """{"kind":"$kind","ntv_id":"$ntvId","changed_at":"$at","data":${data ?: "null"}}"""

    private fun page(items: List<String>, since: String?, afterId: String?, hasMore: Boolean, reset: Boolean = false) =
        """{"items":[${items.joinToString(",")}],"next":{"since":${since?.let { "\"$it\"" } ?: "null"},""" +
            """"after_id":${afterId?.let { "\"$it\"" } ?: "null"}},"has_more":$hasMore,"reset":$reset}"""

    private val generatedAt = 1_800_000_000_000L
    private var now = generatedAt + 60_000L

    private fun sync(
        store: CatalogStore,
        pages: ArrayDeque<String>,
        calls: MutableList<CatalogCursor> = mutableListOf(),
        seed: () -> CatalogSeed? = { CatalogSeed(listOf(movie(603, "Matrix"), movie(604, "Reloaded")), generatedAt) },
        enabled: Boolean = true
    ) = CatalogSync(
        store = store,
        fetchChanges = { c -> calls += c; pages.removeFirst() },
        loadSeed = { seed() },
        enabled = { enabled },
        clock = { now },
        seedChunk = 1
    )

    @Test
    fun `primeira carga vem do indice e depois pede so a diferenca desde generated_at`() = runTest {
        val store = InMemoryCatalogStore()
        val calls = mutableListOf<CatalogCursor>()
        val result = sync(store, ArrayDeque(listOf(page(emptyList(), null, null, hasMore = false))), calls).syncOnce()

        assertEquals(2, store.count())
        // Lotes pequenos; só o primeiro apaga e só o último grava o cursor.
        assertEquals(listOf(true, false), store.batches.take(2).map { it.reset })
        assertNull(store.batches[0].cursor)
        val expectedSince = SyncTime.toIso(generatedAt - 6 * 60 * 60_000L)
        assertEquals(expectedSince, calls.single().since)
        assertNull(calls.single().afterId)
        assertTrue(result is CatalogSyncResult.Done && result.seeded)
        // Página vazia: o cursor continua o mesmo, só marca a rodada como feita.
        assertEquals(expectedSince, store.state?.since)
        assertEquals(now, store.state?.lastSyncAt)
    }

    @Test
    fun `sem indice nao chama o backend e tenta na proxima rodada`() = runTest {
        val store = InMemoryCatalogStore()
        val calls = mutableListOf<CatalogCursor>()
        val result = sync(store, ArrayDeque(), calls, seed = { null }).syncOnce()
        assertEquals(CatalogSyncResult.NoSeed, result)
        assertTrue(calls.isEmpty())
        assertNull(store.cursor())
    }

    @Test
    fun `merge aplica alteracao e removido pagina a pagina seguindo o cursor`() = runTest {
        val store = InMemoryCatalogStore()
        store.apply(CatalogBatch(upserts = listOf(movie(603, "Matrix"), movie(604, "Reloaded")), cursor = CatalogCursor("2026-10-01T00:00:00Z", null)))
        val calls = mutableListOf<CatalogCursor>()
        val pages = ArrayDeque(listOf(
            page(
                listOf(
                    item("movie", "m_603", "2026-10-02T00:00:00Z", """{"tmdb_id":603,"title":"Matrix (4K)","video_message_id":6030,"chat_id":-100}"""),
                    item("removed", "m_604", "2026-10-02T00:00:01Z", null)
                ),
                "2026-10-02T00:00:01Z", "m_604", hasMore = true
            ),
            page(
                listOf(item("movie", "m_605", "2026-10-03T00:00:00Z", """{"tmdb_id":605,"title":"Revolutions","video_message_id":6050}""")),
                "2026-10-03T00:00:00Z", "m_605", hasMore = false
            )
        ))
        val result = sync(store, pages, calls).syncOnce()

        assertEquals(listOf("2026-10-01T00:00:00Z", "2026-10-02T00:00:01Z"), calls.map { it.since })
        assertEquals(listOf(null, "m_604"), calls.map { it.afterId })
        assertEquals(setOf("m_603", "m_605"), store.rows.keys)
        assertTrue(JSONObject(store.rows.getValue("m_603").data).getString("title") == "Matrix (4K)")
        assertEquals(CatalogSyncResult.Done(pages = 2, upserts = 2, removed = 1, seeded = false), result)
        assertEquals(CatalogCursor("2026-10-03T00:00:00Z", "m_605", now), store.state)
    }

    @Test
    fun `mesmo ntv_id repetido na pagina fica com a ultima versao`() {
        val items = CatalogChangesPage.parse(
            page(
                listOf(
                    item("movie", "m_1", "t1", """{"tmdb_id":1,"title":"A"}"""),
                    item("removed", "m_1", "t2", null),
                    item("removed", "m_2", "t3", null),
                    item("movie", "m_2", "t4", """{"tmdb_id":2,"title":"B"}"""),
                    item("futuro", "z_9", "t5", """{"x":1}""")
                ),
                "t5", "z_9", hasMore = false
            )
        ).items
        val batch = CatalogMerge.batchFor(items, null)
        assertEquals(listOf("m_1"), batch.removed)
        assertEquals(listOf("m_2"), batch.upserts.map { it.ntvId })
    }

    @Test
    fun `reset recarrega pelo indice em vez de baixar tudo do backend`() = runTest {
        val store = InMemoryCatalogStore()
        store.apply(CatalogBatch(upserts = listOf(movie(999, "Velho")), cursor = CatalogCursor("2026-01-01T00:00:00Z", "m_999")))
        val calls = mutableListOf<CatalogCursor>()
        val pages = ArrayDeque(listOf(
            page(listOf(item("movie", "m_1", "t", """{"tmdb_id":1,"title":"X"}""")), "t", "m_1", hasMore = true, reset = true),
            page(emptyList(), null, null, hasMore = false)
        ))
        val result = sync(store, pages, calls).syncOnce()

        // O item velho sumiu (reset) e o catálogo é o do índice; a página do reset foi descartada.
        assertEquals(setOf("m_603", "m_604"), store.rows.keys)
        assertEquals(SyncTime.toIso(generatedAt - 6 * 60 * 60_000L), calls[1].since)
        assertTrue(result is CatalogSyncResult.Done && result.seeded)
    }

    @Test
    fun `reset repetido logo apos recarregar para sem laco`() = runTest {
        val store = InMemoryCatalogStore()
        val resetPage = page(emptyList(), null, null, hasMore = true, reset = true)
        val result = sync(store, ArrayDeque(listOf(resetPage, resetPage))).syncOnce()
        assertTrue(result is CatalogSyncResult.Failed)
    }

    @Test
    fun `flag desligada nao faz nada e rodada recente e pulada`() = runTest {
        val store = InMemoryCatalogStore()
        val calls = mutableListOf<CatalogCursor>()
        assertEquals(CatalogSyncResult.Disabled, sync(store, ArrayDeque(), calls, enabled = false).syncOnce())
        assertEquals(0, store.count())

        store.apply(CatalogBatch(cursor = CatalogCursor("s", null, lastSyncAt = now - 60_000L)))
        assertEquals(CatalogSyncResult.Skipped, sync(store, ArrayDeque(), calls).syncOnce())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `429 respeita o Retry-After e mantem o cursor`() = runTest {
        val requests = mutableListOf<BackendRequest>()
        val transport = HttpTransport { r ->
            requests += r
            BackendResponse(429, """{"code":"RATE_LIMITED"}""", retryAfterSeconds = 120)
        }
        val client = SupabaseClient(BackendConfig("http://local", "anon"), transport, now = { now })
        val store = InMemoryCatalogStore()
        val cursor = CatalogCursor("2026-10-01T00:00:00Z", "m_1")
        store.apply(CatalogBatch(cursor = cursor))
        val sync = CatalogSync(store, CatalogSync.rpcFetcher(client), { null }, { true }, clock = { now })

        assertEquals(CatalogSyncResult.RateLimited, sync.syncOnce())
        val body = JSONObject(requests.single().body!!)
        assertEquals("2026-10-01T00:00:00Z", body.getString("since"))
        assertEquals("m_1", body.getString("after_id"))
        assertTrue(requests.single().url.endsWith("/rest/v1/rpc/catalog_changes"))
        assertEquals(cursor, store.state)

        // Dentro do Retry-After nem vai à rede.
        now += 60_000L
        assertEquals(CatalogSyncResult.RateLimited, sync.syncOnce(force = true))
        assertEquals(1, requests.size)
    }

    @Test
    fun `parse aceita next nulo e campos ausentes`() {
        val p = CatalogChangesPage.parse("""{"items":[],"next":{"since":null,"after_id":null},"has_more":false,"reset":false}""")
        assertTrue(p.items.isEmpty())
        assertNull(p.next.since)
        assertFalse(p.hasMore)
        assertFalse(BackendException(500, "x").isRateLimited)
    }
}
