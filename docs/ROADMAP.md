# Roadmap do NBR Play

Atualizado em 11/11/2026, na versão **0.10.0**. Legenda: ✅ entregue · 🧪 entregue, falta validar em aparelho real ·
⏳ em aberto.

## Entregue

| Versão | Data | Destaques |
|---|---|---|
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

### 5. Administração de usuários ⏳
Pedida em 18/10/2026, ainda sem escopo. Ideias:
- ver contas e aparelhos;
- Premium de cortesia;
- bloquear abuso;
- tratar reportes (`media_reports`);
- consultar pagamentos.

Lugar provável: `api/` (NestJS) do `ntv2-backend`. Definir o escopo antes de construir.

### 6. Dívidas técnicas e de dados ⏳
- **Cards da grade:** os que vêm do índice (NBRlib) não mostram duração nem qualidade.
- **Episódios:** alguns têm título igual ao nome da série (dado do bot).
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

## Ordem
Combinado em 18/10/2026: validar em aparelhos reais e, depois, Premium e administração de usuários.

Sugerido em 09/11/2026: encaixar diretor e notas logo após a validação, porque o app já está pronto para eles.
Segurança e custo vêm antes do Premium, porque são pré-requisito dele.

11/11/2026: item 3 revisto contra o `PLANO-SEGURANCA-CUSTO.md`; Fases 0 e 1 essencialmente feitas, restam os
itens listados nele.

Itens novos entram aqui com data.
