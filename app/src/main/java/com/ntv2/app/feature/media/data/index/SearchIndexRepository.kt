package com.ntv2.app.feature.media.data.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.ntv2.app.core.telegram.media.CastMemberMeta
import okhttp3.CacheControl
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

/** Um episódio do índice (schema v2). Unidade reproduzível: resolve o vídeo por [videoMessageId]. */
data class IndexEpisode(
    val episodeTmdbId: Long?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val overview: String?,
    val airDate: String?,
    val durationMin: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val quality: String?,
    val audio: String?,
    val videoMessageId: Long
) {
    /** Código canônico "S01E02" (dois dígitos). */
    val code: String = "S%02dE%02d".format(seasonNumber, episodeNumber)
}

/** Uma temporada: número + episódios em ordem. */
data class IndexSeason(val number: Int, val episodes: List<IndexEpisode>)

/** Uma série do índice (schema v2), agrupando temporadas/episódios. */
data class IndexSeries(
    val tmdbId: Long,
    val title: String,
    val originalTitle: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val genres: List<String>,
    val seasons: List<IndexSeason>
) {
    /** Chave de busca: título da série + títulos dos episódios + códigos SxxExx. */
    val searchKey: String = normalizeForIndex(
        buildString {
            append(title)
            originalTitle?.let { append(' ').append(it) }
            seasons.forEach { s ->
                s.episodes.forEach { e ->
                    append(' ').append(e.code)
                    e.title?.let { append(' ').append(it) }
                }
            }
        }
    )
}

private data class LoadedIndex(
    val channelId: Long,
    val movies: List<IndexMovie>,
    val series: List<IndexSeries> = emptyList(),
    /** Quando o bot gerou o índice ("generated_at"), em ms; null se o JSON não trouxer. */
    val generatedAtMillis: Long? = null
)

/** Situação do índice para a tela de Configurações. */
data class SearchIndexStatus(
    /** Quando o bot gerou o índice que está em uso (ms); null se desconhecido. */
    val generatedAtMillis: Long? = null,
    /** Quando este aparelho baixou o índice em uso (ms); 0 = ainda não baixou. */
    val downloadedAtMillis: Long = 0L,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val refreshing: Boolean = false,
    /** Última atualização manual falhou (mantém o índice anterior). */
    val failed: Boolean = false
)

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

    private val _status = MutableStateFlow(SearchIndexStatus())
    /** Data do índice em uso e se há download manual em andamento. */
    val status: StateFlow<SearchIndexStatus> = _status.asStateFlow()

    private suspend fun ensureLoaded(): LoadedIndex? {
        val now = System.currentTimeMillis()
        cached?.let { if (now - loadedAt < ttlMillis) return it }
        return mutex.withLock {
            val fresh = cached
            if (fresh != null && System.currentTimeMillis() - loadedAt < ttlMillis) return fresh
            val loaded = runCatching { fetch() }.getOrNull()
            if (loaded != null) store(loaded)
            loaded ?: cached // se falhar o fetch, mantém o que tiver
        }
    }

    /**
     * Baixa o índice agora, ignorando o cache de [ttlMillis] (botão em Configurações). Se falhar,
     * mantém o índice anterior. Retorna true se baixou.
     */
    suspend fun refresh(): Boolean {
        _status.update { it.copy(refreshing = true, failed = false) }
        var ok = false
        try {
            mutex.withLock {
                val loaded = runCatching { fetch(forceNetwork = true) }.getOrNull()
                if (loaded != null) {
                    store(loaded)
                    ok = true
                }
            }
        } finally {
            _status.update { it.copy(refreshing = false, failed = !ok) }
        }
        return ok
    }

    private fun store(loaded: LoadedIndex) {
        cached = loaded
        loadedAt = System.currentTimeMillis()
        _status.update {
            it.copy(
                generatedAtMillis = loaded.generatedAtMillis,
                downloadedAtMillis = loadedAt,
                movieCount = loaded.movies.size,
                seriesCount = loaded.series.size
            )
        }
    }

    private suspend fun fetch(forceNetwork: Boolean = false): LoadedIndex? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .apply {
                if (forceNetwork) {
                    // O GitHub Pages guarda o arquivo no CDN por 10 min: o parâmetro muda a URL
                    // para pegar a versão mais nova publicada pelo bot.
                    url("$indexUrl?t=${System.currentTimeMillis()}")
                    cacheControl(CacheControl.FORCE_NETWORK)
                } else {
                    url(indexUrl)
                }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            parse(body)
        }
    }

    private fun parse(body: String): LoadedIndex {
        val root = JSONObject(body)
        val channelId = root.optString("channel").toLongOrNull() ?: 0L
        val generatedAt = parseGeneratedAt(root.optString("generated_at"))
        val arr = root.optJSONArray("movies") ?: return LoadedIndex(channelId, emptyList(), generatedAtMillis = generatedAt)
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
        return LoadedIndex(channelId, movies, parseSeries(root), generatedAt)
    }

    private fun parseSeries(root: JSONObject): List<IndexSeries> {
        val arr = root.optJSONArray("series") ?: return emptyList()
        val out = ArrayList<IndexSeries>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val genresArr = o.optJSONArray("genres")
            val genres = if (genresArr != null) (0 until genresArr.length()).map { genresArr.optString(it) } else emptyList()
            out += IndexSeries(
                tmdbId = o.optLong("tmdb_id"),
                title = o.optString("title").ifBlank { "Série" },
                originalTitle = o.optString("original_title").ifBlank { null },
                posterUrl = o.optString("poster_url").ifBlank { null },
                backdropUrl = o.optString("backdrop_url").ifBlank { null },
                overview = o.cleanString("overview"),
                genres = genres,
                seasons = parseSeasons(o.optJSONArray("seasons"))
            )
        }
        return out
    }

    private fun parseSeasons(arr: org.json.JSONArray?): List<IndexSeason> {
        if (arr == null) return emptyList()
        val out = ArrayList<IndexSeason>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val number = o.optInt("number", o.optInt("season_number", 0))
            out += IndexSeason(
                number = number,
                episodes = parseEpisodes(o.optJSONArray("episodes"), number)
            )
        }
        return out.sortedBy { it.number }
    }

    private fun parseEpisodes(arr: org.json.JSONArray?, seasonNumber: Int): List<IndexEpisode> {
        if (arr == null) return emptyList()
        val out = ArrayList<IndexEpisode>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mids = o.optJSONArray("message_ids")
            val videoMsg = o.optLong("video_message_id", 0L).takeIf { it != 0L }
                ?: (mids?.let { if (it.length() > 0) it.optLong(it.length() - 1) else 0L } ?: 0L)
            out += IndexEpisode(
                episodeTmdbId = o.optLong("episode_tmdb_id", 0L).takeIf { it != 0L },
                seasonNumber = o.optInt("season_number", seasonNumber),
                episodeNumber = o.optInt("episode_number", o.optInt("number", 0)),
                title = o.cleanString("title"),
                overview = o.cleanString("overview"),
                airDate = o.cleanString("air_date"),
                durationMin = o.optInt("runtime", o.optInt("duration", 0)).takeIf { it != 0 },
                posterUrl = o.optString("poster_url").ifBlank { null },
                backdropUrl = o.optString("backdrop_url").ifBlank { null },
                quality = o.optString("quality").ifBlank { null },
                audio = o.optString("audio").ifBlank { null },
                videoMessageId = videoMsg
            )
        }
        return out.sortedBy { it.episodeNumber }
    }

    /** True se o índice cobre esse canal (carrega se preciso). */
    suspend fun covers(channelId: Long): Boolean {
        val idx = ensureLoaded() ?: return false
        return idx.channelId == channelId && (idx.movies.isNotEmpty() || idx.series.isNotEmpty())
    }

    /** Todas as séries do canal (para montar os cards da biblioteca). Vazio se não cobrir o canal. */
    suspend fun allSeries(channelId: Long): List<IndexSeries> {
        val idx = ensureLoaded() ?: return emptyList()
        return if (idx.channelId == channelId) idx.series else emptyList()
    }

    /** Busca local por séries: casa título da série, título de episódio ou código SxxExx. */
    suspend fun searchSeries(channelId: Long, query: String, limit: Int = 60): List<IndexSeries> {
        val idx = ensureLoaded() ?: return emptyList()
        if (idx.channelId != channelId) return emptyList()
        val tokens = normalizeForIndex(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return idx.series.asSequence()
            .filter { s -> tokens.all { s.searchKey.contains(it) } }
            .take(limit)
            .toList()
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

/** "generated_at" do índice (ISO-8601 com fuso, ex.: 2026-09-29T05:00:21+00:00) em ms; null se inválido. */
internal fun parseGeneratedAt(raw: String?): Long? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
    return runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { java.time.Instant.parse(value).toEpochMilli() }
        .getOrNull()
}

/** optString tratando vazio e o literal "null"/"None" (comum em índices) como ausente. */
private fun JSONObject.cleanString(key: String): String? =
    optString(key).trim().ifBlank { null }?.takeUnless { it.equals("null", true) || it.equals("None", true) }

internal fun normalizeForIndex(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
