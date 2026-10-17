package com.ntv2.app.feature.media.data.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackCatalogSearchTest {

    private fun movie(id: Long) = IndexMovie(
        tmdbId = id, title = "Filme $id", originalTitle = null, year = "2020", posterUrl = null,
        backdropUrl = null, logoUrl = null, overview = null, genres = emptyList(), cast = emptyList(),
        videoMessageId = id
    )

    private class FakeSearch(
        val supported: Boolean = true,
        val answer: suspend () -> Pair<List<IndexMovie>, List<IndexSeries>>
    ) : CatalogSearch {
        var calls = 0
        var lastKind: CatalogKind? = null
        override fun supports(query: String, genres: Set<String>, years: Set<Int>, actor: String?) = supported
        override suspend fun search(
            channelId: Long, query: String, genres: Set<String>, years: Set<Int>,
            actor: String?, kind: CatalogKind, limit: Int
        ): Pair<List<IndexMovie>, List<IndexSeries>> {
            calls++
            lastKind = kind
            return answer()
        }
    }

    private val localResult = listOf(movie(1)) to emptyList<IndexSeries>()
    private val remoteResult = listOf(movie(2)) to emptyList<IndexSeries>()

    private fun TestScope.chooser(
        remote: CatalogSearch?,
        local: CatalogSearch,
        backendOn: Boolean = true,
        flagOn: Boolean = true
    ) = FallbackCatalogSearch(
        remote = remote,
        local = local,
        backendEnabled = { backendOn },
        remoteEnabled = { flagOn },
        // Como em produção (SupervisorJob): falha do remoto fica no Deferred, não derruba o teste.
        scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))
    )

    private suspend fun CatalogSearch.find() =
        search(0L, "filme", emptySet(), emptySet(), null, CatalogKind.SERIES, 60)

    @Test
    fun `usa o remoto com backend ligado e flag ligada`() = runTest {
        val remote = FakeSearch { remoteResult }
        val local = FakeSearch { localResult }
        assertEquals(remoteResult, chooser(remote, local).find())
        assertEquals(0, local.calls)
        assertEquals(CatalogKind.SERIES, remote.lastKind)
    }

    @Test
    fun `backend desligado usa so o local`() = runTest {
        val remote = FakeSearch { remoteResult }
        val local = FakeSearch { localResult }
        assertEquals(localResult, chooser(remote, local, backendOn = false).find())
        assertEquals(0, remote.calls)
        assertEquals(localResult, chooser(null, local).find())
    }

    @Test
    fun `flag remote_search desligada usa o local`() = runTest {
        val remote = FakeSearch { remoteResult }
        val local = FakeSearch { localResult }
        assertEquals(localResult, chooser(remote, local, flagOn = false).find())
        assertEquals(0, remote.calls)
    }

    @Test
    fun `filtros que o remoto nao suporta usam o local`() = runTest {
        val remote = FakeSearch(supported = false) { remoteResult }
        val local = FakeSearch { localResult }
        assertEquals(localResult, chooser(remote, local).find())
        assertEquals(0, remote.calls)
    }

    @Test
    fun `erro no remoto cai no local`() = runTest {
        val remote = FakeSearch { throw java.io.IOException("sem rede") }
        val local = FakeSearch { localResult }
        assertEquals(localResult, chooser(remote, local).find())
        assertEquals(1, local.calls)
    }

    @Test
    fun `remoto vazio cai no local`() = runTest {
        val remote = FakeSearch { emptyList<IndexMovie>() to emptyList() }
        val local = FakeSearch { localResult }
        assertEquals(localResult, chooser(remote, local).find())
    }

    @Test
    fun `remoto lento passa do timeout de 3s e cai no local`() = runTest {
        val remote = FakeSearch { delay(10_000); remoteResult }
        val local = FakeSearch { localResult }
        val result = chooser(remote, local).find()
        assertEquals(localResult, result)
        assertTrue("esperou ${currentTime}ms", currentTime in 3_000L until 10_000L)
    }
}
