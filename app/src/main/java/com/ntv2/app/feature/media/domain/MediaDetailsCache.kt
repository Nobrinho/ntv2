package com.ntv2.app.feature.media.domain

/** Detalhes ricos de um filme (pôster + sinopse + metadados), para a tela de reprodução. */
data class MovieDetails(
    val title: String,
    val posterPath: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null
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
