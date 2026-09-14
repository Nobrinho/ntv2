package com.ntv2.app.feature.media.domain

/** Detalhes ricos de um filme (pôster/fundo + sinopse + metadados), para a tela de Detalhes. */
data class MovieDetails(
    val title: String,
    val posterPath: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null,
    // Formato rico.
    val originalTitle: String? = null,
    val backdropPath: String? = null,
    val durationSeconds: Int = 0,
    val rating: Double? = null,
    val ageRating: String? = null,
    val country: String? = null,
    val quality: String? = null,
    val studio: String? = null,
    val cast: List<com.ntv2.app.core.telegram.media.CastMemberMeta> = emptyList(),
    val trailerUrl: String? = null,
    val category: String? = null,
    val collection: String? = null
)

/**
 * Cache em memória (singleton) de detalhes por mediaId. A Biblioteca popula ao montar os cards;
 * a tela de reprodução lê por mediaId — evita passar sinopse longa na rota de navegação.
 */
class MediaDetailsCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, MovieDetails>()

    fun put(mediaId: String, details: MovieDetails) {
        map[mediaId] = details
    }

    fun get(mediaId: String): MovieDetails? = map[mediaId]
}
