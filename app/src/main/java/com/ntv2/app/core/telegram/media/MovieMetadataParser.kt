package com.ntv2.app.core.telegram.media

import java.text.Normalizer

/** Metadados extraídos da legenda de um post de filme/documentário. */
data class MovieMeta(
    val title: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null
)

/**
 * Parser puro (sem TDLib) das legendas dos canais. Suporta:
 *  - Filme (A/B): linhas rotuladas "🎬| Filme: X", "Diretor:", "Áudio:", "Lançamento:", "Gêneros:",
 *    e a sinopse como parágrafo livre (sem rótulo) antes dos @links.
 *  - Documentário (C): título na 1ª linha (sem rótulo) e "Sinopse:" rotulada (multi-linha).
 * Ignora linhas de @canal e linhas só de hashtags. Tolerante a emoji/"|" antes do rótulo e a acentos.
 */
object MovieMetadataParser {

    private val TITLE_KEYS = setOf("filme", "titulo", "title")
    private val DIRECTOR_KEYS = setOf("diretor", "director")
    private val AUDIO_KEYS = setOf("audio")
    private val YEAR_KEYS = setOf("lancamento", "ano", "year")
    private val GENRE_KEYS = setOf("generos", "genero", "genres", "genre")
    private val SYNOPSIS_KEYS = setOf("sinopse", "synopsis")
    // Rótulos conhecidos que NÃO viram título/sinopse por engano.
    private val OTHER_KEYS = setOf("copyright", "direitos")

    private val allLabelKeys =
        TITLE_KEYS + DIRECTOR_KEYS + AUDIO_KEYS + YEAR_KEYS + GENRE_KEYS + SYNOPSIS_KEYS + OTHER_KEYS

    private sealed interface Line {
        data class Labeled(val key: String, val value: String) : Line
        data class Free(val text: String) : Line
        data object Skip : Line // @link / hashtags-only / blank
    }

    fun parse(caption: String?): MovieMeta {
        if (caption.isNullOrBlank()) return MovieMeta()
        val classified = caption.lines().map { classify(it) }

        var title: String? = null
        var director: String? = null
        var audio: String? = null
        var genres: String? = null
        var yearRaw: String? = null
        var synopsis: String? = null

        // Campos rotulados.
        classified.forEach { line ->
            if (line is Line.Labeled) {
                when (line.key) {
                    in TITLE_KEYS -> if (title == null) title = line.value
                    in DIRECTOR_KEYS -> if (director == null) director = line.value
                    in AUDIO_KEYS -> if (audio == null) audio = line.value
                    in YEAR_KEYS -> if (yearRaw == null) yearRaw = line.value
                    in GENRE_KEYS -> if (genres == null) genres = line.value
                }
            }
        }

        val freeLines = classified.filterIsInstance<Line.Free>().map { it.text }

        // Título: rótulo Filme/Título; senão a 1ª linha livre.
        if (title.isNullOrBlank()) {
            title = freeLines.firstOrNull()
        }

        // Sinopse: rótulo "Sinopse:" + linhas livres seguintes; senão o bloco livre (menos o título).
        val sinopseIdx = classified.indexOfFirst { it is Line.Labeled && it.key in SYNOPSIS_KEYS }
        synopsis = if (sinopseIdx >= 0) {
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
            // Caso A: sinopse é o texto livre, exceto a linha usada como título.
            val body = if (title != null && freeLines.firstOrNull() == title) freeLines.drop(1) else freeLines
            body.joinToString(" ").trim().ifBlank { null }
        }

        return MovieMeta(
            title = title?.trim()?.ifBlank { null },
            synopsis = synopsis,
            year = yearRaw?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() },
            director = director?.trim()?.ifBlank { null },
            audio = audio?.trim()?.ifBlank { null },
            genres = genres?.trim()?.ifBlank { null }
        )
    }

    private fun classify(rawLine: String): Line {
        val line = rawLine.trim()
        if (line.isBlank()) return Line.Skip
        // Linha só de @menções (ex.: "@PolemicFilmes | @PolemicMovies").
        val withoutSeparators = line.replace("|", " ").trim()
        if (withoutSeparators.split(Regex("\\s+")).all { it.startsWith("@") }) return Line.Skip
        // Linha só de hashtags (ex.: "#Documentário #politica").
        if (withoutSeparators.split(Regex("\\s+")).all { it.startsWith("#") }) return Line.Skip

        val colon = line.indexOf(':')
        if (colon > 0) {
            val prefix = line.substring(0, colon)
            val key = normalizeKey(prefix)
            if (key in allLabelKeys) {
                val value = line.substring(colon + 1).trim()
                return Line.Labeled(key, value)
            }
        }
        return Line.Free(line)
    }

    /** "🎬| Lançamento" / "©|Copyright" → "lancamento" / "copyright" (sem emoji/pipe/acentos). */
    private fun normalizeKey(prefix: String): String {
        val afterPipe = prefix.substringAfterLast('|')
        val letters = afterPipe.filter { it.isLetter() || it.isWhitespace() }.trim().lowercase()
        return stripAccents(letters)
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
