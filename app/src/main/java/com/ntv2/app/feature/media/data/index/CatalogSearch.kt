package com.ntv2.app.feature.media.data.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Tipo pedido na busca do catálogo (valores do RPC search_catalog). */
enum class CatalogKind(val wire: String) { ALL("all"), MOVIE("movie"), SERIES("series") }

/** Busca no catálogo: índice local em memória ou backend. Mesmos modelos dos dois lados. */
interface CatalogSearch {
    /** Esta implementação sabe responder essa combinação de filtros? */
    fun supports(query: String, genres: Set<String>, years: Set<Int>, actor: String?): Boolean = true

    suspend fun search(
        channelId: Long,
        query: String,
        genres: Set<String>,
        years: Set<Int>,
        actor: String?,
        kind: CatalogKind = CatalogKind.ALL,
        limit: Int = 60
    ): Pair<List<IndexMovie>, List<IndexSeries>>
}

/** Índice local (GitHub Pages). O [kind] é aplicado depois, na tela, como sempre foi. */
class LocalCatalogSearch(private val index: SearchIndexRepository) : CatalogSearch {
    override suspend fun search(
        channelId: Long,
        query: String,
        genres: Set<String>,
        years: Set<Int>,
        actor: String?,
        kind: CatalogKind,
        limit: Int
    ) = index.searchFiltered(channelId, query, genres, years, actor, limit)
}

/**
 * Remoto quando [remoteEnabled] (backend ligado e flag remote_search não desligada) e o remoto
 * suporta os filtros; senão, ou em erro, timeout ([timeoutMs]) ou resultado vazio, usa o [local].
 *
 * O remoto roda em [scope] próprio: a chamada HTTP é bloqueante e não para no cancelamento, então
 * o timeout precisa abandonar a espera em vez de esperar o socket fechar.
 */
class FallbackCatalogSearch(
    private val remote: CatalogSearch?,
    private val local: CatalogSearch,
    private val backendEnabled: () -> Boolean,
    /** Lido dentro do timeout: pode ir à rede. */
    private val remoteEnabled: suspend () -> Boolean = { true },
    private val timeoutMs: Long = 3_000L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : CatalogSearch {

    override suspend fun search(
        channelId: Long,
        query: String,
        genres: Set<String>,
        years: Set<Int>,
        actor: String?,
        kind: CatalogKind,
        limit: Int
    ): Pair<List<IndexMovie>, List<IndexSeries>> {
        val r = remote
        if (r != null && backendEnabled() && r.supports(query, genres, years, actor)) {
            val call = scope.async {
                if (!remoteEnabled()) null
                else r.search(channelId, query, genres, years, actor, kind, limit)
            }
            val result = try {
                withTimeoutOrNull(timeoutMs) { call.await() }
            } catch (e: Exception) {
                // Cancelado por fora (nova busca): propaga. Erro do remoto: cai no local.
                currentCoroutineContext().ensureActive()
                null
            } finally {
                call.cancel()
            }
            if (result != null && (result.first.isNotEmpty() || result.second.isNotEmpty())) return result
        }
        return local.search(channelId, query, genres, years, actor, kind, limit)
    }
}
