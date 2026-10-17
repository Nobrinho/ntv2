package com.ntv2.app.core.backend.catalog

import com.ntv2.app.core.backend.SupabaseClient
import com.ntv2.app.core.telegram.media.CastMemberMeta
import com.ntv2.app.feature.media.data.index.CatalogKind
import com.ntv2.app.feature.media.data.index.CatalogSearch
import com.ntv2.app.feature.media.data.index.IndexEpisode
import com.ntv2.app.feature.media.data.index.IndexMovie
import com.ntv2.app.feature.media.data.index.IndexSeason
import com.ntv2.app.feature.media.data.index.IndexSeries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Uma página do RPC search_catalog (séries ainda sem temporadas). */
data class RemoteCatalogPage(
    val movies: List<IndexMovie>,
    val series: List<IndexSeries>,
    val total: Int
)

/**
 * Busca no catálogo do backend (RPC search_catalog + series_episodes, ver CONTRATO.md), devolvendo os
 * mesmos modelos do índice local. O catálogo é o do canal do índice: [channelId] não vai ao servidor.
 */
class RemoteCatalogSearch(
    private val client: SupabaseClient,
    private val episodeConcurrency: Int = 4
) : CatalogSearch {
    // Temporadas por série: a tela de série é aberta várias vezes a partir da mesma busca.
    private val seasonsCache = ConcurrentHashMap<Long, List<IndexSeason>>()

    /** O RPC filtra por um gênero e um ano e não tem filtro de ator; busca só por filtro fica local. */
    override fun supports(query: String, genres: Set<String>, years: Set<Int>, actor: String?): Boolean =
        query.isNotBlank() && genres.size <= 1 && years.size <= 1 && actor == null

    override suspend fun search(
        channelId: Long,
        query: String,
        genres: Set<String>,
        years: Set<Int>,
        actor: String?,
        kind: CatalogKind,
        limit: Int
    ): Pair<List<IndexMovie>, List<IndexSeries>> {
        val page = searchPage(query, kind, genres.firstOrNull(), years.firstOrNull(), limit, 0)
        return page.movies to withSeasons(page.series)
    }

    suspend fun searchPage(
        query: String,
        kind: CatalogKind = CatalogKind.ALL,
        genre: String? = null,
        year: Int? = null,
        limit: Int = 30,
        offset: Int = 0
    ): RemoteCatalogPage {
        val body = JSONObject()
            .put("q", query.trim())
            .put("kind", kind.wire)
            .put("genre", genre ?: JSONObject.NULL)
            .put("year", year?.toString() ?: JSONObject.NULL)
            .put("lim", limit)
            .put("off", offset)
        return parseSearchCatalog(client.rpc("search_catalog", body.toString()))
    }

    suspend fun seriesEpisodes(seriesTmdbId: Long): List<IndexSeason> {
        seasonsCache[seriesTmdbId]?.let { return it }
        val body = JSONObject().put("series_tmdb_id", seriesTmdbId).toString()
        return parseSeriesEpisodes(client.rpc("series_episodes", body)).also { seasonsCache[seriesTmdbId] = it }
    }

    private suspend fun withSeasons(series: List<IndexSeries>): List<IndexSeries> {
        if (series.isEmpty()) return series
        val permits = Semaphore(episodeConcurrency)
        return coroutineScope {
            series.map { s ->
                async { permits.withPermit { s.copy(seasons = seriesEpisodes(s.tmdbId)) } }
            }.awaitAll()
        }
    }
}

/** Saída do search_catalog: `{ movies: [...], series: [...], total }`. */
internal fun parseSearchCatalog(json: String): RemoteCatalogPage {
    val root = JSONObject(json)
    val movies = root.optJSONArray("movies").objects().map(::parseCatalogMovie)
    val series = root.optJSONArray("series").objects().map(::parseCatalogSeries)
    return RemoteCatalogPage(movies, series, root.optInt("total", movies.size + series.size))
}

/** Saída do series_episodes: `[{ number, episodes: [...] }]`. */
internal fun parseSeriesEpisodes(json: String): List<IndexSeason> =
    JSONArray(json).objects().map { o ->
        val number = o.optInt("number", 0)
        IndexSeason(
            number = number,
            episodes = o.optJSONArray("episodes").objects()
                .map { parseCatalogEpisode(it, number) }
                .sortedBy { it.episodeNumber }
        )
    }.sortedBy { it.number }

private fun parseCatalogMovie(o: JSONObject) = IndexMovie(
    tmdbId = o.optLong("tmdb_id"),
    title = o.text("title") ?: "Filme",
    originalTitle = o.text("original_title"),
    year = o.text("year"),
    posterUrl = o.text("poster_url"),
    backdropUrl = o.text("backdrop_url"),
    logoUrl = o.image("logo_url"),
    overview = o.text("overview"),
    genres = o.strings("genres"),
    cast = o.optJSONArray("cast").objects().mapNotNull { c ->
        val name = c.text("name") ?: return@mapNotNull null
        CastMemberMeta(name = name, photoUrl = c.text("profile_url") ?: c.text("photo_url"))
    },
    videoMessageId = o.optLong("video_message_id", 0L)
)

private fun parseCatalogSeries(o: JSONObject) = IndexSeries(
    tmdbId = o.optLong("tmdb_id"),
    title = o.text("title") ?: "Série",
    originalTitle = o.text("original_title"),
    posterUrl = o.text("poster_url"),
    backdropUrl = o.text("backdrop_url"),
    logoUrl = o.image("logo_url"),
    overview = o.text("overview"),
    genres = o.strings("genres"),
    seasons = emptyList()
)

private fun parseCatalogEpisode(o: JSONObject, seasonNumber: Int) = IndexEpisode(
    episodeTmdbId = o.optLong("episode_tmdb_id", 0L).takeIf { it != 0L },
    seasonNumber = o.optInt("season_number", seasonNumber),
    episodeNumber = o.optInt("episode_number", 0),
    title = o.text("title"),
    overview = o.text("overview"),
    airDate = o.text("air_date"),
    durationMin = o.optInt("duration_min", 0).takeIf { it != 0 },
    posterUrl = o.text("poster_url"),
    backdropUrl = o.text("backdrop_url"),
    logoUrl = o.image("logo_url"),
    quality = o.text("quality"),
    audio = o.text("audio"),
    videoMessageId = o.optLong("video_message_id", 0L)
)

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.strings(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() && !arr.isNull(i) } }
}

/** Texto ausente, vazio ou JSON null vira null (optString devolveria "null"). */
private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).trim().ifBlank { null }

/** Como no índice: aceita URL completa ou caminho TMDB ("/abc.png"). */
private fun JSONObject.image(key: String): String? =
    text(key)?.let { if (it.startsWith("/")) TMDB_LOGO + it else it }

private const val TMDB_LOGO = "https://image.tmdb.org/t/p/w500"
