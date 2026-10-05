package com.ntv2.app.core.recommendations

/**
 * Recomendações por gênero, puras e testáveis: dado o gosto do usuário (gêneros ponderados,
 * derivados do histórico + favoritos) e um conjunto de candidatos do catálogo, pontua cada candidato
 * pela soma dos pesos dos gêneros em comum e devolve os melhores, excluindo o que já foi visto/salvo.
 */
object RecommendationEngine {

    data class Candidate(val id: String, val genres: Set<String>)

    /** Normaliza uma string de gêneros ("Ação, Drama") em um conjunto minúsculo e sem vazios. */
    fun parseGenres(raw: String?): Set<String> =
        raw?.split(',', '/', '|')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /**
     * @param preferredGenres gênero -> peso (quanto o usuário gosta; ex.: favorito pesa mais).
     * @param candidates itens do catálogo (id + gêneros).
     * @param exclude ids a nunca recomendar (já assistidos, favoritados, em andamento).
     * @param limit teto de resultados.
     * @return ids recomendados, do mais relevante ao menos (empate mantém a ordem de entrada).
     */
    fun recommend(
        preferredGenres: Map<String, Int>,
        candidates: List<Candidate>,
        exclude: Set<String>,
        limit: Int = 20,
        seed: Long = 0L
    ): List<String> {
        if (preferredGenres.isEmpty() || candidates.isEmpty() || limit <= 0) return emptyList()
        return candidates.asSequence()
            .filter { it.id !in exclude }
            .map { c ->
                val shared = c.genres.sumOf { g -> preferredGenres[g] ?: 0 }
                // Gêneros fora do gosto diluem a nota levemente: não premia quem só tem mais gêneros.
                val unmatched = c.genres.count { g -> (preferredGenres[g] ?: 0) == 0 }
                Triple(c.id, shared, shared / (1.0 + 0.1 * unmatched))
            }
            .filter { (_, shared, _) -> shared > 0 }
            // Empate: ordem embaralhada de forma estável pela semente. Sem isso o desempate seguia a
            // ordem do catálogo (mais recentes primeiro) e a lista só mostrava lançamentos do índice.
            .sortedWith(compareByDescending<Triple<String, Int, Double>> { it.third }.thenBy { mix(it.first, seed) })
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun mix(id: String, seed: Long): Long {
        var h = id.hashCode().toLong() xor seed
        h *= -0x61c8864680b583ebL
        return h xor (h ushr 29)
    }

    /**
     * Constrói o mapa de gostos a partir dos gêneros de itens vistos/favoritados.
     * @param favoriteGenres gêneros (crus) dos favoritos — peso maior.
     * @param historyGenres gêneros (crus) do histórico — peso menor.
     */
    fun buildTaste(
        favoriteGenres: List<String?>,
        historyGenres: List<String?>,
        favoriteWeight: Int = 3,
        historyWeight: Int = 1
    ): Map<String, Int> {
        val taste = mutableMapOf<String, Int>()
        favoriteGenres.forEach { raw ->
            parseGenres(raw).forEach { g -> taste[g] = (taste[g] ?: 0) + favoriteWeight }
        }
        historyGenres.forEach { raw ->
            parseGenres(raw).forEach { g -> taste[g] = (taste[g] ?: 0) + historyWeight }
        }
        return taste
    }
}
