# Ícones do NBR Play

Os ícones do app são próprios, os mesmos do protótipo (`nbrplay.pen`). A biblioteca
`material-icons-extended` foi removida; não use `Icons.Filled.*`.

- Desenho: vector drawables em `app/src/main/res/drawable/ic_nbr_*.xml` (fonte da verdade; servem também
  para XML e notificações).
- Código: `com.ntv2.app.core.ui.icons.NbrIcons` (ImageVector gerado dos drawables), com o mesmo tipo que
  o Material usava.

## Regras
- Traço de 2 em grade de 24, pontas e junções arredondadas. Tamanho padrão 24 dp; em linhas e botões, 20 dp
  (como no protótipo); na TV, metade do valor do protótipo em dp.
- A cor vem do `tint` (o desenho é branco). Uso em Compose:
  `Icon(NbrIcons.Heart, contentDescription = "Favoritar", tint = BrandColors.Accent)`.
  Em XML: `@drawable/ic_nbr_heart`.
- Estado ativo usa a versão `Filled` quando existe (coração, play, pausa, estrela, avançar e voltar).

## Gerar ou acrescentar ícones
Base de desenho: Lucide `lucide-static@1.57.0` (licença ISC, permite uso comercial).

1. Baixe o pacote numa pasta temporária: `npm pack lucide-static@1.57.0` e extraia.
2. Acrescente o nome em `tools/icones/nomes.txt` (`nome` ou `nome:filled`).
3. `python -I tools/icones/svg2vd.py <pacote>/icons app/src/main/res/drawable tools/icones/nomes.txt`
4. `python -I tools/icones/vd2kt.py app/src/main/res/drawable app/src/main/java/com/ntv2/app/core/ui/icons/NbrIcons.kt`
   (o nome no código vira PascalCase: `share-2` → `NbrIcons.Share2`, `heart:filled` → `NbrIcons.HeartFilled`).

O conversor reescreve os paths com separadores explícitos (o parser do Android erra flags de arco
compactadas) e transforma círculos, retângulos, linhas e polígonos em `pathData`.

## Troca do Material pelo ícone próprio

| Material (`Icons.Filled.*`) | NBR (drawable; no código, `NbrIcons` em PascalCase) | Onde aparece |
|---|---|---|
| Home | ic_nbr_house | NavRail |
| Search | ic_nbr_search | NavRail, MainBottomNav |
| FilterAltOff | ic_nbr_funnel_x | NavRail |
| Subscriptions | ic_nbr_library | NavRail, MainBottomNav (Canais) |
| VideoLibrary | ic_nbr_clapperboard | NavRail, Onboarding |
| Movie | ic_nbr_clapperboard | MainBottomNav (Biblioteca), MediaGrid |
| Favorite | ic_nbr_heart_filled | MainBottomNav, detalhes |
| FavoriteBorder | ic_nbr_heart | detalhes |
| Settings | ic_nbr_settings | NavRail, MainBottomNav, player |
| Refresh | ic_nbr_refresh_cw | canais, testes, Premium, player |
| ExitToApp, AutoMirrored.Logout | ic_nbr_log_out | sair da conta |
| Close | ic_nbr_x | diálogos e overlays |
| SystemUpdate | ic_nbr_download | atualização |
| CloudDownload | ic_nbr_database | Índice de filmes e séries |
| History | ic_nbr_history | Onboarding |
| Lock | ic_nbr_lock | Onboarding |
| AutoMirrored.ArrowForward | ic_nbr_arrow_right | Onboarding, canais |
| AutoMirrored.ArrowBack | ic_nbr_chevron_left | voltar (o protótipo usa chevron) |
| Info | ic_nbr_info | login, reporte |
| Check | ic_nbr_check | listas |
| CheckCircle | ic_nbr_circle_check | seleção, status ok |
| Cancel | ic_nbr_circle_x | teste de conexão (falha) |
| ErrorOutline | ic_nbr_circle_alert | reporte |
| Warning | ic_nbr_triangle_alert | erros |
| KeyboardArrowDown | ic_nbr_chevron_down | expandir |
| KeyboardArrowRight, ChevronRight | ic_nbr_chevron_right | itens de lista |
| Deselect | ic_nbr_square_dashed | canais |
| FilterList | ic_nbr_sliders_horizontal | busca |
| DeleteSweep | ic_nbr_trash_2 | Minha lista, histórico |
| Remove | ic_nbr_minus | Minha lista, histórico |
| PlayArrow | ic_nbr_play_filled | player, cards |
| Pause | ic_nbr_pause_filled | player |
| Replay | ic_nbr_rotate_ccw | recomeçar |
| FastRewind / FastForward | ic_nbr_rewind_filled / ic_nbr_fast_forward_filled | toque duplo no player |
| Flag | ic_nbr_flag | reportar |
| Star | ic_nbr_star_filled | nota |
| Image | ic_nbr_image | Configurações (Capas) |
| AutoAwesome | ic_nbr_sparkles | Configurações (Animações) |
| BlurOn | ic_nbr_sun | Configurações (Iluminação da capa) |
| Group | ic_nbr_users | Configurações (Fotos do elenco) |
| Animation | ic_nbr_square_dashed | Configurações (Animação dos cards) |
| Tv | ic_nbr_tv | Configurações (Modo de tela) |
| WorkspacePremium | ic_nbr_crown | Premium |
| Devices | ic_nbr_link | Conectar aparelhos |
| NetworkCheck | ic_nbr_activity | Teste de conexão |
| Shield | ic_nbr_shield_check | Política de privacidade |
| School | ic_nbr_rotate_ccw | Ver introdução novamente |
| Timer | ic_nbr_timer | Duração mínima |
| SwapHoriz | ic_nbr_arrow_left_right | reporte (áudio trocado) |
| Mic | ic_nbr_mic | reporte |
| VolumeOff | ic_nbr_volume_x | reporte |
| ClosedCaption | ic_nbr_captions | reporte, player |
| HighQuality | ic_nbr_image_off | reporte (qualidade ruim) |
| MoreHoriz | ic_nbr_ellipsis | reporte (outro) |
| QrCode2 | ic_nbr_qr_code | Premium, aparelhos |
| ContentCopy | ic_nbr_copy | Pix |
| Wifi | ic_nbr_wifi | teste de conexão |
| Cloud | ic_nbr_cloud | teste de conexão |
| Send | ic_nbr_send | teste de conexão (Telegram) |
| Speed | ic_nbr_gauge | teste de conexão, velocidade |
| Keyboard | ic_nbr_keyboard | aparelhos (digitar código) |
| LinkOff | ic_nbr_unlink | aparelhos |
| Cast, CastConnected | ic_nbr_cast | player |
| Public | ic_nbr_globe | player (rede) |
| AutoMirrored.VolumeUp | ic_nbr_volume_2 | gesto de volume |
| Brightness6 | ic_nbr_sun | gesto de brilho |
| PictureInPictureAlt | ic_nbr_picture_in_picture_2 | player |
| Audiotrack | ic_nbr_audio_lines | player |
| Fullscreen | ic_nbr_maximize | player |

Ícones do protótipo ainda sem uso no app (entram com as telas novas): bell, bell_ring, calendar,
check_check, circle, circle_dot, credit_card, eye, hourglass, layers, loader, message_square_plus,
message_square_warning, monitor, monitor_x, pencil, plus, receipt, rotate_cw, search_x, share_2, shield,
skip_forward(_filled), smartphone, tablet, thumbs_up, wifi_off.

## Remoção da biblioteca (feita)
Todos os usos foram trocados pela tabela acima (`NbrIcons.<Nome>`), e a dependência saiu de
`app/build.gradle.kts` e `gradle/libs.versions.toml`. Para conferir que nada voltou:
`grep -rn "material.icons" app/src` deve vir vazio.
