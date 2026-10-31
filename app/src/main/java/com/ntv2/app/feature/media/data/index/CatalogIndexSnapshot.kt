package com.ntv2.app.feature.media.data.index

import com.ntv2.app.core.catalog.CatalogRow
import com.ntv2.app.core.catalog.CatalogStore
import com.ntv2.app.core.catalog.KIND_EPISODE
import com.ntv2.app.core.catalog.KIND_MOVIE
import com.ntv2.app.core.catalog.KIND_SERIES
import com.ntv2.app.core.telegram.media.CastMemberMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catálogo local → os mesmos modelos do `index.json` ([IndexMovie]/[IndexSeries]). A busca continua
 * igual (em memória, sem acento, elenco, filtros, erro de digitação); só muda de onde vem a cópia:
 * do banco, sem baixar o arquivo. O caminho inverso é o [IndexCatalogSeed].
 */
object CatalogIndexSnapshot {
    private const val PAGE = 500
    private const val TMDB_LOGO = "https://image.tmdb.org/t/p/w500"

    /** Lê o banco em páginas (memória baixa no Fire TV). Null se o catálogo estiver vazio. */
    suspend fun load(store: CatalogStore): IndexSnapshot? = withContext(Dispatchers.IO) {
        val builder = Builder()
        var after = ""
        while (true) {
            val page = store.page(after, PAGE)
            page.forEach(builder::add)
            if (page.size < PAGE) break
            after = page.last().ntvId
        }
        builder.build(store.cursor()?.lastSyncAt?.takeIf { it > 0 })
    }

    class Builder {
        private val movies = ArrayList<IndexMovie>()
        private val series = HashMap<Long, JSONObject>()
        private val episodes = HashMap<Long, MutableList<IndexEpisode>>()
        private val chatCounts = HashMap<Long, Int>()

        fun add(row: CatalogRow) {
            val d = runCatching { JSONObject(row.data) }.getOrNull() ?: return
            row.chatId?.let { chatCounts[it] = (chatCounts[it] ?: 0) + 1 }
            when (row.kind) {
                KIND_MOVIE -> row.tmdbId?.let { movies += movie(it, d) }
                KIND_SERIES -> row.tmdbId?.let { series[it] = d }
                KIND_EPISODE -> row.tmdbId?.let { s -> episode(d)?.let { episodes.getOrPut(s) { ArrayList() } += it } }
            }
        }

        fun build(updatedAtMillis: Long?): IndexSnapshot? {
            if (movies.isEmpty() && series.isEmpty()) return null
            val seriesList = series.map { (tmdbId, d) ->
                val seasons = episodes[tmdbId].orEmpty()
                    .groupBy { it.seasonNumber }
                    .map { (n, eps) -> IndexSeason(n, eps.sortedBy { it.episodeNumber }) }
                    .sortedBy { it.number }
                IndexSeries(
                    tmdbId = tmdbId,
                    title = d.text("title") ?: "Série",
                    originalTitle = d.text("original_title"),
                    posterUrl = d.text("poster_url"),
                    backdropUrl = d.text("backdrop_url"),
                    logoUrl = d.logo(),
                    overview = d.text("overview"),
                    genres = d.strings("genres"),
                    seasons = seasons
                )
            }
            // Mesma ordem do index.json (gerar_indice.py: título em minúsculas).
            return IndexSnapshot(
                channelId = chatCounts.maxByOrNull { it.value }?.key ?: 0L,
                movies = movies.sortedBy { it.title.lowercase() },
                series = seriesList.sortedBy { it.title.lowercase() },
                generatedAtMillis = updatedAtMillis
            )
        }

        private fun movie(tmdbId: Long, d: JSONObject): IndexMovie {
            val castArr = d.optJSONArray("cast")
            val cast = if (castArr != null) (0 until castArr.length()).mapNotNull { i ->
                val c = castArr.optJSONObject(i) ?: return@mapNotNull null
                val name = c.text("name") ?: return@mapNotNull null
                CastMemberMeta(name, c.text("profile_url") ?: c.text("photo_url"))
            } else emptyList()
            return IndexMovie(
                tmdbId = tmdbId,
                title = d.text("title") ?: "Filme",
                originalTitle = d.text("original_title"),
                // O índice traz o ano como texto; o backend, como número.
                year = d.text("year")?.take(4),
                posterUrl = d.text("poster_url"),
                backdropUrl = d.text("backdrop_url"),
                logoUrl = d.logo(),
                overview = d.text("overview"),
                genres = d.strings("genres"),
                cast = cast,
                videoMessageId = d.optLong("video_message_id", 0L)
            )
        }

        private fun episode(d: JSONObject): IndexEpisode? {
            val season = d.optInt("season_number", 0)
            val number = d.optInt("episode_number", 0)
            if (season <= 0 || number <= 0) return null
            return IndexEpisode(
                episodeTmdbId = d.optLong("episode_tmdb_id", 0L).takeIf { it != 0L },
                seasonNumber = season,
                episodeNumber = number,
                title = d.text("title"),
                overview = d.text("overview"),
                airDate = d.text("air_date"),
                durationMin = d.optInt("duration_min", 0).takeIf { it != 0 },
                posterUrl = d.text("poster_url"),
                backdropUrl = d.text("backdrop_url"),
                logoUrl = d.logo(),
                quality = d.text("quality"),
                audio = d.text("audio"),
                videoMessageId = d.optLong("video_message_id", 0L)
            )
        }

        private fun JSONObject.text(key: String): String? {
            if (isNull(key)) return null
            return optString(key).trim().ifBlank { null }?.takeUnless { it.equals("null", true) || it.equals("None", true) }
        }

        private fun JSONObject.logo(): String? = text("logo_url")?.let { if (it.startsWith("/")) TMDB_LOGO + it else it }

        private fun JSONObject.strings(key: String): List<String> {
            val arr: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).trim().ifBlank { null } }
        }
    }
}
