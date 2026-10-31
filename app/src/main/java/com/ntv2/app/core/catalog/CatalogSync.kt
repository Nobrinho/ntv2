package com.ntv2.app.core.catalog

import com.ntv2.app.core.backend.BackendException
import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.backend.sync.SyncTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Primeira carga: o `index.json` já convertido em linhas, e quando o bot o gerou (ms). */
class CatalogSeed(val rows: List<CatalogRow>, val generatedAtMillis: Long?)

sealed interface CatalogSyncResult {
    /** Flag `remote_catalog` desligada (ou backend desligado): nada foi feito. */
    data object Disabled : CatalogSyncResult
    /** Rodada recente: não chama o backend de novo. */
    data object Skipped : CatalogSyncResult
    /** Precisava da primeira carga e o `index.json` não veio: tenta na próxima rodada. */
    data object NoSeed : CatalogSyncResult
    data class Done(val pages: Int, val upserts: Int, val removed: Int, val seeded: Boolean) : CatalogSyncResult
    /** 429: o [SupabaseClient] já segura a rota até o Retry-After; a próxima rodada continua do cursor. */
    data object RateLimited : CatalogSyncResult
    data class Failed(val reason: String) : CatalogSyncResult
}

/**
 * Mantém o [CatalogStore] em dia com o backend:
 * 1. Sem cursor (nunca carregou): primeira carga pelo `index.json` do GitHub Pages ([loadSeed]), não
 *    pelo backend: uma página cheia de `catalog_changes` tem ~1,2 MB. O cursor começa em
 *    `generated_at` menos [seedMarginMs], para cobrir o que mudou enquanto o índice era publicado.
 * 2. Depois só a diferença: `catalog_changes` página a página (cursor `next`, até `has_more = false`),
 *    cada página gravada numa transação junto com o cursor (interrompeu = continua dali).
 * 3. `reset = true` (cursor com mais de 90 dias): refaz a primeira carga pelo `index.json` em vez de
 *    baixar o catálogo inteiro do backend.
 *
 * Tudo em background; a biblioteca nunca espera por isto (sem catálogo, o cartão vem da legenda).
 */
class CatalogSync(
    private val store: CatalogStore,
    private val fetchChanges: suspend (CatalogCursor) -> String,
    private val loadSeed: suspend () -> CatalogSeed?,
    private val enabled: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = 30 * 60_000L,
    private val maxPagesPerRun: Int = 50,
    private val seedMarginMs: Long = 6 * 60 * 60_000L,
    private val seedChunk: Int = 500
) {
    private val mutex = Mutex()

    suspend fun syncOnce(force: Boolean = false): CatalogSyncResult = mutex.withLock {
        if (!enabled()) return CatalogSyncResult.Disabled
        var cursor = store.cursor()
        if (cursor != null && !force && clock() - cursor.lastSyncAt in 0 until minIntervalMs) {
            return CatalogSyncResult.Skipped
        }
        var seeded = false
        var pages = 0
        var upserts = 0
        var removed = 0
        try {
            if (cursor == null) {
                cursor = seed() ?: return CatalogSyncResult.NoSeed
                seeded = true
            }
            while (pages < maxPagesPerRun) {
                val page = CatalogChangesPage.parse(fetchChanges(cursor!!))
                pages++
                if (page.reset) {
                    // O backend repete o reset enquanto o cursor for velho: recarregar 1x por rodada basta.
                    if (seeded) return CatalogSyncResult.Failed("reset logo depois da primeira carga")
                    cursor = seed() ?: return CatalogSyncResult.NoSeed
                    seeded = true
                    continue
                }
                val done = !page.hasMore
                val position = if (page.items.isEmpty()) cursor else page.next
                val next = position.copy(lastSyncAt = if (done) clock() else cursor.lastSyncAt)
                val batch = CatalogMerge.batchFor(page.items, next)
                store.apply(batch)
                upserts += batch.upserts.size
                removed += batch.removed.size
                cursor = next
                if (done) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendException) {
            return if (e.isRateLimited) CatalogSyncResult.RateLimited else CatalogSyncResult.Failed(e.message.orEmpty())
        } catch (e: Exception) {
            return CatalogSyncResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        CatalogSyncResult.Done(pages, upserts, removed, seeded)
    }

    /**
     * Apaga o catálogo e grava o [CatalogSeed] em lotes (cada um numa transação curta, para não segurar
     * o banco). O cursor só é gravado no último lote: se parar no meio, a próxima rodada recomeça.
     */
    private suspend fun seed(): CatalogCursor? {
        val seed = loadSeed() ?: return null
        if (seed.rows.isEmpty()) return null
        val sinceMs = seed.generatedAtMillis?.let { it - seedMarginMs } ?: (clock() - NO_DATE_MARGIN_MS)
        val cursor = CatalogCursor(since = SyncTime.toIso(sinceMs), afterId = null, lastSyncAt = 0L)
        val chunks = seed.rows.chunked(seedChunk)
        chunks.forEachIndexed { i, chunk ->
            store.apply(CatalogBatch(upserts = chunk, reset = i == 0, cursor = if (i == chunks.lastIndex) cursor else null))
        }
        return cursor
    }

    /** Roda na abertura (depois de [initialDelayMs], para não disputar com a biblioteca) e a cada [periodMs]. */
    fun start(
        scope: CoroutineScope,
        initialDelayMs: Long = 20_000L,
        periodMs: Long = 6 * 60 * 60_000L,
        onResult: (CatalogSyncResult) -> Unit = {}
    ): Job = scope.launch {
        delay(initialDelayMs)
        while (isActive) {
            val result = runCatching { syncOnce() }
                .getOrElse { if (it is CancellationException) throw it else CatalogSyncResult.Failed(it.message.orEmpty()) }
            onResult(result)
            delay(periodMs)
        }
    }

    companion object {
        /** `index.json` sem `generated_at`: pede uma semana de diferença (barato) em vez de tudo. */
        private const val NO_DATE_MARGIN_MS = 7L * 24 * 60 * 60_000L
        const val PAGE_LIMIT = 200

        /** Busca uma página de `catalog_changes` (rate limit e Retry-After ficam com o [SupabaseClient]). */
        fun rpcFetcher(client: SupabaseClient, limit: Int = PAGE_LIMIT): suspend (CatalogCursor) -> String = { c ->
            val body = JSONObject()
                .put("since", c.since ?: JSONObject.NULL)
                .put("after_id", c.afterId ?: JSONObject.NULL)
                .put("lim", limit)
            client.rpc("catalog_changes", body.toString())
        }
    }
}
