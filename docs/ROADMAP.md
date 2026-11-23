# Roadmap do NBR Play

Atualizado em 23/11/2026, na versão **0.10.3** (mais as correções de 23/11 ainda sem release). Legenda: ✅ entregue · 🧪 entregue, falta validar em aparelho real ·
⏳ em aberto.

## Entregue

| Versão | Data | Destaques |
|---|---|---|
| _sem release_ | 23/11/2026 | Correções do raio-x conferido no Context7: cliente OkHttp único (capas, busca e Supabase com as CAs do Fire TV e DNS IPv4), Cast pela API assíncrona, contrato do `transferEnded` nos DataSources, Flows com lifecycle em todas as telas. Dependências: sai `tv-foundation` (puxava o Compose 1.10 escondido) e `turbine`; Compose BOM 2026.02.00 (Material3 1.4.0) e `tv-material` 1.0.1. `CLAUDE.md` com a regra do Context7. Conferido no emulador de TV |
| 0.10.3 | 22/11/2026 | Teste do Atualizar sem `Thread.sleep` (índice de busca com dispatcher injetável) |
| 0.10.2 | 22/11/2026 | Filmes e séries próprios do NBR Studio (sem TMDB), identificados pelo `ntv_id` |
| 0.10.1 | 17/11/2026 | Correções da revisão de 16/11 ([REVISAO-2026-11-16.md](REVISAO-2026-11-16.md)): punch hole no armeabi-v7a, sessão do backend só trocada com `USER_GONE`, APK de atualização só com a chave oficial, sessão fora do backup, progresso real dos episódios e do Cast, sync pela hora do servidor (`synced_at`) com "Sincronizar agora", diretor e notas preservados no catálogo local, estabilidade do TDLib e do player, desempenho da busca e das séries, testes no CI a cada push. Atualizar também sincroniza o catálogo |
| 0.10.0 | 11/11/2026 | D-pad na tela de séries com trailer contínuo e foco preservado. Menu lateral e seletor Filmes/Séries centralizados. Recomendações sempre visíveis nos detalhes, logo na espera do player. Tipo de aparelho explícito e foco correto em Perfis. Tablet deitado respeita a barra de tarefas. Sem anel de foco no uso por toque, "Carregar mais" inteiro na grade. Menos trabalho por quadro na biblioteca |
| 0.9.2 | 09/11/2026 | Trailer na TV: prévia em tela cheia no lugar com som, legenda só na tela cheia, sem tela final do YouTube. Filtros por diretor e notas (IMDb/RT), ano com autocompletar. Episódio abre detalhes antes de tocar. Logo da série. Grade do NBRlib pelo índice. Atualizar e "Carregar mais" sem travar. Um único anel de foco entre telas. O foco volta à origem (busca, trilhas, grade) com o card inteiro na tela. "Continuar assistindo" abre detalhes completos. Fim do filme volta aos detalhes. Logo de entrada menor na TV |
| 0.9.1 | 05/11/2026 | Cache de 5 min das páginas do `search_catalog`; cores de marca centralizadas no `BrandColors` |
| 0.9.0 | 04/11/2026 | Prévia de trailer no fundo dos detalhes e botão Trailer (backend e bot publicados, backfill de 2.583 filmes) |
| 0.6–0.8 | out–nov/2026 | Catálogo no backend (Supabase), migração do acervo NBR Play → NBRlib, séries e episódios no catálogo, rebranding (kit v2.1, Inter, ícones próprios) |
| 0.5.x | 18/10/2026 | Backend em produção, Premium por Pix preparado e desligado (`app_config.premium.enabled=false`), perfis |
| 0.4.x | set–out/2026 | Filmes em partes, Dolby Vision 7 → HEVC, decoder por janelas no Fire TV, saída 4K, atualização automática, personalização (continuar, Minha lista, histórico, recomendações) |
| Fundação | set/2026 | Reprodução progressiva TDLib (MP4/MKV), janela no disco com liberação atrás do playhead, orçamento de armazenamento, R8, banco do TDLib cifrado, testes JVM |

## Em aberto, por prioridade

### 1. Validar em aparelho real 🧪
O emulador não cobre decoder real, rede lenta nem memória do Fire TV. Validar no Fire TV (AFTKM/AFTMM) e no
celular (S23):
- Trailer:
  - prévia e tela cheia, foco do controle e legenda;
  - memória depois de várias aberturas;
  - Assistir logo após o trailer começar.
- Navegação da TV:
  - anel de foco em rajadas;
  - volta da busca e das trilhas.
- Puxar para atualizar no celular.
- Filmes em partes: teste de ~20 GB no Fire TV (etapa 7).
- Dolby Vision 7 no Fire TV (validado só no S23).
- Correções de 23/11: Cast pela API assíncrona com Chromecast de verdade no S23; aparência com o Material3
  1.4.0; capas, busca e Supabase no Fire TV antigo (raízes Amazon/Starfield agora valem para os três).
- Filmes e séries próprios do NBR Studio: série com episódios agrupados e reprodução.
- Correções da revisão de 16/11 que dependem de aparelho: filme acima de 4 GB no Fire TV (punch hole),
  progresso do Cast e foco na TV.

### 2. Dados de diretor e notas ⏳
O app já lê e filtra `director`, `imdb_rating` e `rt_score`. O filtro e os chips não aparecem enquanto o catálogo
não tiver esses dados. Falta:
- **Backend / bot:** gravar o diretor (TMDB, `crew` com `job = Director`) e as notas IMDb e Rotten Tomatoes pelo
  OMDb no `catalog-upsert` e no `index.json`, com backfill dos títulos antigos.
- **Custo:** o OMDb é pago acima do limite gratuito. Precisa de cache e de um limite de gasto antes de ligar (ver
  `ntv2-backend/PLANO-SEGURANCA-CUSTO.md`).
- **Painel de busca:** hoje só se chega ao filtro de diretor pelo chip dos detalhes. Avaliar uma lista de
  diretores no painel.

### 3. Segurança e custo do backend ⏳
Detalhes em `ntv2-backend/PLANO-SEGURANCA-CUSTO.md`. Já feito: plano Free com Spend Cap ligado (conferido em
05/11/2026), GraphQL/Realtime/Storage desligados, `statement_timeout`, `max_rows` 500, rate limit no
`db-pre-request`, alerta de saúde e de abuso no Telegram, limpezas no `pg_cron`. Play Integrity descartado (APK
fora da loja e Fire TV). Falta:
- restringir a rede do banco ao IP do admin (hoje `0.0.0.0/0`);
- documentar a rotação das chaves;
- CAPTCHA (Turnstile) no cadastro anônimo do celular;
- ao ligar o Premium: migrar para o Pro no mesmo dia (backup diário + PITR) com Spend Cap;
- mais adiante: Upstash nas Edge Functions quando houver volume, staging separado da produção, vincular conta
  (e-mail/Google).

Pré-requisito do Premium e de qualquer serviço pago novo.

### 4. Premium por Pix ⏳
Backend pronto e desligado. Falta:
- credenciais do Mercado Pago (`MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`);
- ligar o interruptor.

Qualidade (4K/HDR/DV) e áudio não podem ser cobrados.

### 5. Administração, perfis e denúncias 🧪
Escopo fechado e implementado em 23/11/2026 (app + `ntv2-backend` + painel `site/admin`), **ainda sem deploy**:
- **Perfis reais:** `account_profiles` sincronizado, lista/histórico/progresso por perfil (Room v6), PIN só como
  hash, Kids filtrando por classificação ou, sem ela, por gênero (Animação/Família).
- **Painel:** conta com perfis, itens por perfil, motivo do bloqueio, denúncias da conta e "Apagar dados" (LGPD);
  triagem de denúncias por título com resposta ao usuário, ocultar/mostrar título e pedido de repostagem.
- **App:** Configurações › Minha conta (identificador, aparelhos, Premium, minhas denúncias com a resposta,
  apagar meus dados) e aviso de aparelho bloqueado.
- **Alertas:** 3 denúncias abertas num título avisam na hora no Telegram; resumo das novas a cada hora.

Falta: deploy na ordem do `OPERACAO.md` (release 0.11.0 → `db push` das 4 migrations → `min_version_code`),
validar no Fire TV e no S23, o bot ler `private.repost_requests` e gravar a classificação indicativa
(`certification` do TMDB-BR) para o Kids deixar de depender do gênero.

### 6. Dívidas técnicas e de dados ⏳
- **Cards da grade:** os que vêm do índice (NBRlib) não mostram duração nem qualidade.
- **Episódios:** alguns têm título igual ao nome da série (dado do bot).
- **Media3 1.5.1 → 1.11.x:** tarefa separada. Exige atualizar junto as fontes do `:ffmpeg-decoder` (tag do
  Media3) e testar de novo decoder e janelas no Fire TV.
- **APIs obsoletas** que o build aponta: `LocalLifecycleOwner` (mudou de pacote), `FocusRequester.enter/exit`
  (usar `onEnter/onExit`), `LocalClipboardManager` (usar `LocalClipboard`), `TRIM_MEMORY_RUNNING_LOW`.
- **Fase 5 da revisão de 16/11 (manutenção):** dividir o `MediaLibraryViewModel` (~2.000 linhas: paginação,
  busca, personalização, início de reprodução), overlay selado na `MediaLibraryScreen` e lint no CI.
- **HTTP:** atualizador e teste de conexão ainda em `HttpURLConnection`, fora do cliente único.
- **Cores:** ~56 cores literais `0xFF…` fora do `BrandColors` (principalmente avatares).
- **Cron `ntv2-import-index`:** pausado desde a migração. Decidir se volta ou se sai.
- **TDLib:** abort `PartsManager` mitigado com limite de faixa no download; causa não confirmada.
- **Foco por toque:** 🧪 o anel que aparecia sobre a busca aberta por toque foi tirado na 0.10.0 (sem anel no
  uso por toque). Conferir no celular.
- **Trailer de Portugal:** trailers com legenda gravada em português de Portugal. Escolher outro vídeo no bot quando
  houver versão pt-BR.

### 7. Produto, mais adiante ⏳
- Monetização: a aposta recomendada é B2B/white-label da tecnologia (player progressivo, TV e decoder) mais
  assinatura. O app continua só agregador.
- NBR Studio (repo `nbr-studio`): bot, cortador e captador num app só; falta teste real no Telegram.

### 8. Novas plataformas ⏳
Pedido em 23/11/2026, ainda sem escopo. O app atual é só Android (celular, Android TV e Fire TV); cada
plataforma abaixo é um app novo, não uma recompilação. Pontos a resolver antes de começar:
- **iOS (iPhone, iPad, Apple TV):**
  - o TDLib compila para iOS;
  - a interface teria de ser refeita (SwiftUI) ou migrada para Compose Multiplatform;
  - o AVPlayer não toca MKV nem AC3/DTS/TrueHD, então o player precisaria de FFmpeg ou VLCKit;
  - risco de recusa na App Store para um app que reproduz conteúdo de canais do Telegram;
  - distribuição fora da loja é restrita.
- **Samsung (Tizen) e LG (webOS):**
  - são apps web (HTML/JS) com player nativo da TV (AVPlay no Tizen);
  - o Telegram teria de vir do TDLib em WebAssembly (`tdweb`) ou de um servidor intermediário;
  - o download progressivo com janela no disco não tem equivalente direto;
  - publicar exige conta de desenvolvedor e aprovação na loja de cada fabricante.
- **PS5:**
  - não há SDK aberto para apps de terceiros;
  - apps de vídeo entram só por parceria com a Sony;
  - na prática, depende de um acordo comercial, e não de trabalho técnico.

Ordem sugerida, se avançar: Samsung e LG primeiro (base de TVs grande no Brasil, um mesmo app web serve às
duas), iOS depois e PS5 só com parceria.

## Ordem
Combinado em 18/10/2026: validar em aparelhos reais e, depois, Premium e administração de usuários.

Sugerido em 09/11/2026: encaixar diretor e notas logo após a validação, porque o app já está pronto para eles.
Segurança e custo vêm antes do Premium, porque são pré-requisito dele.

11/11/2026: item 3 revisto contra o `PLANO-SEGURANCA-CUSTO.md`; Fases 0 e 1 essencialmente feitas, restam os
itens listados nele.

16/11/2026: revisão do código ([REVISAO-2026-11-16.md](REVISAO-2026-11-16.md)); fases 1 a 4 corrigidas nos commits de 16/11 (0.10.1);
a fase 5 segue em aberto no item 6.

23/11/2026: raio-x das bibliotecas conferido no Context7; correções feitas e conferidas no emulador. A subida do
Media3 ficou de fora, como tarefa própria.

23/11/2026: novas plataformas (iOS, Samsung, LG e PS5) entram como item 8, ainda sem escopo e sem data.

23/11/2026: item 5 (administração, perfis e denúncias) implementado nas 4 fases do plano; falta o deploy.

Itens novos entram aqui com data.
