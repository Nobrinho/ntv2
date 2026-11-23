package com.ntv2.app.feature.media.domain

import java.text.Normalizer

/**
 * O que um perfil infantil pode ver.
 *
 * Com classificação indicativa no título ("L", "Livre", "10", "12 anos"...), vale ela contra o limite do
 * perfil. Sem classificação (a maior parte do catálogo ainda não tem), só passam os gêneros infantis
 * (Animação, Família, Infantil). Sem gênero conhecido: não passa — no perfil infantil, na dúvida, esconde.
 */
object KidsFilter {
    private val KIDS_GENRES = setOf("animacao", "familia", "infantil", "kids", "animation", "family")

    fun allows(genres: String?, ageRating: String?, maxRating: Int): Boolean {
        parseRating(ageRating)?.let { return it <= maxRating }
        return allowsGenres(genres?.split(',', '/', '|', ';').orEmpty())
    }

    fun allowsGenres(genres: List<String>): Boolean =
        genres.any { normalize(it) in KIDS_GENRES }

    /** "L"/"Livre"/"ER" → 0; "12", "12 anos", "A12" → 12; desconhecida → null. */
    internal fun parseRating(raw: String?): Int? {
        val text = raw?.trim()?.uppercase() ?: return null
        if (text.isEmpty()) return null
        if (text == "L" || text.startsWith("LIVRE") || text == "ER" || text == "AL") return 0
        return Regex("""(\d{1,2})""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
