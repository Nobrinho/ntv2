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

/**
 * IDs do catálogo, iguais aos do backend: `m_603`, `s_1399`, `e_1399_01_03`; conteúdo próprio (NBR
 * Studio, sem TMDB): `m_nbr_<slug>`, `s_nbr_<slug>`, `e_nbr_<slug>_TT_EE` (o slug não tem "_").
 *
 * O app indexa filmes e séries por um `Long` (o antigo `tmdbId`: mapas, chaves de lista, favoritos,
 * "continuar"). Item próprio não tem `tmdb_id`, então ganha uma chave estável derivada do slug
 * ([ownKey]), sempre ≥ 2^62: nunca colide com um id do TMDB e continua > 0 (os `tmdbId > 0` seguem
 * valendo). Nunca vai a uma URL do TMDB: use [isOwnKey] antes.
 */
object NtvIds {
    fun movie(tmdbId: Long) = "m_$tmdbId"
    fun series(tmdbId: Long) = "s_$tmdbId"
    fun episode(seriesTmdbId: Long, season: Int, episode: Int) =
        String.format(java.util.Locale.ROOT, "e_%d_%02d_%02d", seriesTmdbId, season, episode)

    private const val OWN_KEY_BASE = 1L shl 62
    private val RX_OWN = Regex("[mse]_nbr_([a-z0-9][a-z0-9-]*)(?:_\\d+_\\d+)?")
    private val RX_TMDB = Regex("[mse]_(\\d+)(?:_\\d+_\\d+)?")
    private val RX_EPISODE = Regex("e_((?:nbr_)?[a-z0-9-]+)_\\d+_\\d+")

    /** Chave local de um item próprio pelo slug (FNV-1a 64, faixa [2^62, 2^63)). */
    fun ownKey(slug: String): Long {
        var h = -0x340d631b7bdddcdbL // 14695981039346656037
        for (b in slug.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        return OWN_KEY_BASE or (h and (OWN_KEY_BASE - 1))
    }

    /** Chave de conteúdo próprio (não é id do TMDB). */
    fun isOwnKey(key: Long): Boolean = key >= OWN_KEY_BASE

    /** Slug do item próprio (`s_nbr_tom-e-jerry` → `tom-e-jerry`); null se for do TMDB. */
    fun ownSlug(ntvId: String): String? = RX_OWN.matchEntire(ntvId)?.groupValues?.get(1)

    /**
     * Chave `Long` do item (ou, para episódio, da série): o id do TMDB ou [ownKey] do slug. Null se o
     * id não for reconhecido.
     */
    fun keyOf(ntvId: String): Long? =
        ownSlug(ntvId)?.let(::ownKey) ?: RX_TMDB.matchEntire(ntvId)?.groupValues?.get(1)?.toLongOrNull()

    /** `e_1399_01_03` → `s_1399`; `e_nbr_x_01_02` → `s_nbr_x`. Null se não for episódio. */
    fun seriesOfEpisode(ntvId: String): String? = RX_EPISODE.matchEntire(ntvId)?.let { "s_" + it.groupValues[1] }
}

/** Chave `Long` do item ([NtvIds.keyOf]); para episódio, a da série. */
fun CatalogRow.itemKey(): Long? = tmdbId ?: NtvIds.keyOf(ntvId)

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
