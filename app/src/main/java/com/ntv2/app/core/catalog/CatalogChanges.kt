package com.ntv2.app.core.catalog

import org.json.JSONObject

/** Item de `catalog_changes`: [data] = linha de `catalog_*`, null quando [kind] = `removed`. */
data class CatalogChange(val kind: String, val ntvId: String, val changedAt: String?, val data: JSONObject?)

/** Uma página de `catalog_changes` (ntv2-backend/CONTRATO.md). */
data class CatalogChangesPage(
    val items: List<CatalogChange>,
    val next: CatalogCursor,
    val hasMore: Boolean,
    val reset: Boolean
) {
    companion object {
        fun parse(body: String): CatalogChangesPage {
            val root = JSONObject(body)
            val arr = root.optJSONArray("items")
            val items = ArrayList<CatalogChange>(arr?.length() ?: 0)
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ntvId = o.cleanString("ntv_id") ?: continue
                val kind = o.cleanString("kind") ?: continue
                items += CatalogChange(kind, ntvId, o.cleanString("changed_at"), o.optJSONObject("data"))
            }
            val next = root.optJSONObject("next")
            return CatalogChangesPage(
                items = items,
                next = CatalogCursor(next?.cleanString("since"), next?.cleanString("after_id")),
                hasMore = root.optBoolean("has_more", false),
                reset = root.optBoolean("reset", false)
            )
        }
    }
}

const val KIND_MOVIE = "movie"
const val KIND_SERIES = "series"
const val KIND_EPISODE = "episode"
const val KIND_REMOVED = "removed"

/** IDs do catálogo, iguais aos do backend: `m_603`, `s_1399`, `e_1399_01_03`. */
object NtvIds {
    fun movie(tmdbId: Long) = "m_$tmdbId"
    fun series(tmdbId: Long) = "s_$tmdbId"
    fun episode(seriesTmdbId: Long, season: Int, episode: Int) = 
        String.format(java.util.Locale.ROOT, "e_%d_%02d_%02d", seriesTmdbId, season, episode)
}

object CatalogMerge {
    /**
     * Página → escrita no banco. Os itens vêm em ordem de `changed_at`: o mesmo `ntv_id` repetido fica
     * com a última versão (inclusive uma remoção depois de uma alteração, ou o contrário). Tipo
     * desconhecido é ignorado (versão nova do backend).
     */
    fun batchFor(items: List<CatalogChange>, cursor: CatalogCursor?, reset: Boolean = false): CatalogBatch {
        val last = LinkedHashMap<String, CatalogChange>()
        items.forEach { c ->
            if (c.kind == KIND_REMOVED || (c.kind in KNOWN_KINDS && c.data != null)) {
                last.remove(c.ntvId)
                last[c.ntvId] = c
            }
        }
        val upserts = ArrayList<CatalogRow>(last.size)
        val removed = ArrayList<String>()
        last.values.forEach { c ->
            if (c.kind == KIND_REMOVED) removed += c.ntvId else upserts += rowOf(c.kind, c.ntvId, c.changedAt, c.data!!)
        }
        return CatalogBatch(upserts = upserts, removed = removed, reset = reset, cursor = cursor)
    }

    /** Linha local a partir da linha do backend (ou do `index.json`, já no mesmo formato). */
    fun rowOf(kind: String, ntvId: String, changedAt: String?, data: JSONObject): CatalogRow = CatalogRow(
        ntvId = ntvId,
        kind = kind,
        tmdbId = data.positiveLong(if (kind == KIND_EPISODE) "series_tmdb_id" else "tmdb_id"),
        chatId = data.optLong("chat_id", 0L).takeIf { it != 0L },
        videoMessageId = if (kind == KIND_SERIES) null else data.positiveLong("video_message_id"),
        changedAt = changedAt,
        data = data.toString()
    )

    private val KNOWN_KINDS = setOf(KIND_MOVIE, KIND_SERIES, KIND_EPISODE)
}

internal fun JSONObject.positiveLong(key: String): Long? = optLong(key, 0L).takeIf { it > 0L }

/** optString tratando ausente, JSON null e o literal "null" como null. */
internal fun JSONObject.cleanString(key: String): String? {
    if (isNull(key)) return null
    return optString(key).trim().ifBlank { null }?.takeUnless { it.equals("null", true) }
}
