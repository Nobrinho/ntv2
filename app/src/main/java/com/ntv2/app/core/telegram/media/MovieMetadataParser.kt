package com.ntv2.app.core.telegram.media

import java.text.Normalizer

/** Ator do elenco: nome + foto (URL pronta) opcional. */
data class CastMemberMeta(val name: String, val photoUrl: String?)

/** Metadados extraídos da legenda de um post de filme/documentário. */
data class MovieMeta(
    val title: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null,
    // Campos do formato rico (canal próprio, alimentado pelo bot/TMDB).
    val originalTitle: String? = null,
    val type: String? = null,
    val durationMin: Int? = null,
    val rating: Double? = null,
    val ageRating: String? = null,
    val category: String? = null,
    val collection: String? = null,
    val country: String? = null,
    val quality: String? = null,
    val studio: String? = null,
    val cast: List<CastMemberMeta> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val trailerUrl: String? = null,
    val tmdbId: String? = null,
    val tags: String? = null,
) {
    /** É um post no formato rico do nosso canal (tem título + ao menos um campo rico chave)? */
    val isRich: Boolean
        get() = title != null &&
            (posterUrl != null || backdropUrl != null || tmdbId != null || cast.isNotEmpty())

    /**
     * Dados COMPLETOS na própria legenda (mensagem única: legenda no vídeo). Diferente de [isRich],
     * NÃO basta ter só o `TMDB:` — precisa de conteúdo real (pôster, elenco ou sinopse). Assim uma
     * legenda mínima de vídeo (só "Título + TMDB", usada para amarrar por id) não é confundida.
     */
    val isFull: Boolean
        get() = title != null &&
            (posterUrl != null || backdropUrl != null || cast.isNotEmpty() || synopsis != null)
}

/**
 * Parser puro (sem TDLib) das legendas dos canais. Suporta:
 *  - Formato simples (Polemic): "Filme:/Título:", "Diretor:", "Áudio:", "Lançamento:", "Gêneros:",
 *    sinopse como parágrafo livre.
 *  - Formato rico (canal próprio): todos os campos abaixo, com imagens por URL ou caminho TMDB
 *    (o parser já reconstrói a URL completa quando vier só o caminho "/xxxx.jpg").
 * Ignora linhas de @canal e linhas só de hashtags. Tolerante a emoji/"|" antes do rótulo e a acentos.
 */
object MovieMetadataParser {

    private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w780"
    private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280"
    private const val TMDB_PROFILE = "https://image.tmdb.org/t/p/w185"

    private val TITLE_KEYS = setOf("filme", "titulo", "title")
    private val ORIGINAL_KEYS = setOf("original")
    private val TYPE_KEYS = setOf("tipo", "type")
    private val DIRECTOR_KEYS = setOf("diretor", "director")
    private val AUDIO_KEYS = setOf("audio")
    private val QUALITY_KEYS = setOf("qualidade", "quality")
    private val YEAR_KEYS = setOf("lancamento", "ano", "year")
    private val DURATION_KEYS = setOf("duracao", "duration", "runtime")
    private val RATING_KEYS = setOf("nota", "avaliacao", "rating")
    private val AGE_KEYS = setOf("classificacao", "faixa", "idade")
    private val GENRE_KEYS = setOf("generos", "genero", "genres", "genre")
    private val CATEGORY_KEYS = setOf("categoria", "category")
    private val COLLECTION_KEYS = setOf("colecao", "collection")
    private val COUNTRY_KEYS = setOf("pais", "country")
    private val STUDIO_KEYS = setOf("estudio", "studio")
    private val CAST_KEYS = setOf("elenco", "cast")
    private val POSTER_KEYS = setOf("poster", "capa")
    private val BACKDROP_KEYS = setOf("fundo", "backdrop")
    private val TRAILER_KEYS = setOf("trailer")
    private val TMDB_KEYS = setOf("tmdb")
    private val TAGS_KEYS = setOf("tags")
    private val SYNOPSIS_KEYS = setOf("sinopse", "synopsis")
    private val OTHER_KEYS = setOf("copyright", "direitos")

    private val allLabelKeys =
        TITLE_KEYS + ORIGINAL_KEYS + TYPE_KEYS + DIRECTOR_KEYS + AUDIO_KEYS + QUALITY_KEYS +
            YEAR_KEYS + DURATION_KEYS + RATING_KEYS + AGE_KEYS + GENRE_KEYS + CATEGORY_KEYS +
            COLLECTION_KEYS + COUNTRY_KEYS + STUDIO_KEYS + CAST_KEYS + POSTER_KEYS + BACKDROP_KEYS +
            TRAILER_KEYS + TMDB_KEYS + TAGS_KEYS + SYNOPSIS_KEYS + OTHER_KEYS

    private sealed interface Line {
        data class Labeled(val key: String, val value: String) : Line
        data class Free(val text: String) : Line
        data object Skip : Line
    }

    fun parse(caption: String?): MovieMeta {
        if (caption.isNullOrBlank()) return MovieMeta()
        val classified = caption.lines().map { classify(it) }

        val labeled = HashMap<String, String>()
        classified.forEach { line ->
            if (line is Line.Labeled && line.key !in labeled) labeled[line.key] = line.value
        }
        fun get(keys: Set<String>): String? = keys.firstNotNullOfOrNull { labeled[it] }?.trim()?.ifBlank { null }

        var title = get(TITLE_KEYS)
        val freeLines = classified.filterIsInstance<Line.Free>().map { it.text }
        if (title.isNullOrBlank()) title = freeLines.firstOrNull()

        // Sinopse: rótulo "Sinopse:" (multi-linha) senão bloco livre (menos o título).
        val sinopseIdx = classified.indexOfFirst { it is Line.Labeled && it.key in SYNOPSIS_KEYS }
        val synopsis = if (sinopseIdx >= 0) {
            val head = (classified[sinopseIdx] as Line.Labeled).value
            val tail = buildList {
                var i = sinopseIdx + 1
                while (i < classified.size) {
                    val l = classified[i]
                    if (l is Line.Free) add(l.text) else if (l is Line.Labeled) break
                    i++
                }
            }
            (listOf(head) + tail).filter { it.isNotBlank() }.joinToString(" ").trim().ifBlank { null }
        } else {
            val body = if (title != null && freeLines.firstOrNull() == title) freeLines.drop(1) else freeLines
            body.joinToString(" ").trim().ifBlank { null }
        }

        return MovieMeta(
            title = title?.trim()?.ifBlank { null },
            synopsis = synopsis,
            year = get(YEAR_KEYS)?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() },
            director = get(DIRECTOR_KEYS),
            audio = get(AUDIO_KEYS),
            genres = get(GENRE_KEYS),
            originalTitle = get(ORIGINAL_KEYS),
            type = get(TYPE_KEYS),
            durationMin = get(DURATION_KEYS)?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() },
            rating = get(RATING_KEYS)?.replace(',', '.')?.let { Regex("\\d+(\\.\\d+)?").find(it)?.value?.toDoubleOrNull() },
            ageRating = get(AGE_KEYS),
            category = get(CATEGORY_KEYS),
            collection = get(COLLECTION_KEYS),
            country = get(COUNTRY_KEYS),
            quality = get(QUALITY_KEYS),
            studio = get(STUDIO_KEYS),
            cast = get(CAST_KEYS)?.let { parseCast(it) } ?: emptyList(),
            posterUrl = get(POSTER_KEYS)?.let { tmdbUrl(it, TMDB_POSTER) },
            backdropUrl = get(BACKDROP_KEYS)?.let { tmdbUrl(it, TMDB_BACKDROP) },
            trailerUrl = get(TRAILER_KEYS),
            tmdbId = get(TMDB_KEYS),
            tags = get(TAGS_KEYS),
        )
    }

    private fun parseCast(value: String): List<CastMemberMeta> =
        value.split(";").mapNotNull { raw ->
            val entry = raw.trim()
            if (entry.isBlank()) return@mapNotNull null
            val sep = entry.indexOf("::")
            if (sep >= 0) {
                val name = entry.substring(0, sep).trim()
                val foto = entry.substring(sep + 2).trim().ifBlank { null }?.let { tmdbUrl(it, TMDB_PROFILE) }
                if (name.isBlank()) null else CastMemberMeta(name, foto)
            } else {
                CastMemberMeta(entry, null)
            }
        }

    /** Aceita URL completa (retorna como está) ou caminho TMDB ("/xxx.jpg" → base + caminho). */
    private fun tmdbUrl(value: String, base: String): String? {
        val v = value.trim()
        return when {
            v.isBlank() -> null
            v.startsWith("http", ignoreCase = true) -> v
            v.startsWith("/") -> base + v
            else -> v
        }
    }

    private fun classify(rawLine: String): Line {
        val line = rawLine.trim()
        if (line.isBlank()) return Line.Skip
        val withoutSeparators = line.replace("|", " ").trim()
        if (withoutSeparators.split(Regex("\\s+")).all { it.startsWith("@") }) return Line.Skip
        if (withoutSeparators.split(Regex("\\s+")).all { it.startsWith("#") }) return Line.Skip

        val colon = line.indexOf(':')
        if (colon > 0) {
            val key = normalizeKey(line.substring(0, colon))
            if (key in allLabelKeys) {
                return Line.Labeled(key, line.substring(colon + 1).trim())
            }
        }
        return Line.Free(line)
    }

    private fun normalizeKey(prefix: String): String {
        val afterPipe = prefix.substringAfterLast('|')
        val letters = afterPipe.filter { it.isLetter() || it.isWhitespace() }.trim().lowercase()
        return stripAccents(letters)
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
