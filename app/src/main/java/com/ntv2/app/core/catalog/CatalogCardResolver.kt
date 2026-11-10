package com.ntv2.app.core.catalog

import com.ntv2.app.core.telegram.media.CastMemberMeta
import com.ntv2.app.core.telegram.media.MovieMeta
import org.json.JSONArray
import org.json.JSONObject

/**
 * Metadados do cartão a partir do catálogo local. Ordem: `ntv_id` da legenda v3 → `(chat_id,
 * message_id)` do vídeo → null (quem chama cai na legenda completa e, por fim, no nome do arquivo).
 * Com a flag `remote_catalog` desligada devolve sempre null: o cartão fica como hoje.
 */
class CatalogCardResolver(
    private val store: CatalogStore,
    private val enabled: suspend () -> Boolean
) {
    /** [messageId] pode vir do TDLib (deslocado 20 bits) ou do Bot API. */
    suspend fun metaFor(ntvId: String?, chatId: Long, messageId: Long): MovieMeta? {
        if (!enabled()) return null
        val row = ntvId?.let { store.byNtvId(it) }
            ?: store.byMessage(chatId, botApiMessageId(messageId))
            ?: return null
        val series = if (row.kind == KIND_EPISODE) row.tmdbId?.let { store.byNtvId(NtvIds.series(it)) } else null
        return runCatching { CatalogMetaMapper.toMeta(row, series) }.getOrNull()
    }

    companion object {
        /** O índice e o backend usam o id do Bot API; o TDLib usa o mesmo id << 20. */
        fun botApiMessageId(messageId: Long): Long =
            if (messageId > 0xFFFFF && (messageId and 0xFFFFF) == 0L) messageId shr 20 else messageId
    }
}

/** Linha de `catalog_*` (JSON snake_case) → [MovieMeta], o mesmo modelo que a legenda rica produz. */
object CatalogMetaMapper {
    private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"
    private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w780"
    private const val TMDB_LOGO = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_PROFILE = "https://image.tmdb.org/t/p/w92"

    fun toMeta(row: CatalogRow, series: CatalogRow? = null): MovieMeta? {
        val d = JSONObject(row.data)
        val extra = d.optJSONObject("extra") ?: JSONObject()
        return when (row.kind) {
            KIND_MOVIE, KIND_SERIES -> {
                val title = d.cleanString("title") ?: return null
                common(d, extra, row).copy(
                    title = title,
                    synopsis = d.cleanString("overview") ?: extra.cleanString("overview"),
                    tmdbId = row.tmdbId?.toString(),
                    type = if (row.kind == KIND_SERIES) "Série" else extra.cleanString("type")
                )
            }
            KIND_EPISODE -> episode(d, extra, row, series?.let { JSONObject(it.data) })
            else -> null
        }
    }

    private fun episode(d: JSONObject, extra: JSONObject, row: CatalogRow, s: JSONObject?): MovieMeta? {
        val season = d.optInt("season_number", 0)
        val number = d.optInt("episode_number", 0)
        if (season <= 0 || number <= 0) return null
        val seriesTitle = s?.cleanString("title") ?: extra.cleanString("series_title") ?: "Série"
        val episodeTitle = d.cleanString("title") ?: "Episódio $number"
        val code = String.format(java.util.Locale.ROOT, "S%02dE%02d", season, number)
        val seriesMeta = s?.let { common(it, it.optJSONObject("extra") ?: JSONObject(), null) }
        val own = common(d, extra, row)
        return own.copy(
            // Mesmo formato do título da legenda rica de episódio (bot: legenda.py).
            title = "$seriesTitle — $code — $episodeTitle",
            synopsis = d.cleanString("overview") ?: s?.cleanString("overview"),
            type = "Episódio",
            category = own.category ?: "Séries",
            collection = own.collection ?: seriesTitle,
            originalTitle = own.originalTitle?.takeIf { it != episodeTitle },
            posterUrl = own.posterUrl ?: seriesMeta?.posterUrl,
            backdropUrl = own.backdropUrl ?: seriesMeta?.backdropUrl,
            logoUrl = own.logoUrl ?: seriesMeta?.logoUrl,
            trailerUrl = own.trailerUrl ?: seriesMeta?.trailerUrl,
            genres = own.genres ?: seriesMeta?.genres,
            cast = own.cast.ifEmpty { seriesMeta?.cast.orEmpty() },
            year = own.year ?: d.cleanString("air_date")?.take(4)?.toIntOrNull() ?: seriesMeta?.year,
            ageRating = own.ageRating ?: seriesMeta?.ageRating,
            country = own.country ?: seriesMeta?.country,
            studio = own.studio ?: seriesMeta?.studio,
            // Legado: [MovieMeta.tmdbId] de episódio é o id da SÉRIE.
            tmdbId = row.tmdbId?.toString(),
            seriesTitle = seriesTitle,
            seasonNumber = season,
            episodeNumber = number,
            episodeTitle = episodeTitle,
            airDate = d.cleanString("air_date"),
            seriesTmdbId = row.tmdbId?.toString(),
            episodeTmdbId = d.positiveLong("episode_tmdb_id")?.toString()
        )
    }

    /** Campos com o mesmo nome em filme/série/episódio; os ricos sem coluna própria vêm de `extra`. */
    private fun common(d: JSONObject, extra: JSONObject, row: CatalogRow?): MovieMeta {
        fun pick(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { d.cleanString(it) } ?: keys.firstNotNullOfOrNull { extra.cleanString(it) }
        return MovieMeta(
            originalTitle = pick("original_title"),
            year = pick("year")?.take(4)?.toIntOrNull(),
            director = pick("director", "diretor"),
            audio = pick("audio"),
            genres = genresOf(d.optJSONArray("genres") ?: extra.optJSONArray("genres")),
            durationMin = pick("duration_min", "runtime", "duration")?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 },
            rating = pick("rating", "vote_average")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 },
            imdbRating = pick("imdb_rating")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 },
            rtScore = pick("rt_score")?.removeSuffix("%")?.toDoubleOrNull()?.toInt()?.takeIf { it in 0..100 },
            ageRating = pick("age_rating", "certification", "classificacao"),
            category = pick("category", "categoria"),
            collection = pick("collection", "colecao"),
            country = pick("country", "pais"),
            quality = pick("quality", "qualidade"),
            studio = pick("studio", "estudio"),
            cast = castOf(d.optJSONArray("cast") ?: extra.optJSONArray("cast")),
            posterUrl = pick("poster_url")?.let { image(it, TMDB_POSTER) },
            backdropUrl = pick("backdrop_url")?.let { image(it, TMDB_BACKDROP) },
            logoUrl = pick("logo_url")?.let { image(it, TMDB_LOGO) },
            trailerUrl = pick("trailer_url", "trailer"),
            tags = (extra.optJSONArray("tags")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }
                ?.joinToString(", ")?.ifBlank { null }) ?: pick("tags"),
            ntvId = row?.ntvId,
            imdbId = pick("imdb_id")
        )
    }

    private fun genresOf(arr: JSONArray?): String? {
        if (arr == null) return null
        return (0 until arr.length()).mapNotNull { arr.optString(it).trim().ifBlank { null } }
            .joinToString(", ").ifBlank { null }
    }

    private fun castOf(arr: JSONArray?): List<CastMemberMeta> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.cleanString("name") ?: return@mapNotNull null
            val photo = (o.cleanString("profile_url") ?: o.cleanString("photo_url"))?.let { image(it, TMDB_PROFILE) }
            CastMemberMeta(name, photo)
        }
    }

    private fun image(value: String, base: String): String = if (value.startsWith("/")) base + value else value
}

/** Ordem de resolução do cartão (pura, para o gateway e os testes). */
object CardResolution {
    /**
     * Procurar a mensagem de pôster/texto vizinha? Não quando a própria legenda basta: completa, ou v3
     * (o post v3 é só o vídeo; parear pegaria o pôster de outro post). Sem catálogo, o cartão v3 fica
     * com o nome do arquivo + a logo da legenda.
     */
    fun needsPosterPairing(caption: MovieMeta): Boolean = !caption.isFull && caption.ntvId == null

    /**
     * Nome do vídeo: título do catálogo → título da legenda → nome do arquivo (já limpo) → 1ª linha da
     * legenda. A linha de IDs da legenda v3 nunca vira título.
     */
    fun videoName(catalog: MovieMeta?, caption: MovieMeta, cleanFileName: String?, rawCaption: String?): String? =
        catalog?.title
            ?: caption.title
            ?: cleanFileName?.ifBlank { null }
            ?: rawCaption?.takeIf { caption.ntvId == null }
                ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

    /**
     * Catálogo por cima da legenda: campo do catálogo ganha; o que ele não tiver (diretor, nota... que o
     * `index.json` não traz) continua vindo da legenda, para o cartão não perder informação.
     */
    fun merge(catalog: MovieMeta, caption: MovieMeta?): MovieMeta {
        if (caption == null) return catalog
        return catalog.copy(
            synopsis = catalog.synopsis ?: caption.synopsis,
            year = catalog.year ?: caption.year,
            director = catalog.director ?: caption.director,
            audio = catalog.audio ?: caption.audio,
            genres = catalog.genres ?: caption.genres,
            originalTitle = catalog.originalTitle ?: caption.originalTitle,
            durationMin = catalog.durationMin ?: caption.durationMin,
            rating = catalog.rating ?: caption.rating,
            imdbRating = catalog.imdbRating ?: caption.imdbRating,
            rtScore = catalog.rtScore ?: caption.rtScore,
            ageRating = catalog.ageRating ?: caption.ageRating,
            category = catalog.category ?: caption.category,
            collection = catalog.collection ?: caption.collection,
            country = catalog.country ?: caption.country,
            quality = catalog.quality ?: caption.quality,
            studio = catalog.studio ?: caption.studio,
            cast = catalog.cast.ifEmpty { caption.cast },
            posterUrl = catalog.posterUrl ?: caption.posterUrl,
            backdropUrl = catalog.backdropUrl ?: caption.backdropUrl,
            logoUrl = catalog.logoUrl ?: caption.logoUrl,
            trailerUrl = catalog.trailerUrl ?: caption.trailerUrl,
            tmdbId = catalog.tmdbId ?: caption.tmdbId,
            tags = catalog.tags ?: caption.tags,
            episodeTmdbId = catalog.episodeTmdbId ?: caption.episodeTmdbId,
            imdbId = catalog.imdbId ?: caption.imdbId
        )
    }
}
