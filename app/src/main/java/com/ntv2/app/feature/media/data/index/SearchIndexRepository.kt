package com.ntv2.app.feature.media.data.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.ntv2.app.core.telegram.media.CastMemberMeta
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.Normalizer

/** Um filme do índice hospedado (schema v1). */
data class IndexMovie(
    val tmdbId: Long,
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val genres: List<String>,
    val cast: List<CastMemberMeta>,
    val videoMessageId: Long
) {
    val searchKey: String = normalizeForIndex(listOfNotNull(title, originalTitle).joinToString(" "))
}

private data class LoadedIndex(val channelId: Long, val movies: List<IndexMovie>)

/**
 * Baixa (1x por sessão) o índice de filmes publicado pelo bot (GitHub Pages) e permite busca
 * local instantânea por título, sem round-trip no TDLib. Cobre apenas o canal do índice.
 */
class SearchIndexRepository(
    private val indexUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    // Revalida após esse tempo; enquanto isso usa o cache em memória.
    private val ttlMillis: Long = 6 * 60 * 60 * 1000L
) {
    private val mutex = Mutex()
    @Volatile private var cached: LoadedIndex? = null
    @Volatile private var loadedAt = 0L

    private suspend fun ensureLoaded(): LoadedIndex? {
        val now = System.currentTimeMillis()
        cached?.let { if (now - loadedAt < ttlMillis) return it }
        return mutex.withLock {
            val fresh = cached
            if (fresh != null && System.currentTimeMillis() - loadedAt < ttlMillis) return fresh
            val loaded = runCatching { fetch() }.getOrNull()
            if (loaded != null) {
                cached = loaded
                loadedAt = System.currentTimeMillis()
            }
            loaded ?: cached // se falhar o fetch, mantém o que tiver
        }
    }

    private suspend fun fetch(): LoadedIndex? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(indexUrl).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            parse(body)
        }
    }

    private fun parse(body: String): LoadedIndex {
        val root = JSONObject(body)
        val channelId = root.optString("channel").toLongOrNull() ?: 0L
        val arr = root.optJSONArray("movies") ?: return LoadedIndex(channelId, emptyList())
        val movies = ArrayList<IndexMovie>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mids = o.optJSONArray("message_ids")
            val videoMsg = o.optLong("video_message_id", 0L).takeIf { it != 0L }
                ?: (mids?.let { if (it.length() > 0) it.optLong(it.length() - 1) else 0L } ?: 0L)
            val genresArr = o.optJSONArray("genres")
            val genres = if (genresArr != null) (0 until genresArr.length()).map { genresArr.optString(it) } else emptyList()
            val castArr = o.optJSONArray("cast")
            val cast = if (castArr != null) (0 until castArr.length()).mapNotNull { ci ->
                val co = castArr.optJSONObject(ci) ?: return@mapNotNull null
                val nome = co.optString("name").ifBlank { null } ?: return@mapNotNull null
                CastMemberMeta(name = nome, photoUrl = co.optString("photo_url").ifBlank { null })
            } else emptyList()
            movies += IndexMovie(
                tmdbId = o.optLong("tmdb_id"),
                title = o.optString("title").ifBlank { "Filme" },
                originalTitle = o.optString("original_title").ifBlank { null },
                year = o.optString("year").ifBlank { null },
                posterUrl = o.optString("poster_url").ifBlank { null },
                backdropUrl = o.optString("backdrop_url").ifBlank { null },
                overview = o.optString("overview").ifBlank { null },
                genres = genres,
                cast = cast,
                videoMessageId = videoMsg
            )
        }
        return LoadedIndex(channelId, movies)
    }

    /** True se o índice cobre esse canal (carrega se preciso). */
    suspend fun covers(channelId: Long): Boolean {
        val idx = ensureLoaded() ?: return false
        return idx.channelId == channelId && idx.movies.isNotEmpty()
    }

    /** Busca local por título/título original (sem acento/caixa). Vazio se não cobrir o canal. */
    suspend fun search(channelId: Long, query: String, limit: Int = 60): List<IndexMovie> {
        val idx = ensureLoaded() ?: return emptyList()
        if (idx.channelId != channelId) return emptyList()
        val tokens = normalizeForIndex(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return idx.movies.asSequence()
            .filter { m -> tokens.all { m.searchKey.contains(it) } }
            .take(limit)
            .toList()
    }
}

internal fun normalizeForIndex(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
