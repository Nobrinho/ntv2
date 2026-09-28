# Séries e episódios — contrato técnico do app

O bot publica `docs/index.json` no schema 2. O array `movies` permanece inalterado; séries entram em
`series[]`, agrupadas por `tmdb_id`, `seasons[]` e `episodes[]`. Cada episódio contém seu próprio
`video_message_id`, que resolve o arquivo no TDLib.

## Modelo de domínio

Adicionar a `MediaItemSummary`:

```kotlin
enum class MediaType { MOVIE, EPISODE }

val mediaType: MediaType = MediaType.MOVIE
val seriesTmdbId: Long? = null
val episodeTmdbId: Long? = null
val seriesTitle: String? = null
val seasonNumber: Int? = null
val episodeNumber: Int? = null
val airDate: String? = null
```

Criar agrupadores não reproduzíveis:

```kotlin
data class SeriesSummary(
    val tmdbId: Long,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val seasons: List<SeasonSummary>,
)

data class SeasonSummary(val number: Int, val episodes: List<MediaItemSummary>)
```

O `MediaItemSummary` continua sendo a unidade reproduzível. Isso preserva o player, o progresso e o
histórico existentes.

## Leitura do índice

Em `SearchIndexRepository`, manter a leitura de `movies` e adicionar `series`. Um clique em episódio
resolve `video_message_id` por `MediaRepository.getVideoByMessage`. A busca deve indexar título da
série, título do episódio e código `SxxExx`.

O identificador local do episódio continua sendo `channelId_messageId`. IDs do TMDB servem para
agrupamento e metadados, nunca como chave do progresso.

## Interface

1. A biblioteca mostra um card por série, usando pôster e fundo da série.
2. O card abre `SeriesDetailsOverlay`.
3. O overlay seleciona uma temporada e lista seus episódios em ordem numérica.
4. Cada linha mostra `SxxExx`, título, sinopse, duração, imagem e progresso.
5. O botão reproduzir envia o `MediaItemSummary` do episódio ao fluxo atual.
6. Ao concluir, oferecer o menor episódio seguinte existente na mesma temporada; depois, o primeiro
   episódio da próxima temporada.

## Deduplicação e biblioteca do usuário

Não aplicar a deduplicação atual somente por `tmdbId` aos episódios. Usar:

```text
filme: movie:{tmdbId}
série: tv:{seriesTmdbId}
episódio: tv:{seriesTmdbId}:s{seasonNumber}:e{episodeNumber}
```

Favoritar o card da série deve persistir a chave da série. Progresso e histórico permanecem por
`mediaId`, portanto são independentes para cada vídeo.

## Compatibilidade com mensagens do canal

Expandir `MovieMetadataParser` para os rótulos `Série`, `Temporada`, `Episódio`, `Título do episódio`,
`Exibição`, `TMDB Série` e `TMDB Episódio`. O campo legado `TMDB` continuará contendo o ID da série.
Assim, versões antigas ainda exibem o episódio como um vídeo comum, enquanto versões novas usam a
hierarquia do índice v2.
