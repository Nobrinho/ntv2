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
    val searchWords: Set<String> by lazy { wordsOf(searchKey) }
    /** Gêneros já normalizados (o filtro não renormaliza a cada busca). */
    val genreKeys: Set<String> by lazy { genres.mapTo(HashSet()) { normalizeForIndex(it).trim() } }
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
    /** Ano da série = ano do 1º episódio (menor temporada/episódio) que traz data de exibição. */
    val year: Int? = seasons.asSequence().flatMap { it.episodes.asSequence() }
        .firstNotNullOfOrNull { e -> e.airDate?.take(4)?.toIntOrNull() }

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
    val searchWords: Set<String> by lazy { wordsOf(searchKey) }
    val genreKeys: Set<String> by lazy { genres.mapTo(HashSet()) { normalizeForIndex(it).trim() } }
}

private data class LoadedIndex(
    val channelId: Long,
    val movies: List<IndexMovie>,
    val series: List<IndexSeries> = emptyList(),
    /** Quando o bot gerou o índice ("generated_at"), em ms; null se o JSON não trouxer. */
    val generatedAtMillis: Long? = null
) {
    /** Nomes do elenco normalizados de cada filme (paralelo a [movies]); montado 1x, sob demanda. */
    val movieActorKeys: List<Set<String>> by lazy {
        movies.map { m -> m.cast.map { normalizeForIndex(it.name).trim() }.toSet() }
    }

    /** Filme pelo message_id do vídeo (Bot API), montado 1x: evita varrer o catálogo a cada consulta. */
    val movieByVideoMessage: Map<Long, IndexMovie> by lazy { movies.associateBy { it.videoMessageId } }
}

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
    // Falha recente sem cópia em memória: evita refazer o download (e segurar o mutex) a cada busca.
    @Volatile private var lastFailureAt = 0L

    private val _status = MutableStateFlow(SearchIndexStatus())
    /** Data do índice em uso e se há download manual em andamento. */
    val status: StateFlow<SearchIndexStatus> = _status.asStateFlow()

    private suspend fun ensureLoaded(): LoadedIndex? {
        val now = System.currentTimeMillis()
        cached?.let { if (now - loadedAt < ttlMillis) return it }
        if (cached == null && now - lastFailureAt < FAILURE_BACKOFF_MS) return null
        return mutex.withLock {
            val fresh = cached
            if (fresh != null && System.currentTimeMillis() - loadedAt < ttlMillis) return fresh
            if (fresh == null && System.currentTimeMillis() - lastFailureAt < FAILURE_BACKOFF_MS) return null
            val loaded = runCatching { fetch() }.getOrNull()
            if (loaded != null) store(loaded) else lastFailureAt = System.currentTimeMillis()
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
                    lastFailureAt = 0L
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
            val body = resp.body ?: return@withContext null
            // Lê em streaming: nunca existe a String inteira do arquivo (pico de memória ~3x o tamanho).
            android.util.JsonReader(body.charStream()).use { parse(it) }
        }
    }

    /** Percorre o JSON raiz item a item: só um filme/série fica como JSONObject por vez. */
    private fun parse(reader: android.util.JsonReader): LoadedIndex {
        var channelId = 0L
        var generatedAt: Long? = null
        val movies = ArrayList<IndexMovie>()
        val series = ArrayList<IndexSeries>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "channel" -> channelId = readValue(reader).toString().toLongOrNull() ?: 0L
                "generated_at" -> generatedAt = parseGeneratedAt(readValue(reader).toString())
                "movies" -> forEachObject(reader) { parseMovie(it)?.let(movies::add) }
                "series" -> forEachObject(reader) { series += parseOneSeries(it) }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return LoadedIndex(channelId, movies, series, generatedAt)
    }

    private inline fun forEachObject(reader: android.util.JsonReader, block: (JSONObject) -> Unit) {
        if (reader.peek() != android.util.JsonToken.BEGIN_ARRAY) { reader.skipValue(); return }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) block(readValue(reader) as JSONObject)
            else reader.skipValue()
        }
        reader.endArray()
    }

    /** Lê o valor atual do [reader] como JSONObject/JSONArray/String/número/boolean/JSONObject.NULL. */
    private fun readValue(reader: android.util.JsonReader): Any = when (reader.peek()) {
        android.util.JsonToken.BEGIN_OBJECT -> JSONObject().also { o ->
            reader.beginObject()
            while (reader.hasNext()) o.put(reader.nextName(), readValue(reader))
            reader.endObject()
        }
        android.util.JsonToken.BEGIN_ARRAY -> org.json.JSONArray().also { arr ->
            reader.beginArray()
            while (reader.hasNext()) arr.put(readValue(reader))
            reader.endArray()
        }
        android.util.JsonToken.STRING -> reader.nextString()
        android.util.JsonToken.NUMBER -> reader.nextString().let { it.toLongOrNull() ?: it.toDoubleOrNull() ?: it }
        android.util.JsonToken.BOOLEAN -> reader.nextBoolean()
        else -> { reader.skipValue(); JSONObject.NULL }
    }

    private fun parseMovie(o: JSONObject): IndexMovie? {
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
        return IndexMovie(
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

    private fun parseOneSeries(o: JSONObject): IndexSeries {
        val genresArr = o.optJSONArray("genres")
        val genres = if (genresArr != null) (0 until genresArr.length()).map { genresArr.optString(it) } else emptyList()
        return IndexSeries(
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
    suspend fun searchSeries(channelId: Long, query: String, limit: Int = 60): List<IndexSeries> = withContext(Dispatchers.Default) {
        val idx = ensureLoaded() ?: return@withContext emptyList()
        if (idx.channelId != channelId) return@withContext emptyList()
        val tokens = normalizeForIndex(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@withContext emptyList()
        fun hits(fuzzy: Boolean) = idx.series.asSequence()
            .filter { s -> matchesTokens(s.searchKey, s.searchWords, tokens, fuzzy) }
            .take(limit)
            .toList()
        hits(false).ifEmpty { hits(true) }
    }

    /**
     * Busca no índice combinando texto (opcional), gêneros (todos os marcados) e anos (qualquer um; série: ano do 1º episódio). Com texto
     * limita a 60 resultados (como a busca simples); só com filtro devolve todos.
     */
    suspend fun searchFiltered(
        channelId: Long,
        query: String,
        genres: Set<String>,
        years: Set<Int>,
        actor: String? = null,
        limit: Int = 60
    ): Pair<List<IndexMovie>, List<IndexSeries>> = withContext(Dispatchers.Default) {
        val idx = ensureLoaded()?.takeIf { it.channelId == channelId } ?: return@withContext emptyList<IndexMovie>() to emptyList()
        val tokens = normalizeForIndex(query).split(' ').filter { it.isNotBlank() }
        val wanted = genres.map { normalizeForIndex(it).trim() }
        fun genreOk(keys: Set<String>): Boolean = wanted.isEmpty() || wanted.all { it in keys }
        fun <T> Sequence<T>.cap() = if (tokens.isEmpty()) this else take(limit)
        val actorKey = actor?.let { normalizeForIndex(it).trim() }
        val actorKeys = if (actorKey != null) idx.movieActorKeys else emptyList()
        fun run(fuzzy: Boolean): Pair<List<IndexMovie>, List<IndexSeries>> {
            val movies = idx.movies.asSequence().withIndex()
                .filter { (i, _) -> actorKey == null || actorKey in actorKeys[i] }
                .map { it.value }
                .filter { m ->
                    matchesTokens(m.searchKey, m.searchWords, tokens, fuzzy) && genreOk(m.genreKeys) &&
                        (years.isEmpty() || m.year?.take(4)?.toIntOrNull() in years)
                }.cap().toList()
            // Séries não trazem elenco no índice: com filtro de ator não entram.
            val series = (if (actorKey != null) emptySequence() else idx.series.asSequence())
                .filter { sr ->
                    matchesTokens(sr.searchKey, sr.searchWords, tokens, fuzzy) && genreOk(sr.genreKeys) &&
                        (years.isEmpty() || sr.year in years)
                }
                .cap().toList()
            return movies to series
        }
        val exact = run(fuzzy = false)
        // Nada casou exato: tenta de novo tolerando erro de digitação (ex.: "eletric" → "electric").
        if (tokens.isEmpty() || exact.first.isNotEmpty() || exact.second.isNotEmpty()) exact else run(fuzzy = true)
    }

    /** Quantos filmes do canal têm [actor] no elenco. */
    suspend fun countByActor(channelId: Long, actor: String): Int = withContext(Dispatchers.Default) {
        val idx = ensureLoaded()?.takeIf { it.channelId == channelId } ?: return@withContext 0
        val key = normalizeForIndex(actor).trim()
        idx.movieActorKeys.count { key in it }
    }

    /** Gêneros (A-Z) e anos (mais recente primeiro) existentes no índice do canal, para o filtro. */
    suspend fun filterOptions(channelId: Long): Pair<List<String>, List<Int>> = withContext(Dispatchers.Default) {
        val idx = ensureLoaded()?.takeIf { it.channelId == channelId } ?: return@withContext emptyList<String>() to emptyList()
        val genres = LinkedHashMap<String, String>()
        (idx.movies.flatMap { it.genres } + idx.series.flatMap { it.genres }).forEach { g ->
            val label = g.trim()
            if (label.isNotEmpty()) genres.putIfAbsent(normalizeForIndex(label).trim(), label)
        }
        val years = (idx.movies.mapNotNull { it.year?.take(4)?.toIntOrNull() } + idx.series.mapNotNull { it.year })
            .toSortedSet(compareByDescending { it })
        genres.values.sortedBy { normalizeForIndex(it) } to years.toList()
    }

    /** Filme do índice pelo id da mensagem do vídeo (metadados dos Detalhes de itens fora da grade). */
    suspend fun movieByMessage(channelId: Long, messageId: Long): IndexMovie? {
        val idx = ensureLoaded() ?: return null
        if (idx.channelId != channelId) return null
        // Aceita o id do Bot API (como no índice) ou o do TDLib (deslocado 20 bits, usado nos mediaId).
        val byId = idx.movieByVideoMessage
        return byId[messageId] ?: if (messageId > 0xFFFFF && (messageId and 0xFFFFF) == 0L) byId[messageId shr 20] else null
    }

    /** Busca local por título/título original (sem acento/caixa). Vazio se não cobrir o canal. */
    suspend fun search(channelId: Long, query: String, limit: Int = 60): List<IndexMovie> = withContext(Dispatchers.Default) {
        val idx = ensureLoaded() ?: return@withContext emptyList()
        if (idx.channelId != channelId) return@withContext emptyList()
        val tokens = normalizeForIndex(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@withContext emptyList()
        fun hits(fuzzy: Boolean) = idx.movies.asSequence()
            .filter { m -> matchesTokens(m.searchKey, m.searchWords, tokens, fuzzy) }
            .take(limit)
            .toList()
        hits(false).ifEmpty { hits(true) }
    }
}

private const val FAILURE_BACKOFF_MS = 60_000L

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

/** Palavras (só letras/dígitos) de uma chave de busca já normalizada. */
internal fun wordsOf(key: String): Set<String> =
    key.split(NON_WORD).filterTo(HashSet()) { it.isNotEmpty() }

/**
 * Todas as palavras da busca precisam casar. Exato: trecho da chave. [fuzzy]: se não for trecho,
 * aceita uma palavra do título com poucas letras trocadas/faltando/sobrando/invertidas.
 */
internal fun matchesTokens(key: String, words: Set<String>, tokens: List<String>, fuzzy: Boolean): Boolean =
    tokens.all { t ->
        key.contains(t) || (fuzzy && words.any { w -> isTypoOf(t, w) })
    }

/** Palavras curtas (<5) exigem acerto exato; 5–8 letras toleram 1 erro; 9+ toleram 2. */
internal fun isTypoOf(token: String, word: String): Boolean {
    val max = when {
        token.length < 5 -> return false
        token.length < 9 -> 1
        else -> 2
    }
    if (kotlin.math.abs(token.length - word.length) > max) return false
    return editDistanceAtMost(token, word, max)
}

/** Distância de edição (inserção/remoção/troca/transposição de vizinhas) limitada a [max]. */
private fun editDistanceAtMost(a: String, b: String, max: Int): Boolean {
    var prev2 = IntArray(b.length + 1)
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        var rowMin = cur[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
            cur[j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > max) return false
        prev2 = prev
        prev = cur
    }
    return prev[b.length] <= max
}

// Compilados uma vez: criar o Regex a cada chamada travava a thread principal no Fire TV (ANR em
// filterOptions, que normaliza milhares de gêneros).
private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NON_WORD = Regex("[^a-z0-9]+")

internal fun normalizeForIndex(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
