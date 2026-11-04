package com.ntv2.app.feature.media.data.index

import com.ntv2.app.core.catalog.CatalogMerge
import com.ntv2.app.core.catalog.CatalogRow
import com.ntv2.app.core.catalog.CatalogSeed
import com.ntv2.app.core.catalog.KIND_EPISODE
import com.ntv2.app.core.catalog.KIND_MOVIE
import com.ntv2.app.core.catalog.KIND_SERIES
import com.ntv2.app.core.catalog.NtvIds
import org.json.JSONArray
import org.json.JSONObject

/**
 * `index.json` → linhas do catálogo local, no mesmo formato de `catalog_*` do backend (o import do
 * backend lê este mesmo arquivo), com os mesmos `ntv_id`. Itens sem TMDB ficam de fora: o cartão
 * deles continua vindo da legenda.
 */
object IndexCatalogSeed {

    fun from(snapshot: IndexSnapshot): CatalogSeed {
        val chatId = snapshot.channelId.takeIf { it != 0L }
        val rows = ArrayList<CatalogRow>(snapshot.movies.size + snapshot.series.size * 8)
        snapshot.movies.forEach { m -> if (m.tmdbId > 0) rows += movieRow(m, chatId) }
        snapshot.series.forEach { s ->
            if (s.tmdbId <= 0) return@forEach
            rows += seriesRow(s)
            s.seasons.forEach { season ->
                season.episodes.forEach { e ->
                    if (e.seasonNumber > 0 && e.episodeNumber > 0) rows += episodeRow(s.tmdbId, e, chatId)
                }
            }
        }
        return CatalogSeed(rows.distinctBy { it.ntvId }, snapshot.generatedAtMillis)
    }

    internal fun movieRow(m: IndexMovie, chatId: Long?): CatalogRow {
        val d = JSONObject()
            .put("tmdb_id", m.tmdbId)
            .put("title", m.title)
            .putOpt("original_title", m.originalTitle)
            .putOpt("year", m.year?.take(4)?.toIntOrNull())
            .putOpt("poster_url", m.posterUrl)
            .putOpt("backdrop_url", m.backdropUrl)
            .putOpt("logo_url", m.logoUrl)
            .putOpt("overview", m.overview)
            .put("genres", JSONArray(m.genres))
            .put("cast", JSONArray().also { arr ->
                m.cast.forEach { c -> arr.put(JSONObject().put("name", c.name).putOpt("profile_url", c.photoUrl)) }
            })
            .putOpt("video_message_id", m.videoMessageId.takeIf { it > 0 })
            .putOpt("chat_id", chatId)
            .putOpt("trailer_url", m.trailerUrl)
        return CatalogMerge.rowOf(KIND_MOVIE, NtvIds.movie(m.tmdbId), null, d)
    }

    internal fun seriesRow(s: IndexSeries): CatalogRow {
        val d = JSONObject()
            .put("tmdb_id", s.tmdbId)
            .put("title", s.title)
            .putOpt("original_title", s.originalTitle)
            .putOpt("year", s.year)
            .putOpt("poster_url", s.posterUrl)
            .putOpt("backdrop_url", s.backdropUrl)
            .putOpt("logo_url", s.logoUrl)
            .putOpt("overview", s.overview)
            .put("genres", JSONArray(s.genres))
            .putOpt("trailer_url", s.trailerUrl)
        return CatalogMerge.rowOf(KIND_SERIES, NtvIds.series(s.tmdbId), null, d)
    }

    internal fun episodeRow(seriesTmdbId: Long, e: IndexEpisode, chatId: Long?): CatalogRow {
        val d = JSONObject()
            .put("series_tmdb_id", seriesTmdbId)
            .put("season_number", e.seasonNumber)
            .put("episode_number", e.episodeNumber)
            .putOpt("episode_tmdb_id", e.episodeTmdbId)
            .putOpt("title", e.title)
            .putOpt("overview", e.overview)
            .putOpt("air_date", e.airDate)
            .putOpt("duration_min", e.durationMin)
            .putOpt("poster_url", e.posterUrl)
            .putOpt("backdrop_url", e.backdropUrl)
            .putOpt("logo_url", e.logoUrl)
            .putOpt("quality", e.quality)
            .putOpt("audio", e.audio)
            .putOpt("video_message_id", e.videoMessageId.takeIf { it > 0 })
            .putOpt("chat_id", chatId)
        return CatalogMerge.rowOf(KIND_EPISODE, NtvIds.episode(seriesTmdbId, e.seasonNumber, e.episodeNumber), null, d)
    }
}
