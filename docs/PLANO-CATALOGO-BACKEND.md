# Plano: catálogo no backend, Telegram só com o vídeo

Objetivo: metadados (filme, série, episódio) passam a viver no backend Supabase, sem o limite de
1024 caracteres da legenda. O post no Telegram leva só o vídeo e uma legenda mínima com os IDs.

Repositórios envolvidos: `ntv2` (app), `ntv2-backend` (Supabase), `agente_filmes` (bot),
`nbr-cortador` (postagem de filmes grandes).

## Como é hoje
- Biblioteca: cartão montado da legenda (`core/telegram/media/MovieMetadataParser.kt`).
- Busca: `index.json` gerado pelo bot (`gerar_indice.py`, a partir de `postagens_publicadas.json` + TMDB),
  publicado no GitHub Pages; o backend importa o mesmo arquivo a cada 6 h para `catalog_*`.
- Ligação metadado ↔ vídeo: `video_message_id` (parte 1, no filme em partes). O canal vem de `channel` no índice.
- Legenda > 1024: o cortador posta um texto separado antes da parte 1.

## Contrato da legenda mínima (v3)
Uma linha, chaves fixas, ordem livre, o app ignora chave que não conhece:
```
ntv:m_603 tmdb:603 imdb:tt0133093
ntv:e_1399_01_03 tmdb:1399 S01E03
```
- `ntv`: ID interno estável. Filme `m_<tmdb>`; episódio `e_<tmdbSérie>_<TT>_<EE>`. Sem TMDB: `x_<uuid curto>`.
- O título continua no nome do arquivo (`Título (Ano) [Qualidade].ext`), que serve de último recurso.
- Filme em partes: a mesma legenda em todas as partes (o agrupamento continua pelo nome `.partNNofMM`).

## Banco (ntv2-backend) — feito (migration `20261030000100_catalog_sync.sql`)
- `catalog_*`: `ntv_id` calculado e único, `imdb_id`, `chat_id` e `parts` (filme/episódio), `extra jsonb`
  para campos ricos (tagline, classificação, trailer etc. entram ali, sem migration).
- `changed_at` só muda com conteúdo novo; removidos ficam 90 dias em `private.catalog_removed`.
- `catalog_changes(since, after_id, lim)`: diferença em ordem de `(changed_at, ntv_id)`, com cursor
  (`next`), `has_more` e `reset`. Página cheia ~1,2 MB / 28 ms: primeira carga pelo `index.json`.
- `catalog_lookup(ntv_ids)`: até 200 itens.
- Escrita: Edge Function `catalog-upsert` (token próprio, como o `import-index`), chamada pelo bot — etapa 4.
  O `import-index` continua até o bot gravar direto; depois vira só verificação.
- Detalhes no `CONTRATO.md` do backend.

## App (ntv2)
- `core/catalog/`: `CatalogStore` (Room: tabela `catalog_item` por `ntv_id`, `message_id`, `chat_id`, JSON dos
  campos), `CatalogSync` (pede `catalog_changes` na abertura e a cada 6 h; guarda a marca `since`).
  Primeira carga: o `index.json` do Pages (um arquivo, cacheável), depois só diferenças.
- `MovieMetadataParser` ganha o formato v3 (lê `ntv/tmdb/imdb/SxxEyy`).
- Resolução do cartão, em ordem: catálogo local por `ntv_id` → por `(chat_id, message_id)` →
  legenda completa (posts antigos) → nome do arquivo. Nunca cartão vazio.
- Flag `flags.remote_catalog` em `app_config` (desligada = só legenda + `index.json`, como hoje).
- A busca passa a usar o mesmo `CatalogStore` (sem baixar o `index.json` inteiro a cada vez).

## Bot e cortador
- `agente_filmes`: ao publicar, gerar o `ntv_id`, postar só a legenda v3 e chamar `catalog-upsert` com os
  metadados completos (o que hoje vai para a legenda + o que o TMDB tiver). Sem texto separado.
- `nbr-cortador`: mesma legenda v3 em todas as partes; deixa de postar o texto separado.
  Atualizar o `CONTRATO.md` dele e o `tests/test_contrato_bot.py`.
- Repostou um vídeo (`message_id` mudou): o `catalog-upsert` atualiza `message_id` pelo `ntv_id`.

## Etapas (cada uma entra em produção sozinha)
1. **Backend (feito, falta aplicar na nuvem):** migration (colunas + `catalog_changes` + `catalog_lookup`), pgTAP,
   `import-index` preenchendo `imdb_id`/`chat_id`/`parts`. Nada muda para o app.
2. **App, leitura:** `CatalogStore` + `CatalogSync` + flag; cartão usa o catálogo quando houver e cai na legenda.
   Funciona com os posts atuais. Validar no Fire TV (memória, tempo de abertura).
3. **App, parser v3:** entender a legenda mínima. Lançar uma versão **antes** de qualquer post v3 e esperar a base
   atualizar (`min_version_code` sobe só depois).
4. **Bot:** `catalog-upsert` + legenda v3 nos posts novos, primeiro no canal de teste.
5. **Cortador:** legenda v3 nas partes, sem texto separado.
6. **Opcional:** reeditar legendas antigas para v3 (`editMessageCaption`, em lote lento por causa do FloodWait).
7. **Limpeza:** `import-index` e `index.json` viram backup; parser do formato antigo fica para posts antigos.

## Riscos
- **App antigo + post v3** = cartão sem informação. Mitigação: etapa 3 antes da 4 e `min_version_code`.
- **Backend fora:** o catálogo local (Room) segura; com a flag desligada, volta ao `index.json`.
- **Custo:** só diferenças após a primeira carga; primeira carga pelo Pages (grátis). Fica no Free.
- **Fire TV:** sync em background, inserção em lote, sem bloquear a abertura da biblioteca.
- **IDs:** `ntv_id` nunca muda; `message_id` pode mudar e é atualizado pelo upsert.

## Testes
- pgTAP: `catalog_changes` (diferença, tombstone, limite), `catalog_lookup`, upsert idempotente.
- JVM: parser v3, ordem de resolução do cartão, merge do sync.
- Bot: legenda v3 cabe em 1024, upsert chamado com o mesmo `ntv_id` em repost.
- Aparelho: Fire TV e S23 com catálogo de ~3 mil itens; modo avião depois da primeira carga.
