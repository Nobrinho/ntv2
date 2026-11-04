# Prévia de trailer — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** guardar o link do trailer (YouTube/TMDB) no catálogo do backend, para todos os itens, e tocá-lo mudo e automático no fundo dos detalhes de filme e série no app.

**Architecture:** uma coluna `trailer_url` nas três tabelas `catalog_*`, preenchida por duas fontes que não se sobrepõem: o `import-index`, a partir do `index.json`, e o `catalog-upsert`, chamado pelo bot. O app já lê a coluna pelo `CatalogCardResolver`. Um composable `TrailerBackdrop` (WebView + player oficial do YouTube) substitui a imagem de fundo quando o vídeo de fato começa a tocar.

**Tech Stack:** Supabase (Postgres + pgTAP, Edge Functions em Deno/TS), Python 3 (pytest) no `agente_filmes`, Kotlin/Compose (JUnit) no `ntv2`.

**Spec:** `docs/superpowers/specs/2026-11-03-trailer-preview-design.md`

## Global Constraints
- Repositórios: `C:\DEV\ntv2-backend`, `C:\DEV\agente_filmes`, `C:\DEV\ntv2`. Cada task faz commit **no repositório que ela altera**.
- Commits **sem** linha `Co-Authored-By` (preferência do usuário). Sem push, sem `supabase db push`/deploy de função e sem release do app. Tudo isso só quando o usuário pedir.
- `trailer_url` válido: string que começa com `https://`, com no máximo 300 caracteres, ou `null`.
- Formatos de link aceitos pelo app: `youtube.com/watch?v=`, `m.youtube.com/watch?v=`, `youtu.be/`, `youtube.com/embed/`. O ID tem 11 caracteres `[A-Za-z0-9_-]`.
- O trailer do episódio é o da série. Não existe trailer por episódio no índice.
- Flag remota `trailer_preview`. **Se faltar, conta como ligada.**
- Configuração local `trailer_preview` (DataStore), padrão `true`.
- Player: `autoplay=1&mute=1&controls=0&loop=1&playlist=<id>&playsinline=1&rel=0&iv_load_policy=3&disablekb=1&enablejsapi=1&origin=https://nobrinho.github.io`. Base URL do WebView: `https://nobrinho.github.io/`.
- Tempos: o WebView é criado 2000 ms depois de a imagem carregar; o trailer entra com transição de 600 ms (corte seco sem animações); sem `playing` em 8000 ms conta como falha. Escala de 1,2×.
- Nada sai da tela: a imagem de fundo continua sendo o estado inicial e a reserva para qualquer falha.

## Review Focus
- **Trailer bloqueado fora do YouTube (erro 101/150) ou erro 153:** fica a imagem, sem mensagem, e o vídeo não é tentado de novo na sessão. Teste em App-4 (`TrailerHtmlTest` cobre a ponte; `TrailerPreviewPolicyTest` cobre `failedIds`).
- **Link que não é do YouTube ou ID malformado no catálogo** (ex.: `vimeo.com/…`, `watch?v=` vazio): sem prévia, sem erro. Teste em App-1.
- **`import-index` com item sem `trailer_url`:** grava `null` e não toca no `extra`. Teste em Backend-3 (`extra` não aparece no objeto mapeado).
- **Reimportação com o mesmo trailer:** o `changed_at` não muda, então o app não baixa o catálogo de novo. Teste pgTAP em Backend-1.
- **Controle remoto da TV com o trailer tocando:** o foco nunca vai para o WebView. É garantido por `isFocusable=false` em App-4 e conferido no Fire TV em App-5.

---

## Backend (`C:\DEV\ntv2-backend`)

### Task Backend-1: coluna `trailer_url`

**Files:**
- Create: `supabase/migrations/20261103000100_catalog_trailer.sql`
- Create: `supabase/tests/11_catalog_trailer.sql`

**Interfaces:**
- Produces: coluna `trailer_url text` (nula) em `public.catalog_movies`, `public.catalog_series` e `public.catalog_episodes`. Ela sai em `catalog_changes`/`catalog_lookup` sem mudança de função, porque `private.catalog_row` só tira `search_key`, `cast_key` e `doc_key`.

- [ ] **Step 1: escrever o teste pgTAP** `11_catalog_trailer.sql` (mesmo esqueleto de `10_catalog_sync.sql`: `begin`, `plan(5)`, limpar as tabelas, `rollback`):
  - `has_column('public','catalog_movies','trailer_url')`, e o mesmo para `catalog_series` e `catalog_episodes` (3 asserções);
  - inserir o filme 880201; com o trigger `catalog_movies_changed` **desligado**, colocar `changed_at = now() - interval '1 hour'`; religar o trigger; `update ... set trailer_url = 'https://www.youtube.com/watch?v=abcdefghijk'` → `ok(changed_at > now() - interval '1 minute', 'trailer novo muda changed_at')`;
  - guardar o `changed_at`, repetir o mesmo `update` → `is(changed_at, guardado, 'mesmo trailer não muda changed_at')`.
- [ ] **Step 2: rodar** `npx supabase test db`. Esperado: FAIL em `11_catalog_trailer.sql` (coluna inexistente).
- [ ] **Step 3: migration** com `alter table ... add column trailer_url text;` nas três tabelas e um cabeçalho de comentário no estilo das outras migrations (por que existe a coluna e por que não vai no `extra`).
- [ ] **Step 4: rodar** `npx supabase db reset` e depois `npx supabase test db`. Esperado: todos os arquivos PASS.
- [ ] **Step 5: commit** `feat: coluna trailer_url no catalogo (filme, serie, episodio)`.

### Task Backend-2: `catalog-upsert` aceita `trailer_url`

**Files:**
- Modify: `supabase/functions/_shared/catalog.ts` (`COMMON`, `EPISODE_COLUMNS`, `pick`)
- Test: `supabase/functions/_shared/catalog_test.ts`
- Modify: `CONTRATO.md` (seção do `catalog-upsert` e das colunas do catálogo)

**Interfaces:**
- Consumes: a coluna de Backend-1.
- Produces: `trailer_url` aceito em `movies[]`, `series[]` e `episodes[]`. Valor inválido gera `BadRequest("invalid_body", "<where>: trailer_url ...")`.

- [ ] **Step 1: teste** `Deno.test("catalog-upsert: trailer_url", ...)`:
  - `parseUpsertRequest({ movies: [{ tmdb_id: 1, trailer_url: "https://youtu.be/abcdefghijk" }] }).movies[0].trailer_url === "https://youtu.be/abcdefghijk"`;
  - `trailer_url: null` é aceito;
  - em `episodes`, o mesmo campo é aceito;
  - `assertThrows(..., BadRequest, "trailer_url")` para `"http://x"`, para `123` e para `"https://" + "a".repeat(300)`.
- [ ] **Step 2: rodar** `deno test supabase/functions/_shared/`. Esperado: FAIL (`unknown_column "trailer_url"`).
- [ ] **Step 3:** colocar `"trailer_url"` em `COMMON` e em `EPISODE_COLUMNS`. Em `pick`, ao lado da checagem do `extra`, validar: `null`, ou string que começa com `https://` e tem até 300 caracteres.
- [ ] **Step 4: rodar** os testes. Esperado: PASS.
- [ ] **Step 5: `CONTRATO.md`:** coluna `trailer_url` (link do YouTube, ≤ 300, `https://`), quem grava (`import-index` em filme/série; o bot em todas as tabelas) e a precedência no app (coluna antes de `extra.trailer_url`, que continua valendo para itens antigos). Em `OPERACAO.md`, o procedimento da flag: `update app_config set value = jsonb_set(value, '{trailer_preview}', 'false') where key = 'flags';`.
- [ ] **Step 6: commit** `feat: catalog-upsert aceita trailer_url (https, ate 300); contrato e flag trailer_preview`.

### Task Backend-3: `import-index` grava o trailer

**Files:**
- Create: `supabase/functions/_shared/import_map.ts` (move de `import-index/index.ts`: `str`, `tmdbImage`, `int`, `nonZero`, `year`, `date`, `videoMessageId`, `links`, `genres`, `cast`, `mapMovie`, `mapSeries`, sem mudar comportamento)
- Modify: `supabase/functions/import-index/index.ts` (importa de `../_shared/import_map.ts`)
- Test: `supabase/functions/_shared/import_map_test.ts`

**Interfaces:**
- Produces: `export function mapMovie(o: Json, chatId: number | null): Json | null` e `export function mapSeries(o: Json, chatId: number | null): { series: Json; episodes: Json[] } | null`. Filme e série ganham `trailer_url: string | null`. Os episódios **não** levam a chave, para não apagar o que o bot gravou.

- [ ] **Step 1: testes:**
  - `mapMovie({tmdb_id: 603, title: "Matrix", trailer_url: "https://www.youtube.com/watch?v=abcdefghijk"}, -100).trailer_url` é o link;
  - sem `trailer_url` no item → `null`;
  - `"extra" in mapMovie(...)` é `false`;
  - `mapSeries({tmdb_id: 1399, trailer_url: "https://youtu.be/abcdefghijk", seasons: [{number: 1, episodes: [{episode_number: 1}]}]}, null)`: `series.trailer_url` é o link e `"trailer_url" in episodes[0]` é `false`.
- [ ] **Step 2: rodar** `deno test supabase/functions/_shared/`. Esperado: FAIL (módulo inexistente).
- [ ] **Step 3:** mover as funções para `import_map.ts` e acrescentar `trailer_url: str(o.trailer_url)` em `mapMovie` e no objeto `series` de `mapSeries`.
- [ ] **Step 4: rodar** os testes (PASS) e `deno check supabase/functions/import-index/index.ts` (sem erro).
- [ ] **Step 5: commit** `feat: import-index grava trailer_url de filmes e series (mapeamento em _shared/import_map.ts, testado)`.

---

## Bot (`C:\DEV\agente_filmes`)

### Task Bot-1: o TMDB devolve trailer em português ou inglês

**Files:**
- Modify: `tmdb_client.py` (`_extrair_trailer` e os `append_to_response` com `videos` nas linhas ~198, 355, 411 e 427)
- Test: `tests/test_tmdb_client.py`

**Interfaces:**
- Produces:
  - `_extrair_trailer(videos: dict) -> str | None`: entre os vídeos com `site == "YouTube"` e `type == "Trailer"`, prefere `iso_639_1 == "pt"`; se não houver, devolve o primeiro. Formato `https://www.youtube.com/watch?v=<key>`;
  - os detalhes de episódio passam a ter também `"serie_trailer_url": _extrair_trailer(serie["videos"])`.
- Motivo: com `language=pt-BR` o TMDB só devolve vídeos em pt. Por isso todas as chamadas com `videos` precisam de `include_video_language=pt,en,null`.

- [ ] **Step 1: testes:**
  - `test_trailer_prefere_portugues`: resultados `[en Trailer k1, pt Trailer k2]` devolvem o link de `k2`;
  - `test_trailer_cai_no_primeiro_sem_pt`: só `en` devolve `k1`;
  - `test_trailer_ignora_teaser_e_vimeo`: devolve `None`.
- [ ] **Step 2: rodar** `pytest tests/test_tmdb_client.py -k trailer -v`. Esperado: FAIL em `prefere_portugues`.
- [ ] **Step 3:** implementar a preferência; colocar `"include_video_language": "pt,en,null"` nos parâmetros das quatro chamadas; colocar `serie_trailer_url` no dicionário do episódio (linha ~478).
- [ ] **Step 4: rodar** `pytest tests/test_tmdb_client.py -v`. Esperado: PASS.
- [ ] **Step 5: commit** `feat(tmdb): trailer em pt quando houver, en como reserva; trailer da serie no episodio`.

### Task Bot-2: `trailer_url` no `index.json` (com preenchimento dos antigos)

**Files:**
- Modify: `gerar_indice.py` (nova `_fetch_trailer`, nova `_backfill_trailers`, chamada em `build_index` depois de `_backfill_imdb`, campos no filme e na série)
- Test: `tests/test_gerar_indice.py`

**Interfaces:**
- Consumes: `tmdb_client._extrair_trailer` (Bot-1).
- Produces:
  - `_fetch_trailer(caminho: str) -> str | None`: faz `GET /3/{caminho}/videos?api_key&language=pt-BR&include_video_language=pt,en,null` e passa o resultado por `_extrair_trailer`. `caminho` é `"movie/603"` ou `"tv/1399"`;
  - `_backfill_trailers(tarefas: dict, cache: dict, verbose: bool = False) -> None`: no filme grava `cache[chave]["trailer_url"]`; no episódio grava `cache[chave]["series_trailer_url"]`. Busca uma vez por série, grava `None` quando não há trailer e só busca as chaves que ainda não existem. Usa `ThreadPoolExecutor(max_workers=8)` e salva o `CACHE` no fim, como `_backfill_imdb`;
  - no índice: `movies[i]["trailer_url"] = enr.get("trailer_url")` e `series[i]["trailer_url"]` (primeiro `series_trailer_url` não nulo entre os episódios dela; série avulsa fica `None`). Os episódios não ganham o campo.

- [ ] **Step 1: testes** (no padrão de `test_backfill_busca_uma_vez_por_serie_e_grava_none`, com `monkeypatch` em `_fetch_trailer`):
  - `test_backfill_trailer_uma_vez_por_serie_e_grava_none`: dois episódios da mesma série e um filme sem trailer geram 2 chamadas (`tv/X` e `movie/Y`), e o cache do filme fica com `"trailer_url": None`;
  - `test_backfill_trailer_nao_busca_de_novo`: com a chave já no cache, 0 chamadas;
  - `test_indice_leva_trailer_em_filme_e_serie`: `indice["movies"][0]["trailer_url"]` e `indice["series"][0]["trailer_url"]` vêm do cache, e `"trailer_url" not in` no episódio.
- [ ] **Step 2: rodar** `pytest tests/test_gerar_indice.py -k trailer -v`. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4: rodar** `pytest tests/test_gerar_indice.py -v`. Esperado: tudo PASS.
- [ ] **Step 5: commit** `feat(indice): trailer_url em filmes e series, com backfill unico por serie (None gravado)`.

### Task Bot-3: `catalogo.py` manda o trailer na coluna

**Files:**
- Modify: `catalogo.py` (`item_filme`, `item_episodio`, `item_serie_do_episodio`)
- Test: `tests/test_catalogo_v3.py`

**Interfaces:**
- Consumes: `dados["trailer_url"]` e `dados["serie_trailer_url"]` (Bot-1); a coluna aceita pelo `catalog-upsert` (Backend-2).
- Produces:
  - `item_filme(...)["trailer_url"]` e `item_episodio(...)["trailer_url"]` no nível de cima, e **não** mais em `extra`;
  - `item_serie_do_episodio(dados)["trailer_url"] = dados.get("serie_trailer_url")`.
  - Valor vazio continua sendo removido por `_sem_vazios`.

- [ ] **Step 1: testes:**
  - `item_filme(DADOS, REG)["trailer_url"] == "https://youtu.be/x"` e `"trailer_url" not in item["extra"]`;
  - o mesmo para `item_episodio`;
  - `item_serie_do_episodio({..., "serie_trailer_url": "https://youtu.be/s"})["trailer_url"] == "https://youtu.be/s"`.
  - Ajustar as asserções antigas que esperavam o trailer em `extra`.
- [ ] **Step 2: rodar** `pytest tests/test_catalogo_v3.py -v`. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4: rodar** `pytest -q`. Esperado: a suíte inteira PASS.
- [ ] **Step 5: commit** `feat(catalogo): trailer_url vai na coluna do catalog-upsert (filme, episodio e serie)`.

---

## App (`C:\DEV\ntv2`)

### Task App-1: ID do YouTube e regra de exibição

**Files:**
- Create: `app/src/main/java/com/ntv2/app/feature/media/presentation/trailer/TrailerPreviewPolicy.kt`
- Test: `app/src/test/java/com/ntv2/app/feature/media/presentation/trailer/TrailerPreviewPolicyTest.kt`

**Interfaces:**
- Produces:
  - `object YouTubeIds { fun videoId(url: String?): String? }`: devolve o ID de 11 caracteres nos formatos das Global Constraints e `null` em qualquer outro caso;
  - `fun shouldPreviewTrailer(videoId: String?, userEnabled: Boolean, remoteEnabled: Boolean, showCovers: Boolean, dataSaver: Boolean, failedIds: Set<String>): Boolean`: `true` só com `videoId != null`, as três chaves ligadas, `!dataSaver` e `videoId !in failedIds`;
  - `object TrailerFailures { val ids: Set<String>; fun add(id: String) }`: só na memória, durante a sessão, seguro entre threads.

- [ ] **Step 1: testes:**
  - `videoId` dos links:
    - `https://www.youtube.com/watch?v=dQw4w9WgXcQ` → `dQw4w9WgXcQ`;
    - `https://m.youtube.com/watch?feature=x&v=dQw4w9WgXcQ&t=10` → `dQw4w9WgXcQ`;
    - `https://youtu.be/dQw4w9WgXcQ?si=a` → `dQw4w9WgXcQ`;
    - `https://www.youtube.com/embed/dQw4w9WgXcQ` → `dQw4w9WgXcQ`;
  - `null` para `https://vimeo.com/123`, `https://www.youtube.com/watch?v=` e `https://youtu.be/curto`;
  - `shouldPreviewTrailer`: o caso todo ligado dá `true`, e cada condição desligada sozinha dá `false` (6 casos).
- [ ] **Step 2: rodar** `.\gradlew :app:testDebugUnitTest --tests "*TrailerPreviewPolicyTest*"`. Esperado: FAIL (não compila).
- [ ] **Step 3:** implementar com `java.net.URI` (não `android.net.Uri`), para o teste rodar na JVM sem Robolectric.
- [ ] **Step 4: rodar** o mesmo comando. Esperado: PASS.
- [ ] **Step 5: commit** `feat(trailer): id do YouTube e regra de exibicao da previa`.

### Task App-2: o trailer chega à série e ao episódio

**Files:**
- Modify: `app/src/main/java/com/ntv2/app/feature/media/data/index/SearchIndexRepository.kt` (`IndexSeries` e o parser na linha ~333)
- Modify: `app/src/main/java/com/ntv2/app/feature/media/data/index/CatalogIndexSnapshot.kt:59`
- Modify: `app/src/main/java/com/ntv2/app/feature/media/domain/MediaContracts.kt:70` (`SeriesSummary`)
- Modify: `app/src/main/java/com/ntv2/app/feature/media/presentation/viewmodel/MediaLibraryViewModel.kt:884` (`toSeriesSummary`)
- Modify: `app/src/main/java/com/ntv2/app/core/catalog/CatalogCardResolver.kt` (`episode`)
- Test: `CatalogIndexSnapshotTest.kt`, `CatalogCardResolverTest.kt` e o teste do parser do índice (`SearchIndexImageTest.kt` ou o arquivo de parser equivalente em `feature/media/data/index`)

**Interfaces:**
- Produces:
  - `IndexSeries.trailerUrl: String? = null` (último parâmetro, com padrão, para não quebrar quem já chama);
  - `SeriesSummary.trailerUrl: String? = null`;
  - no episódio do resolver, `trailerUrl = own.trailerUrl ?: seriesMeta?.trailerUrl`.
  - Fontes: `d.text("trailer_url")` no snapshot do catálogo; `o.cleanString("trailer_url")` no `index.json`.

- [ ] **Step 1: testes:**
  - snapshot com série que tem `trailer_url` → `IndexSeries.trailerUrl` igual ao valor;
  - parser do `index.json`: série com `"trailer_url": "https://youtu.be/abcdefghijk"` mantém o link; `"None"` vira `null`;
  - resolver: episódio sem trailer, cuja série tem a coluna `trailer_url`, devolve `MovieMeta.trailerUrl` da série;
  - resolver: filme com `trailer_url` na coluna e outro em `extra.trailer_url` → vale a coluna.
- [ ] **Step 2: rodar** `.\gradlew :app:testDebugUnitTest --tests "*CatalogIndexSnapshotTest*" --tests "*CatalogCardResolverTest*" --tests "*SearchIndex*"`. Esperado: FAIL.
- [ ] **Step 3:** implementar e passar `trailerUrl` em `toSeriesSummary`.
- [ ] **Step 4: rodar** `.\gradlew :app:testDebugUnitTest`. Esperado: a suíte inteira PASS.
- [ ] **Step 5: commit** `feat(trailer): trailer_url na serie (catalogo e index.json) e episodio herda o da serie`.

### Task App-3: liga/desliga (configuração local + flag remota)

**Files:**
- Modify: `app/src/main/java/com/ntv2/app/core/preferences/UserPreferencesDataStore.kt` (chave `trailer_preview`, padrão `true`)
- Modify: `app/src/main/java/com/ntv2/app/feature/settings/domain/SettingsContracts.kt`
- Modify: `app/src/main/java/com/ntv2/app/feature/settings/presentation/SettingsScreen.kt` e quem a chama (seguir o caminho de `castPhotos`)
- Modify: `app/src/main/java/com/ntv2/app/core/backend/config/RemoteConfigRepository.kt` (`FLAG_TRAILER_PREVIEW = "trailer_preview"`)
- Modify: `MediaLibraryViewModel.kt` (construtor e coleta), `MediaLibraryViewModelFactory`, `state/MediaLibraryUiState.kt` e `di/AppContainer.kt`
- Test: o teste do ViewModel em `app/src/test/java/com/ntv2/app/feature/media/presentation/viewmodel/`

**Interfaces:**
- Produces:
  - `SettingsRepository.trailerPreview: Flow<Boolean>` com padrão `flowOf(true)` e `suspend fun updateTrailerPreview(value: Boolean) = Unit` como métodos padrão da interface (os fakes dos testes não quebram). `DataStoreSettingsRepository` sobrescreve os dois;
  - `MediaLibraryViewModel(..., trailerPreviewRemote: () -> Boolean = { true })`. O `AppContainer` passa `{ remoteConfigRepository.current.flag(RemoteConfig.FLAG_TRAILER_PREVIEW, default = true) }`;
  - `MediaLibraryUiState.trailerPreviewEnabled: Boolean = true`, igual a `settings.trailerPreview && trailerPreviewRemote()`. Recalcula a cada emissão da configuração e na abertura dos detalhes;
  - Configurações: item "Prévia de trailer" com a descrição "Toca o trailer sem som no fundo dos detalhes", logo depois do item de fotos do elenco. Nenhum item sai da tela.

- [ ] **Step 1: testes do ViewModel:**
  - fake de configurações com `trailerPreview = flowOf(false)` → `uiState.trailerPreviewEnabled == false`;
  - configuração ligada e `trailerPreviewRemote = { false }` → `false`;
  - os dois ligados → `true`.
- [ ] **Step 2: rodar** o teste. Esperado: FAIL.
- [ ] **Step 3:** implementar a cadeia DataStore → repositório → ViewModel → estado, o item nas Configurações e a flag.
- [ ] **Step 4: rodar** `.\gradlew :app:testDebugUnitTest`. Esperado: PASS.
- [ ] **Step 5: commit** `feat(trailer): opcao Previa de trailer nas configuracoes e flag remota trailer_preview (ausente = ligada)`.

### Task App-4: `TrailerBackdrop` nos detalhes

**Files:**
- Create: `app/src/main/java/com/ntv2/app/feature/media/presentation/trailer/TrailerHtml.kt`
- Create: `app/src/main/java/com/ntv2/app/feature/media/presentation/trailer/TrailerBackdrop.kt`
- Modify: `app/src/main/java/com/ntv2/app/feature/media/presentation/MovieDetailsOverlay.kt` (fundo do celular em ~165 e da TV em ~218; parâmetro novo `trailerPreviewEnabled: Boolean = false`)
- Modify: `app/src/main/java/com/ntv2/app/feature/media/presentation/SeriesDetailsOverlay.kt` (herói em ~331; parâmetros novos `trailerPreviewEnabled: Boolean = false` e `animationsEnabled: Boolean = true`)
- Modify: `app/src/main/java/com/ntv2/app/feature/media/presentation/MediaLibraryScreen.kt:734` e `:946` (passar `uiState.trailerPreviewEnabled`)
- Test: `app/src/test/java/com/ntv2/app/feature/media/presentation/trailer/TrailerHtmlTest.kt`

**Interfaces:**
- Consumes: `YouTubeIds`, `shouldPreviewTrailer`, `TrailerFailures` (App-1); `MovieDetails.trailerUrl` (já existe); `SeriesSummary.trailerUrl` (App-2); `uiState.trailerPreviewEnabled` (App-3).
- Produces:
  - `object TrailerHtml { const val BASE_URL = "https://nobrinho.github.io/"; fun build(videoId: String): String }`: página com o IFrame API, os parâmetros exatos das Global Constraints, fundo preto e o player em 100% × 100%. Nos eventos `onStateChange == 1` e `onError(code)`, chama `NtvTrailer.playing()` e `NtvTrailer.error(code)`. Ao terminar, o vídeo volta ao início (o `loop` com `playlist` cobre isso);
  - `@Composable fun TrailerBackdrop(videoId: String, animationsEnabled: Boolean, onFailed: (String) -> Unit, modifier: Modifier = Modifier)`, que:
    - cria o WebView no `AndroidView` (JS ligado; `mediaPlaybackRequiresUserGesture = false`; `isFocusable`, `isFocusableInTouchMode` e `isClickable` em `false`; fundo transparente);
    - carrega com `loadDataWithBaseURL(TrailerHtml.BASE_URL, html, "text/html", "utf-8", null)`;
    - registra a ponte `NtvTrailer` com `@JavascriptInterface`;
    - fica com `alpha` 0 até `playing` e depois entra com transição de 600 ms (`snap` sem animações);
    - aplica `graphicsLayer(scaleX = 1.2f, scaleY = 1.2f)` dentro de um `clipToBounds`;
    - chama `onFailed(videoId)` em `error` ou sem `playing` em 8000 ms;
    - pausa em `ON_STOP` e retoma em `ON_START` (`evaluateJavascript("player.pauseVideo()")`/`playVideo()`);
    - no `onDispose`, chama `stopLoading()`, `loadUrl("about:blank")` e `destroy()`.
  - Nos dois overlays, o `TrailerBackdrop` fica por cima do `AsyncImage` do fundo e por baixo do degradê. Ele só é composto quando:
    - `shouldPreviewTrailer(...)` é `true`;
    - `backdropLoaded` é `true`;
    - passaram 2000 ms (`LaunchedEffect(videoId) { delay(2000); ready = true }`).
  - Em `onFailed`, chama `TrailerFailures.add(id)` e tira o trailer da composição.
  - `dataSaver` vem de `ConnectivityManager.restrictBackgroundStatus == RESTRICT_BACKGROUND_STATUS_ENABLED`.

- [ ] **Step 1: testes** em `TrailerHtmlTest`:
  - `build("dQw4w9WgXcQ")` contém `videoId: 'dQw4w9WgXcQ'`, `mute: 1`, `controls: 0`, `playlist: 'dQw4w9WgXcQ'` e `origin: 'https://nobrinho.github.io'`;
  - contém `NtvTrailer.playing()` e `NtvTrailer.error(`;
  - `build("a'b")` lança `IllegalArgumentException` (só aceita o ID validado).
- [ ] **Step 2: rodar** `.\gradlew :app:testDebugUnitTest --tests "*TrailerHtmlTest*"`. Esperado: FAIL.
- [ ] **Step 3:** implementar `TrailerHtml`, `TrailerBackdrop` e a integração nos dois overlays e no `MediaLibraryScreen`.
- [ ] **Step 4: rodar** `.\gradlew :app:testDebugUnitTest :app:assembleDebug`. Esperado: PASS e BUILD SUCCESSFUL.
- [ ] **Step 5: commit** `feat(trailer): previa do trailer no fundo dos detalhes de filme e serie (player oficial do YouTube, mudo)`.

### Task App-5: validação em aparelho

Não gera código novo. Só corrige o que aparecer.

- [ ] **Step 1: build de debug no S23**:
  - abrir os detalhes de um filme com trailer: a imagem aparece e o trailer entra uns 2 s depois, sem som;
  - um filme sem trailer continua só com a imagem;
  - "Prévia de trailer" desligada nas Configurações: sem trailer;
  - economia de dados ativa: sem trailer;
  - apertar Play: o trailer some e o player abre normalmente.
- [ ] **Step 2: Fire TV AFTKM**:
  - navegar pelo controle com o trailer tocando: o foco nunca vai para o vídeo;
  - abrir e fechar 20 detalhes: memória estável no `adb shell dumpsys meminfo com.ntv2.app`;
  - abrir o player logo depois: sem travadas novas (use a skill `ntv2-firetv-logs` se travar).
- [ ] **Step 3:** anotar o resultado numa seção nova "Validação" no fim de `docs/superpowers/specs/2026-11-03-trailer-preview-design.md` e fazer commit `docs: validacao da previa de trailer no S23 e Fire TV`.

---

## Depois do plano (só com o pedido do usuário)
1. `supabase db push` e deploy de `catalog-upsert` e `import-index`.
2. Gerar o índice com o backfill dos trailers e publicar.
3. Release do app pela skill `ntv2-release`.
