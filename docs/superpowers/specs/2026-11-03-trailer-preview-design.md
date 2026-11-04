# Prévia de trailer no fundo dos detalhes

Data: 2026-11-03 · Repositórios: `ntv2` (app), `ntv2-backend` (Supabase), `agente_filmes` (bot)

## Objetivo
Na tela de detalhes de filme e série, o trailer (YouTube, vindo do TMDB) toca **mudo e automático no fundo**,
no lugar da imagem de fundo, estilo "prévia". O link do trailer vive no backend, para todos os itens do catálogo
(novos e antigos).

**Sucesso:**
- Itens antigos e novos têm `trailer_url` no backend depois da próxima importação ou do próximo post.
- Abrir os detalhes mostra a imagem de fundo na hora e, uns 2 s depois, o trailer entra com uma transição suave.
- Nenhuma piora perceptível na abertura dos detalhes nem na navegação pelo controle no Fire TV (AFTKM).
- Trailer indisponível, bloqueado para reprodução fora do YouTube, sem internet ou com a prévia desligada: fica a
  imagem de fundo de hoje, sem mensagem de erro.

**Fora do escopo:** botão para abrir o trailer no app do YouTube, trailer com som ou tela cheia, trailer por
episódio (o episódio usa o trailer da série), e qualquer extração de stream do YouTube (viola os termos de uso).

## Estado atual
- O bot já pega o trailer do TMDB (`tmdb_client._extrair_trailer`: primeiro vídeo do YouTube do tipo "Trailer") e já
  manda `extra.trailer_url` no `catalog-upsert` (`catalogo.py`), para filmes e episódios.
- O app já lê `trailer_url` (`CatalogCardResolver.common` → `pick("trailer_url", "trailer")`, olhando as colunas
  antes do `extra`) e da legenda (`MovieMetadataParser`, chave `Trailer:`). O valor chega em `MediaDetails.trailerUrl`,
  mas **nenhuma tela usa**.
- O `index.json` (`gerar_indice.py`) não tem trailer, e o `import-index` não grava nada em `extra`. Por isso os itens
  importados estão sem trailer.

## Decisão: coluna própria, não `extra`
No plano do catálogo o trailer ia no `extra`. Isso não funciona para o `import-index`: o upsert do PostgREST
**substitui o `extra` inteiro** e apagaria o que o bot gravou (áudio, qualidade, tags etc.). A coluna
`trailer_url text` resolve isso:
- cada fonte grava só as colunas que manda, então o `import-index` e o `catalog-upsert` não se apagam um ao outro;
- o app lê sem mudança, porque `pick("trailer_url")` olha a coluna antes do `extra`;
- o `extra.trailer_url` antigo continua sendo lido como reserva.

## Backend (`ntv2-backend`)
1. **Migration `2026110300xxxx_catalog_trailer.sql`:** `alter table catalog_movies, catalog_series,
   catalog_episodes add column trailer_url text`. O trigger `catalog_track_change` compara a linha inteira, então
   um trailer novo muda o `changed_at` e entra na próxima diferença do app sem mudança no trigger. `catalog_row` não
   filtra colunas, então o campo sai em `catalog_changes` e `catalog_lookup` sem mudança.
2. **`catalog-upsert`:** `trailer_url` entra em `COMMON` e `EPISODE_COLUMNS` (`_shared/catalog.ts`). Validação: string
   `https://` de até 300 caracteres, ou `null`. Qualquer outro valor é recusado com `invalid_body`.
3. **`import-index`:** `mapMovie`, `mapSeries` (série) e episódios gravam `trailer_url: str(o.trailer_url)`. O
   índice manda trailer em todo item, então gravar `null` quando falta é correto, porque reflete o TMDB.
4. **`CONTRATO.md`:** documentar a coluna (link do YouTube `watch?v=` ou `youtu.be/`) e a precedência no app
   (coluna antes de `extra.trailer_url`).
5. **Testes:** pgTAP (a coluna existe; um trailer novo muda o `changed_at`; o mesmo trailer não muda) e Deno (validação
   do `trailer_url` no `catalog-upsert`; mapeamento do `import-index` extraído para uma função testável, se ainda
   não for).

## Bot (`agente_filmes`)
1. **`gerar_indice.py`:** filmes e séries do índice ganham `trailer_url`; os episódios não (o app herda o da série).
   - Um único `_backfill_trailers`, como os `_backfill_logos`/`_backfill_imdb` que já existem, cobre itens novos e
     antigos. Busca `/{movie|tv}/{id}/videos`, uma vez por série. Só busca o que ainda não tem a chave no cache e grava
     `None` também, para não buscar de novo a cada 6 h.
   - `tmdb_client._extrair_trailer` prefere o trailer em pt e cai no en. Toda chamada com `videos` passa
     `include_video_language=pt,en,null`, porque com `language=pt-BR` o TMDB só devolveria vídeos em pt.
2. **`catalogo.py`:** `trailer_url` passa a ir **na coluna**: sai do `extra` em `item_filme`/`item_episodio` e entra
   no nível de cima. A série (`item_serie_do_episodio`) também passa a mandar o dela.
3. **Testes:** pytest com o `trailer_url` no item do índice, o backfill que não busca de novo, e o item do
   `catalog-upsert` com o trailer na coluna.

## App (`ntv2`)
### Dados
- Nenhuma mudança no catálogo nem no resolver. Garantir que o episódio herda o `trailerUrl` da série quando não tem
  o próprio (conferir a junção em `CatalogCardResolver.episode`).
- Busca sem backend (`SearchIndexRepository`, a partir do `index.json`): ler `trailer_url` do item, se o modelo de
  detalhes dela for preenchido por ali.
- `YouTubeIds.videoId(url): String?`: aceita `youtube.com/watch?v=`, `youtu.be/`, `youtube.com/embed/` e
  `m.youtube.com`. Qualquer outro link devolve `null`, e então não há prévia.

### Componente `TrailerBackdrop` (`feature/media/presentation/trailer/`)
- `AndroidView` com WebView que carrega uma página HTML local com o **player oficial (IFrame API)**:
  `autoplay=1, mute=1, controls=0, loop=1, playlist=<id>, playsinline=1, rel=0, iv_load_policy=3, disablekb=1`.
- `loadDataWithBaseURL("https://nobrinho.github.io/", …)`, o domínio do site do projeto, que também vai no `origin`
  do player: sem origem/referer, o YouTube devolve erro 153.
- WebView: `mediaPlaybackRequiresUserGesture = false`, JavaScript ligado, `isFocusable = false`,
  `isFocusableInTouchMode = false`, sem tratar toques. O foco do controle continua nos botões da tela.
- Ponte JS → Kotlin (`@JavascriptInterface`) com os estados `playing`, `ended` e `error(code)`.
- Exibição:
  - a imagem de fundo aparece primeiro, como hoje;
  - o WebView só é criado depois de **2 s** de tela aberta e de a imagem ter carregado;
  - ele fica com transparência 0 e só aparece (transição de ~600 ms, ou corte seco com as animações desligadas)
    quando chega `playing`;
  - fica por baixo do mesmo degradê de hoje;
  - escala de ~1,2× com corte, para esconder a marca e o título que o player mostra nas bordas.
- Erros:
  - `error` (101/150 = bloqueado fora do YouTube, 153, 2, 5, 100) ou nada de `playing` em 8 s: destrói o WebView e
    fica a imagem;
  - o ID que falhou fica guardado em memória durante a sessão, para não tentar de novo.
- Ciclo de vida: `stopLoading` + `destroy()` quando o componente sai da tela (sair dos detalhes, trocar de item, abrir
  o player). Em `ON_STOP` o vídeo pausa e em `ON_START` volta. Nunca roda junto com o player do app.
- Usado em `MovieDetailsOverlay` (layout de celular e de TV) e `SeriesDetailsOverlay`, no mesmo lugar do
  `AsyncImage` do fundo.

### Liga/desliga (todos precisam estar ligados)
1. Configuração local **"Prévia de trailer"** (DataStore, ao lado de `showCovers`/`animationsEnabled`), ligada por
   padrão. Opção nova na tela de Configurações; nada é removido.
2. Flag remota **`flags.trailer_preview`** no `app_config` (`RemoteConfigRepository`). **Se faltar, conta como
   ligada**: a flag existe para desligar de longe, então não precisa de migration. Para desligar, basta gravar
   `false` no `app_config`; o procedimento entra no `OPERACAO.md`.
3. `showCovers` ligado (sem capas não há fundo).
4. Celular com **economia de dados** do sistema ativa (`ConnectivityManager.restrictBackgroundStatus`) não toca.

### Testes
- JVM: `YouTubeIds.videoId` (formatos válidos, link com parâmetros extras, link que não é do YouTube) e a regra de
  liga/desliga (combinações das quatro condições).
- A decisão "criar ou não o WebView" fica numa função pura (`shouldPreviewTrailer(...)`) coberta pelo teste JVM.
  Sem teste de UI em Compose.
- Aparelhos:
  - **Fire TV AFTKM**: abertura dos detalhes, navegação pelo controle com o trailer tocando, memória depois de abrir e
    fechar 20 detalhes, abrir o player logo em seguida (o decoder fica livre?);
  - **S23**: o mesmo, mais economia de dados.

## Ordem de entrega (cada etapa entra sozinha)
1. **Backend:** migration + `catalog-upsert` + `import-index` + contrato + testes. Aplicar na nuvem só quando o
   usuário pedir.
2. **Bot:** `gerar_indice` com trailer e backfill; `catalogo.py` mandando a coluna. Na próxima importação os itens
   antigos ganham trailer, e o app baixa só a diferença.
3. **App:** `YouTubeIds`, `TrailerBackdrop`, liga/desliga e Configurações. Compilar e rodar os testes JVM. O release
   fica a critério do usuário.

## Riscos
- **Peso no Fire TV:** o WebView com vídeo usa o mesmo decoder fraco. Para conter: só começa após 2 s, só um por vez, é
  destruído ao sair, e existe a flag remota. Se a validação no AFTKM mostrar travadas, a saída é desligar só na TV
  (adicionar `trailer_preview_tv` depois; não entra agora).
- **Trailers bloqueados fora do YouTube:** fica a imagem, sem erro na tela. Aceitável.
- **Mudanças no player do YouTube:** o erro 153 e outras regras novas caem no tratamento de erro. A flag remota é a
  saída de emergência.
- **Custo do backend:** uma coluna de texto curta. A diferença gerada pelo backfill é de uma vez só. Continua no Free.
- **Quota do TMDB:** o backfill faz uma chamada por item que ainda não tem trailer, em paralelo limitado como o
  `_backfill_logos`, e uma vez só por causa do cache.
