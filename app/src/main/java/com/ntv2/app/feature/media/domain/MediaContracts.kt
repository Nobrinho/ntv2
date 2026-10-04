package com.ntv2.app.feature.media.domain


/** Tipo da mídia reproduzível. Episódios são unidades reproduzíveis agrupadas por série. */
enum class MediaType { MOVIE, EPISODE }

data class MediaItemSummary(
    val mediaId: String,
    val channelId: Long,
    val channelTitle: String,
    val title: String,
    val caption: String?,
    val fileName: String?,
    val durationSeconds: Int,
    val thumbnailPath: String?,
    val fileId: Int,
    val width: Int = 0,
    val height: Int = 0,
    /** Proporção (largura/altura) da capa exibida; 0 = desconhecida. */
    val coverAspectRatio: Float = 0f,
    val posterPath: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val director: String? = null,
    val audio: String? = null,
    val genres: String? = null,
    // Formato rico (canal próprio).
    val originalTitle: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    val ageRating: String? = null,
    val country: String? = null,
    val quality: String? = null,
    val studio: String? = null,
    val cast: List<com.ntv2.app.core.telegram.media.CastMemberMeta> = emptyList(),
    val trailerUrl: String? = null,
    val tmdbId: String? = null,
    val category: String? = null,
    val collection: String? = null,
    val tags: String? = null,
    // Séries/episódios. Para MOVIE, ficam nulos. Progresso/histórico continuam por [mediaId].
    val mediaType: MediaType = MediaType.MOVIE,
    val seriesTmdbId: Long? = null,
    val episodeTmdbId: Long? = null,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val airDate: String? = null,
    /** Filme dividido em partes no Telegram: total de partes (>= 2). 1 = arquivo único. */
    val partCount: Int = 1
) {
    /**
     * Chave estável de deduplicação/biblioteca:
     *  - filme:    `movie:{tmdbId}`
     *  - episódio: `tv:{seriesTmdbId}:s{seasonNumber}:e{episodeNumber}`
     * Null quando faltam os ids necessários (ex.: canais simples sem TMDB).
     */
    val dedupKey: String?
        get() = when (mediaType) {
            MediaType.EPISODE ->
                if (seriesTmdbId != null && seasonNumber != null && episodeNumber != null)
                    "tv:$seriesTmdbId:s$seasonNumber:e$episodeNumber" else null
            MediaType.MOVIE -> tmdbId?.takeIf { it.isNotBlank() }?.let { "movie:$it" }
        }
}

/** Agrupador não reproduzível: uma série com suas temporadas. */
data class SeriesSummary(
    val tmdbId: Long,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val seasons: List<SeasonSummary>,
    /** Ano da série (do 1º episódio com data), se o índice trouxer. */
    val year: Int? = null
) {
    /** Chave de biblioteca da série (favoritar o card da série). */
    val libraryKey: String get() = "tv:$tmdbId"
}

data class SeasonSummary(val number: Int, val episodes: List<MediaItemSummary>)

data class MediaPage(
    val items: List<MediaItemSummary>,
    // Cursor para a próxima página (fromMessageId do TDLib). 0 => não há mais resultados.
    val nextCursor: Long
)

interface MediaRepository {
    suspend fun fetchChannelVideos(
        channelId: Long,
        channelTitle: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage

    suspend fun searchChannelVideos(
        channelId: Long,
        channelTitle: String,
        query: String,
        fromMessageId: Long,
        limit: Int
    ): MediaPage

    /** Página "para cima": vídeos mais novos que [newerThanMessageId] (do mais novo ao mais antigo). */
    suspend fun fetchNewerChannelVideos(
        channelId: Long,
        channelTitle: String,
        newerThanMessageId: Long,
        limit: Int
    ): MediaPage = MediaPage(emptyList(), 0L)

    /** Resolve uma mídia pelo id da mensagem (usado pela busca via índice para obter o fileId). */
    suspend fun getVideoByMessage(
        channelId: Long,
        channelTitle: String,
        messageId: Long
    ): MediaItemSummary?
}

/** Resultado de preparar um filme dividido em partes para tocar. */
sealed interface MultiPartPrepareResult {
    /** Todas as partes foram achadas e registradas: dá para tocar. */
    data object Ready : MultiPartPrepareResult

    /** Faltam partes no canal (índices de 1) — o filme não toca inteiro. */
    data class Incomplete(val missing: List<Int>, val total: Int) : MultiPartPrepareResult

    /** Não foi possível localizar as partes (rede, mensagem apagada, tamanho desconhecido). */
    data object Failed : MultiPartPrepareResult
}

/**
 * Antes de tocar um filme dividido: acha todas as partes no canal e as registra para o player
 * (que passa a abrir o filme como um arquivo só). [messageId] é a mensagem de qualquer parte.
 */
interface MultiPartPreparer {
    suspend fun prepare(channelId: Long, messageId: Long): MultiPartPrepareResult
}
