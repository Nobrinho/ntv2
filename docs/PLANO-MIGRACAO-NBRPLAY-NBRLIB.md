# Plano: migrar filmes e séries do canal NBR Play para o NBRlib, no formato v3

Objetivo: o acervo inteiro do canal **NBR Play** (`-1004483833987`) passa a viver no **NBRlib**
(`-1003819811669`) com a legenda mínima v3 e os metadados no backend. Sem baixar e subir os vídeos de
novo: o Telegram copia no servidor.

Pré-leitura: `docs/PLANO-CATALOGO-BACKEND.md` (contrato da legenda v3 e do `catalog-upsert`).

## Como está hoje
- Filmes: o `postagens_publicadas.json` local tem 2449 filmes (`tmdb:<id>`, `file_id`, `message_ids`), quase todos
  com 2 mensagens (texto + vídeo, formato antigo) e uns 150 com 1. O banco do bot pode ter mais; contar lá.
- Séries: não estão nesse arquivo; vêm da varredura do canal pelo monitor (Telethon) e da legenda de cada episódio.
- Filmes grandes em partes (`.partNNofMM`): postados pelo `nbr-cortador`, cada parte é uma mensagem de documento.
- Catálogo (backend): uma linha por filme (`tmdb_id`), com **um só** `chat_id` e `video_message_id`. Item migrado =
  item cujo `chat_id` e `video_message_id` passam a apontar para o NBRlib.
- `LEGENDA_V3` ainda desligado no bot e no cortador; o NBRlib já recebe posts v3 de teste.

## Como migrar (técnica)
- **`copyMessage` do Bot API**, do NBR Play para o NBRlib, com `caption` trocada pela legenda v3. Copia o arquivo
  no servidor do Telegram (sem download), vale para os 20 GB+ e devolve o novo `message_id`.
  Exige o bot **administrador nos dois canais**.
- Alternativa para quem já tem `file_id` válido no registro: `send_video(file_id=..., caption=v3)`. Mesmo efeito.
- **Filme normal:** copiar só a mensagem do vídeo (a de texto não vai: os dados vão pelo `catalog-upsert`).
- **Filme em partes:** copiar todas as partes, na ordem, cada uma com a mesma legenda v3.
- **Série:** copiar cada episódio com a legenda `ntv:e_<série>_<TT>_<EE> tmdb:<série> SxxEyy` (+ `logo:` da série).
- **Sem TMDB** (`custom`) e série avulsa: seguem com a legenda completa, sem v3 (regra atual do bot).
- Para cada item copiado: gravar o novo `message_id` no registro do bot e mandar o `catalog-upsert` com o novo
  `chat_id` e `video_message_id`. O `ntv_id` não muda.
- Ritmo: respeitar o limite de envio do Telegram (FloodWait) e trabalhar em lotes pequenos com retomada. Estimar o
  tempo total depois de contar filmes, partes e episódios.

## Decisões em aberto (a confirmar)
1. **O NBRlib é o canal definitivo?** Pressuposto deste plano: sim.
2. **O NBR Play fica como arquivo?** Recomendado: sim, sem apagar nada, até a base de usuários estar na versão com
   catálogo. Quem ainda lê o canal direto continua vendo os posts antigos.
3. **Como o usuário passa a ver o NBRlib?** Hoje cada pessoa escolhe os canais no app. Definir se o NBRlib já
   aparece para todos ou se é entregue por configuração remota.

## Etapas (cada uma entra em produção sozinha)
0. **Corrigir o `import-index`** (feito no código do backend: deixou de apagar o que o bot grava direto). Falta
   publicar a função. Sem isso, a cada 6 h somem os filmes migrados que não estão no `index.json`.
1. **Congelar o `import-index` durante a migração.** Ele grava o `chat_id` do `index.channel` e o
   `video_message_id` do índice por cima do que o bot gravou. Enquanto o índice não for regenerado com o NBRlib,
   isso desfaz a migração. Desligar o agendamento (`cron.unschedule('ntv2-import-index')`) antes do primeiro lote
   e só religar, se ainda fizer sentido, depois de o bot ser a fonte de tudo.
2. **Inventário.** Contar no backend o que há por canal:
   `select chat_id, count(*) from catalog_movies group by chat_id;` (e `catalog_episodes`). Contar também as
   partes e os itens sem TMDB. Esse é o tamanho real da migração e vira o relatório "antes".
3. **Pré-requisitos de versão.** App com leitor v3 e catálogo (0.8.0 ou mais nova) na base de usuários; só então
   subir `min_version_code`. App antigo diante de post v3 mostra cartão sem informação.
4. **Script de migração** (`scripts/migrar_para_nbrlib.py` no `agente_filmes`), com:
   - modo `--dry-run` que só lista o que faria e a legenda de cada item;
   - lotes pequenos, retomada por um arquivo de progresso (item concluído nunca repete) e tratamento de FloodWait;
   - um item por vez: `copyMessage` → gravar `message_id` no registro → `catalog-upsert`;
   - filtros `--tipo filme|serie`, `--limite N`, `--tmdb <id>`.
5. **Piloto.** 20 filmes (alguns com 1 mensagem, outros com 2, 1 em partes) e 1 série inteira. Conferir no app o
   cartão completo, tocar o vídeo, e que o item some da lista do NBR Play e aparece na do NBRlib.
6. **Filmes, em lotes** (ex.: 200 por vez), conferindo a cada lote a contagem por `chat_id` no backend.
7. **Séries**, uma série completa por vez (temporada incompleta confunde a tela de detalhe).
8. **Filmes em partes**, por último e um de cada vez: são os mais pesados e os que mais quebram se a ordem das
   partes mudar.
9. **Cortador e bot**: ligar `legenda_v3` no cortador e `LEGENDA_V3=true` no bot (com o `CANAL_DESTINO_ID` do
   NBRlib) para tudo que entrar daqui em diante já sair no formato novo.
10. **Fechamento.** Regenerar o `index.json` com `channel` do NBRlib, religar o `import-index` só como verificação
    (ou deixá-lo desligado), e arquivar o NBR Play. Apagar posts do NBR Play só depois de confirmar que nenhum
    app em uso depende deles.

## Riscos
- **Import sobrescrevendo a migração:** tratado na etapa 1. Conferir sempre `import-index` antes de cada lote.
- **App antigo no NBRlib:** cartão sem informação. Mitigação: etapa 3 e `min_version_code`.
- **Item único por canal:** o catálogo guarda um `chat_id` por filme. Enquanto migra, o filme está só num dos
  canais; um filme não pode aparecer nos dois ao mesmo tempo.
- **Bot sem permissão no NBR Play:** `copyMessage` falha. Testar com 1 item antes (etapa 5).
- **Legenda acima de 1024 caracteres:** a v3 cabe em uma linha; só o item sem TMDB, que não entra na v3, não muda.
- **Mensagens de texto antigas** (a primeira das 2 mensagens do filme) não são copiadas; se o bot de varredura
  ainda procurar pôster/texto adjacente, conferir que o NBRlib não depende disso (o v3 já não pareia).
- **Duplicatas:** o registro do bot é por `tmdb:<id>`; atualizar `message_ids` e `canal` no mesmo passo, senão
  uma nova varredura republica o filme.

## Verificação
- Backend: contagem por `chat_id` antes e depois de cada lote; nenhum `ntv_id` em `catalog_removed` que não
  devia estar lá.
- App (S23 e Fire TV): cartão completo (título, pôster, sinopse, elenco), vídeo toca, série com todas as
  temporadas e episódios, modo avião depois da primeira carga.
- Bot: registro sem `message_id` apontando para o canal antigo nos itens migrados.

## Rollback
- Nada é apagado no NBR Play até o fechamento. Para voltar um item, repetir o `catalog-upsert` com o `chat_id` e o
  `video_message_id` antigos (guardados no arquivo de progresso do script).
- `remote_catalog` em `false` devolve o app ao comportamento anterior.
