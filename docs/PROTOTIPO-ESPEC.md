# Especificação do protótipo NBR Play (pen.dev → Compose)

Fonte: `nbrplay.pen` (lido pelo MCP pencil). Fonte tipográfica: Inter em todo o protótipo.

**Conversão**
- Celular (390×844; player 844×390): 1 px = 1 dp; texto em sp.
- TV (1920×1080): divida tudo por 2 (Fire TV = 960×540 dp). Os valores TV abaixo **já estão divididos**.
- Cores pelo token (`BrandColors`): bg=Background, surface=Surface, surface-alt=SurfaceAlt, accent=Accent,
  on-accent=OnCta, text=branco #F5F5F7, text-2=TextSecondary, muted=TextMuted, border=Border (#FFFFFF33),
  border-subtle=BorderSubtle (protótipo #FFFFFF1F), fill=Fill, success/warning/error/violet/cyan/rose.
  Hex com alfa fora dos tokens aparecem como `accent@12%` etc. (ex.: `#66D9FF1F` = accent@12%,
  `#66D9FF22` = accent@13%, `#66D9FF26` = accent@15%, `#FFFFFF1A` = branco@10%).
- Altura de linha: quando não indicada, é a padrão do Inter (~1,2×). `lh1.5` = 1,5 × tamanho.
- Ícones: `NbrIcons.<PascalCase>` (ver `docs/ICONES.md`); "ativo" usa a versão `Filled` quando existir.

---

## 1. Escala de texto

### Celular (sp)
| Tam/peso | Uso |
|---|---|
| 34 / 800, lh 1.05 | Título do hero (detalhe filme/série) |
| 28 / 700 | Título de tela (Biblioteca, Configurações) |
| 20 / 700 | Título de seção grande (Episódios), título de sheet |
| 18 / 700 | Cabeçalho de fileira/seção (Continuar assistindo, Elenco) |
| 17 / 700 | Título no player |
| 16 / 700 | Botão primário (Rotulo), título de opção de sheet (Temporada N), título de grupo no painel do player |
| 16 / 600 | Botão secundário; hora da StatusBar |
| 15 / 500 | Item de ação de sheet (opções do episódio) |
| 14 / 600 | Título de card (Continuar), título de item de configuração, coleção |
| 14 / 400, lh 1.5 | Sinopse (text-2) |
| 13 / 700 | Título de episódio; opção selecionada do painel do player (accent) |
| 13 / 600 | Chip seletor (canal, temporada), valor de item de config, título de card de pôster, aviso "Reprodução automática" |
| 13 / 500 | Chip (Rotulo); opção não selecionada do player |
| 13 / 400 | Meta do hero ("2026 · Terror · 1h33min", text-2) |
| 12 / 600 | Link ("OK para ler tudo", "Marcar temporada…", accent); tempo atual e rótulos de botão no player |
| 12 / 400 | "faltam 10min" (accent), "Faltam 22min…" (accent, centro), duração total no player |
| 11 / 700, ls 1 | Rótulo de grupo em maiúsculas (CONTA E APP, muted) |
| 11 / 400, lh 1.35 | Subtítulo de item de config e de episódio (muted) |
| 11 / 400 | Nome de ator (text-2), meta pequena, rodapé de versão |
| 10 / 600 (ativo) · 10 / 500 | Rótulo da tab bar |
| 10 / 800 · 9 / 800 | Selo "NOVO" (branco sobre violet) |
| 10 / 700 | Selo de qualidade no pôster (FHD) |

### TV (sp, já ÷2)
| Tam/peso | Uso |
|---|---|
| 40 / 800 | Título do detalhe do filme |
| 36 / 800, lh 1.05 | Título do detalhe da série |
| 32 / 800 | Título do destaque da Biblioteca; título de tela (Configurações) |
| 20 / 700 | Título no player |
| 16 / 700 | Cabeçalho de fileira, "Elenco" |
| 14 / 700 | Texto de botão (Assistir, Minha lista) |
| 14 / 400, lh 1.5 | Sinopse do filme · 13/400 lh1.5 sinopse da série |
| 13 / 700 | Título de item de config, título de episódio |
| 13 / 400 | Meta (ano · gênero), nome do ator |
| 12 / 700 | "Reprodução automática"; valor à direita em config (accent/success/text-2) |
| 12 / 600 | Tempo e rótulos de botão do player |
| 12 / 400 | Meta da Biblioteca, sinopse do destaque, "Voltar", duração total |
| 11 / 700 | Rótulo de grupo (CONTA E APP, muted) |
| 11 / 600 | Título de card, chip, "OK para ler tudo" |
| 11 / 400 | "faltam…" (accent), meta do player, "Faltam 22min…" |
| 10 / 700 | "Marcar como assistida" (violet) |
| 10 / 400 | Subtítulo de item de config/episódio (muted), "2 de 10 assistidos" |
| 9 / 800 | Selo NOVO |

Regra da TV no protótipo: texto mínimo 22 px = **11 sp**; área segura de 5% nas bordas.

---

## 2. Componentes repetidos

### 2.1 StatusBar (celular)
Só referência visual: 390×62, padding 0/28. No app é a barra do sistema (edge-to-edge); reserve o inset.

### 2.2 Botão primário (`Botao/Primario`)
| Prop | Celular | TV |
|---|---|---|
| Altura | 52 (largura 342 = fill ou conteúdo) | 36 |
| Raio | 26 (pílula) | 18 |
| Fundo / conteúdo | accent / on-accent | accent / on-accent |
| Texto | 16/700 | 14/700 |
| Ícone | o protótipo usa "▶  " no texto → use `NbrIcons.PlayFilled` 20 + gap 8 | `PlayFilled` 15, gap 6 |
| Foco (TV) | — | anel focado (2.9) sempre presente no botão em foco |

### 2.3 Botão secundário (`Botao/Secundario`)
| Prop | Celular | TV |
|---|---|---|
| Altura / raio | 52 / 26 | 36 / 18 |
| Fundo | fill | fill |
| Contorno | 1 border | 1 border (protótipo 2 px ÷2) |
| Texto | 16/600 text | 14/700 text, ícone 15 + gap 6 |

### 2.4 Botão circular de ícone (detalhe, celular)
48×48, raio 24, fundo branco@10%, contorno 1 border, ícone 20 text. Variação "ativo" do coração: fundo rose@13% (`#FF6FAE22`), ícone rose (`HeartFilled`).

### 2.5 Chip
| Prop | Celular | TV |
|---|---|---|
| Altura / raio | 32 / 16 | 26 / 13 |
| Padding horizontal | 14 | 12 |
| Repouso | fundo fill, contorno 1 border-subtle, texto 13/500 text-2 | fill, contorno border-subtle, texto 11/600 text-2 |
| Selecionado | fundo accent, texto on-accent (contorno some) | idem |
| Gap entre chips | 8 | 6 |

Chip seletor (canal atual / temporada, celular): altura 36, raio 18, padding 0/12, gap 6, fundo fill, contorno border-subtle (canal) ou border (temporada), texto 13/600 text, `ChevronDown` 14 text-2.

### 2.6 Card de pôster (`PosterCard`, celular)
Largura 112 (ou peso igual na grade, ~107), vertical gap 8.
- Capa: 112×168 (2:3), raio 12, contorno 1 border-subtle, fallback gradiente vertical `#2A3A5C→#0F1320`, padding 8, conteúdo no fundo.
- Selo: fundo `#000000AA`, raio 6, padding 2/6, texto 10/700 text.
- Título: 13/600 text, largura do card (até 2 linhas).
TV (fileira Recomendados): 100×150, raio 10, título 11/600, gap 6, espaço entre cards 12.

### 2.7 Card "Continuar assistindo"
| Prop | Celular | TV |
|---|---|---|
| Imagem | 240×135 (16:9), raio 12 | 180×101.5, raio 10 |
| Escurecimento | gradiente vertical transparente (50%) → `#000000B3` | idem |
| Trilho | altura 4, branco@20%, encostado no fundo, sem raio | altura 4 |
| Progresso | accent | accent |
| Título | 14/600 text | 11/600 text |
| "faltam…" | 12/400 accent | 10/400 accent |
| Gaps | 8 interno, 12 entre cards | 6 interno, 12 entre cards |

### 2.8 Tab bar flutuante (celular)
- Container: padding 0/16/12/16 (horizontal 16, fundo 12) acima do inset de navegação.
- Barra: altura 60, raio 30, padding 6, fundo surface@93% (`#151A22EE`), contorno 1 border-subtle,
  sombra `#00000066` y=6 blur 20.
- 5 itens de peso igual, altura 48, raio 24, vertical gap 2, centrados.
  - Ativo: fundo accent@13% (`#66D9FF22`), ícone 20 accent (versão Filled se houver), texto 10/600 accent.
  - Repouso: transparente, ícone 20 muted, texto 10/500 muted.
- Itens: Biblioteca `Clapperboard`, Busca `Search`, Meu `Heart`, Canais `Library`, Config `Settings`.
- Troca de aba: a cápsula desliza 160 ms.

### 2.9 Anel de foco (TV)
Regra do protótipo: anel accent 4 px + brilho 24 px; ampliação 4% nos cards em 160 ms.
Em dp: **contorno 2 dp accent + sombra/brilho accent@40% (`#66D9FF66`) blur 12 dp, sem deslocamento**;
`scale 1.04` em 160 ms nos cards. Raio do anel = raio do elemento. Aplicado a cards, botões, itens de config,
episódio e à barra de progresso do player. Foco inicial = ação principal; foco restaurado ao voltar.

### 2.10 Menu lateral da TV
- Largura 64, altura cheia, fundo surface, contorno direito 1 border-subtle, vertical gap 14,
  padding vertical 24, itens centralizados.
- Logo NBR Play 36×28, depois espaço 10.
- Itens 40×40, raio 12: ativo fundo accent@15% (`#66D9FF26`) e ícone 18 accent; repouso transparente, ícone 18 muted.
- Itens: Início `House`, Busca `Search`, Minha lista `Heart`, Canais `Library`, Config `Settings`.

### 2.11 Item de configuração (celular)
- Grupo: fundo surface, raio 16, contorno 1 border-subtle; itens empilhados sem gap; divisória de 1 dp
  border-subtle **no topo** de cada item a partir do 2º.
- Item: padding 12/14, gap 12, alinhado ao centro; altura resulta 58 (1 linha) ou 73 (2 linhas).
  - Ícone em caixa 34×34, raio 10, fundo accent@12% (`#66D9FF1F`), ícone 18 accent.
    Variações: Premium/Admin violet@12% + ícone violet; Sair error@12% + ícone error.
  - Texto (peso 1, vertical gap 2): título 14/600 text (Sair: error); subtítulo 11/400 lh1.35 muted.
  - Direita, um destes: `ChevronRight` 16 muted · Valor (texto 13/600 + chevron 16, gap 4;
    cores: success "Ativo", text-2 "Off", accent versão) · Chave.
- Chave (switch): 44×26, raio 13, padding 3; ligada fundo accent, bolinha 20 branca à direita;
  desligada fundo fill + contorno 1 border, bolinha 20 muted à esquerda.
- Rótulo de grupo: 11/700 ls1 muted, maiúsculas.
- Botão "Mais ajustes": altura 44, raio 22, fundo fill, contorno border-subtle, gap 8, texto 13/600 text-2 + `ChevronDown` 16 text-2.

### 2.12 Item de configuração (TV)
Cartão individual (não agrupado): fundo surface, raio 10, contorno 0,5 border-subtle (use 1 dp),
padding 10/12, gap 10, altura ~50. Ícone 18 accent **sem caixa** (Premium violet, Sair error).
Título 13/700 text, subtítulo 10/400 muted, gap 2. Direita: chave 36×20 raio 10 padding 2, bolinha 16
(ligada accent/branca; desligada fill+contorno border, bolinha muted), ou valor 12/700 (accent/success/text-2;
"◀  10 min  ▶" para ajuste com ◀▶). Em foco: anel 2.9. Gap entre cartões 7.

### 2.13 Sheet (celular)
- Escurecimento: preto@70% (`#000000B3`) na tela toda.
- Painel: ancorado embaixo, largura cheia, raio 28 só em cima, fundo surface, contorno 1 border-subtle,
  padding 10/24/28/24, vertical gap 14.
- Handle: barra 40×4, raio 2, cor border, centralizada.
- Título: 20/700 text.
- Animação: slide-up 220 ms + escurecimento 0→70%; arrastar o handle fecha.
- TV: sheets viram painel lateral ou diálogo central, com foco preso dentro.

### 2.14 Cartão de episódio
| Prop | Celular | TV |
|---|---|---|
| Container | padding 10, gap 12, raio 14, fundo surface, contorno 1 border-subtle | padding 7, gap 12, raio 10, altura 77 |
| Atual (em andamento) | fundo accent@7% (`#66D9FF12`), contorno 2 accent | fundo accent@8%, anel de foco 2.9 |
| Miniatura | 112×68, raio 10, escurecimento igual ao 2.7; trilho 4 + barra accent se em andamento | 112×63, raio 7, trilho 4 |
| Título | 13/700 text, "E1 · Nome" (até 2 linhas) | 13/700 text |
| Subtítulo | 11/400 lh1.35 muted, "58min · sinopse" | 10/400 muted |
| Texto gap | 3 | 3 |
| Estado à direita | assistido `CircleCheck` 20 success · atual `PlayFilled` 20 accent · nenhum | 20 dp |
| Lista gap | 10 | 7 |

### 2.15 Ator (elenco)
Celular: coluna 64 de largura, gap 6, foto 56 círculo, nome 11/400 text-2; fileira gap 14.
TV: linha foto 40 círculo + "Nome · Papel" 13/400 text-2, gap 10; lista gap 10.

### 2.16 Hero do detalhe (celular)
- Altura 330 (filme) / 300 (série); imagem com gradiente vertical bg@33% → bg@13% (40%) → bg@93%.
- Topo: inset da status bar + linha de ações de 56 (padding 4/16): `ChevronLeft` à esquerda; à direita
  `Share2` (filme) ou `Heart` + `Share2` (série), gap 10; botões 2.4.
- Bloco do título no fundo: padding 40/24/20/24, gradiente transparente → bg, gap 6:
  título 34/800 lh1.05 text; meta 13/400 text-2.

---

## 3. Telas do celular

### 10 Biblioteca (`f9zGD`)
Fundo bg. Conteúdo rolável: padding horizontal 24, gap vertical 18 entre seções. Tab bar 2.8 por cima.
| Seção | Especificação |
|---|---|
| Topo | linha space-between, altura 36: "Biblioteca" 28/700; chip seletor de canal (2.5) "NBRlib" |
| Filtros rápidos | Chips 2.5, gap 8: Todos (selecionado), Filmes, Séries, Lançamentos |
| Cabeçalho | "Continuar assistindo" 18/700 text (altura 22) |
| Fileira | cards 2.7, gap 12, rolagem horizontal (sangra até a borda) |
| Cabeçalho | "Recomendados para você" 18/700 |
| Fileira | PosterCard 2.6 112 de largura, gap 12 |
| Cabeçalho | "Todos os títulos" 18/700 |
| Grade | 3 colunas de peso igual, gap horizontal 10, entre linhas 14, PosterCard |

### 14 Detalhe do filme (`kBsXA`)
Hero 2.16 (330, ação `Share2`). Conteúdo: padding 16/24/24/24, gap 18.
| Seção | Especificação |
|---|---|
| Meta | Chips 2.5 gap 8: FHD, Dublado, 5.1 |
| Ações | linha gap 10, centro: Botão primário 168×52 "Assistir" com `PlayFilled`; botões 2.4 `Download`, `Heart`, `Flag` |
| Sinopse | 14/400 lh1.5 text-2, 4 linhas; abaixo "OK para ler tudo" 12/600 accent |
| Coleção | linha padding 10/12, gap 12, raio 14, surface, contorno border-subtle: caixa 34 raio 10 accent@12% com `Layers` 18 accent; título 14/600 text + "7 filmes · você viu 3" 11/400 muted (gap 2); `ChevronRight` 16 muted |
| Elenco | "Elenco" 18/700; fileira 2.15 |

### 15 Filme em andamento (`w5kVtl`)
Igual à 14, exceto:
- Ações: coluna "Continuar" com peso 1 (gap 6): botão primário "Continuar • 1h27" (`PlayFilled`) + barra de progresso 4, raio 2, trilho fill, barra accent. Depois botões 2.4 `RotateCcw` (recomeçar), `HeartFilled` ativo (rose), `Flag`.
- Sem o bloco Coleção.

### 16 Série — detalhe (`FHW0U`, 390×1480)
Hero 2.16 (300, ações `Heart` + `Share2`). Conteúdo padding 14/24/24/24, gap 18.
| Seção | Especificação |
|---|---|
| Meta | Chips: "3 temporadas", "28 episódios", "FHD" |
| CTA | coluna gap 8: Botão primário largura cheia "Continuar T1 · E3"; "Faltam 22min · Episódio 3 de 10" 12/400 accent centralizado |
| Sinopse | 14/400 lh1.5 text-2, 3 linhas |
| Reprodução automática | linha padding 12, gap 12, raio 14, fundo violet@10% (`#9B7CFF1A`), contorno 1 violet: `SkipForward` 18 violet; "Reprodução automática" 13/600 text + "Próximo episódio começa em 5s ao terminar" 11/400 text-2 (gap 2); selo NOVO (padding 2/8, raio 8, violet, 10/800 branco) |
| Temporadas | coluna gap 12: linha space-between (altura 36) "Episódios" 20/700 + chip seletor "Temporada 1" (contorno border) → abre 17; linha gap 10: trilho 4 raio 2 fill com barra success + "2 de 10 assistidos" 11/400 muted; linha gap 6: `CheckCheck` 16 accent + "Marcar temporada como assistida" 12/600 accent + selo NOVO (padding 1/6, raio 6, 9/800) |
| Lista | cartões 2.14 gap 10 (E1, E2 assistidos; E3 atual; E4, E5 sem estado) |
| Elenco | "Elenco" 18/700; fileira 2.15 |

### 17 Série — escolher temporada (`gb5S1`)
Fundo = tela 16. Sheet 2.13 (altura ~430): título "Temporadas"; opções gap 14:
- Opção: padding 16, gap 12, raio 16, altura 68. Selecionada: fundo accent@8% (`#66D9FF14`), contorno 2 accent, `Check` 20 accent à direita. Demais: fundo surface-alt, contorno 1 border-subtle.
- Texto (gap 2): "Temporada 1" 16/700 text; "10 episódios · 2 assistidos" 12/400 muted.

### 18 Série — opções do episódio (`tdKGl`)
Fundo = tela 16. Sheet 2.13 (~420): título "E3 · Segundo da Sua Espécie" 20/700.
Ações: linhas de 52, gap 14 (ícone↔texto), ícone 20, texto 15/500:
| Ação | Ícone | Cor |
|---|---|---|
| Marcar como assistido | `Check` | text |
| Recomeçar episódio | `RotateCcw` | text |
| Compartilhar | `Share2` | text |
| Reportar problema | `Flag` | warning (ícone e texto) |

### 21 Player — controles (`Py3N1`, paisagem 844×390)
Fundo vídeo (protótipo: gradiente 135° `#1B2A44→#06080C`).
| Área | Especificação |
|---|---|
| Barra superior | altura 72, padding 16/28, gap 16, gradiente vertical preto@67% → transparente. `ChevronLeft` 26 text; título (peso 1, gap 2) "Resident Evil (2026)" 17/700 text + "NBRlib · FHD · Dublado" 11/400 text-2; `PictureInPicture2` 22, `Cast` 22, `Info` 22 (text) |
| Centro | linha centrada gap 44: `RotateCcw` 36 text; botão Play 84 círculo, fundo branco@15% (`#FFFFFF26`), contorno 1 border, `PauseFilled`/`PlayFilled` 38 text; `RotateCw` 36 text |
| Barra inferior | altura 152, padding 0/28/18/28, gap 12, conteúdo no fundo, gradiente transparente → preto@80% |
| Busca | linha gap 12: "1:27:03" 12/600 text; trilho peso 1, 4, raio 2, branco@20%, buffer branco@33% (progresso accent — o protótipo só mostra o buffer); "1:33:00" 12/400 text-2 |
| Botões | space-between, altura 22. Esquerda gap 22: (ícone 20 + texto 12/600, gap 6) `AudioLines` Áudio, `Captions` Legendas, `Gauge` Velocidade. Direita gap 22: `SkipForward` 22, `Maximize` 22 |
Controles somem após 3 s; entrada: fade para preto 200 ms.

### 22 Player — áudio e legendas (`e3Tdp9`)
Barra superior igual à 21. Painel à direita: largura 340, altura cheia, fundo surface@95% (`#151A22F2`), contorno 1 border-subtle, padding 24, gap 10.
- Títulos de grupo "Áudio", "Legendas": 16/700 text.
- Opção: altura 40, padding 0/12, raio 10, space-between. Selecionada: fundo accent@12%, texto 13/700 accent, `Check` 16 accent. Demais: transparente, 13/500 text.

### 28 Configurações (`WffSm`)
Fundo bg; conteúdo padding horizontal 20, gap 10; tab bar 2.8 (Config ativa). Itens 2.11.
| Seção | Itens |
|---|---|
| Título | "Configurações" 28/700 |
| CONTA E APP | Conectar aparelhos (`Link`, "Conectar TV e celular com a mesma Minha lista · Pix", chevron) · NBR Play Premium (`Crown` violet, "Compartilhar Minha lista e o progresso entre TV e celular", valor "Ativo" success) · Seleção de canais (`Library`, "Gerenciar os canais disponíveis") · Teste de conexão (`Activity`, "Medir internet, Telegram e velocidade neste aparelho") |
| CONTEÚDO | Duração mínima (`Timer`, "Oculta vídeos mais curtos que o tempo escolhido.", valor "Off" text-2) · Índice de filmes e séries (`Database`, "Atualizado hoje às 14:32", valor "Ativo" success) |
| Mais ajustes | botão 2.11 "Interface e sobre" + `ChevronDown` → abre 28b |
| ADMINISTRAÇÃO | grupo com contorno violet@33% (`#9B7CFF55`): Painel de administração (caixa violet, `Link` violet — sugere-se `Shield`; "Usuários, pedidos e aparelhos · só para admins") |

### 28b Configurações — interface e sobre (`AfWe7`)
Conteúdo gap 12; tab bar igual.
| Seção | Itens |
|---|---|
| Título | "Configurações" 28/700 |
| INTERFACE | Capas (`Image`, chave ligada) · Fotos do elenco (`Users`, "Mostrar rostos do elenco nos detalhes (senão, só nomes)", ligada) · Animações (`Sparkles`, "Luz pulsante ao carregar, controles do player e rolagem do t…", ligada) · Iluminação da capa: desfoque nativo (`Sun`, "ON = desfoque do Android 12+ · OFF = versão compatível", desligada) · Prévia do card (`SquareDashed`, "Como o card aparece enquanto a capa carrega", chevron) |
| SOBRE | Atualizações (`Download`, "Versão 0.6.0 · nova versão disponível", valor "0.7.0" accent) · Ver introdução novamente (`RotateCcw`) · Política de privacidade (`ShieldCheck`, "Como o app trata seus dados") · Sair da conta (caixa error@12%, `LogOut` error, título error) |
| Rodapé | "NBR Play 0.6.0 (32) · e-mail" 11/400 muted, centralizado |

---

## 4. Telas da TV (valores já ÷2)

### TV 07 Biblioteca (`vvTxp`)
Menu lateral 2.10 (Início ativo) + conteúdo (vertical gap 14).
| Seção | Especificação |
|---|---|
| Destaque | altura 220, imagem com gradiente igual ao hero do celular, padding 0/36/18/36, conteúdo no fundo, gap 7: título 32/800 text; meta (gap 6): "2026 · Terror · 1h33min" 12/400 text-2 + chip FHD; sinopse 12/400 text-2 1 linha (largura máx. 450) |
| Continuar assistindo | padding horizontal 36, gap 8: título 16/700; fileira gap 12 de cards 2.7 TV (180×101); o card em foco tem anel 2.9 |
| Recomendados para você | título 16/700; fileira gap 12 de pôsteres 100×150 raio 10 + título 11/600 |

### TV 09 Detalhe do filme (`Tbsnw`)
Fundo: imagem + gradiente horizontal bg@98% (esq.) → bg@80% (45%) → bg@20% (dir.). Conteúdo padding 36/48, gap 48 entre colunas.
| Coluna | Especificação |
|---|---|
| Info (peso 1, centro vertical, gap 14) | "Voltar": `ChevronLeft` 15 text-2 + 12/400 text-2, gap 5 · título 40/800 · meta gap 6: texto 13/400 text-2 + chips FHD, Dublado, 5.1 (2.5 TV) · sinopse 14/400 lh1.5 text-2 · "OK para ler tudo" 11/600 accent · ações gap 10: Assistir (primário 180×36, `PlayFilled` 15, em foco), "Adicionar à minha lista" (secundário 260, `Heart`), Reportar (secundário 130, `Flag`) |
| Elenco (260, centro vertical, gap 10) | "Elenco" 16/700; atores 2.15 TV ("Ana · Detetive") |

### TV 10 Série — detalhe (`c8QxaM`)
Fundo: imagem + gradiente horizontal bg@98% → bg@85% (45%) → bg@60%. Padding 32/40, gap 36.
| Coluna | Especificação |
|---|---|
| Info (350, centro vertical, gap 12) | Voltar · título 36/800 lh1.05 · meta: texto 12/400 + chips "3 temporadas", "28 episódios" · sinopse 13/400 lh1.5 text-2 · CTA (gap 5): primário 260×36 "Continuar T1 · E3" (foco) + "Faltam 22min · Episódio 3 de 10" 11/400 accent · ações gap 8: secundário "Minha lista" 120 (`Heart`), "Reportar" 110 (`Flag`) · Reprodução automática: padding 9, gap 8, raio 10, violet@10%, contorno 1 violet; `SkipForward` 15 violet; "Reprodução automática" 12/700 + "O próximo episódio começa em 5 s ao terminar" 10/400 text-2; selo NOVO padding 1.5/6, raio 6, 9/800 |
| Episódios (peso 1, centro vertical, gap 10) | chips de temporada (selecionada accent/on-accent) gap 6 + "Marcar como assistida" (`CheckCheck` 13 violet + 10/700 violet, gap 4, recuo 8) · progresso: trilho 4 raio 2 fill, barra success, "2 de 10 assistidos" 10/400 muted, gap 8 · lista de episódios 2.14 TV, gap 7 |

### TV 11 Player — controles (`IwV7b`)
| Área | Especificação |
|---|---|
| Barra superior | altura 90, padding 24/48, gap 12, gradiente preto@70% → transparente; `ChevronLeft` 22 text; título 20/700 + "NBRlib · FHD · Dublado" 11/400 text-2 (gap 3) |
| Barra inferior | altura 160, padding 0/48/32/48, gap 14, conteúdo no fundo, gradiente transparente → preto@85% |
| Busca | gap 12: tempo 12/600 text; trilho 5, raio 2.5, branco@20%, buffer branco@33% (+ progresso accent); em foco anel 2.9; total 12/400 text-2 |
| Botões | linha gap 14, altura 40, raio 20, padding 0/14, gap ícone-texto 6, ícone 18, texto 12/600 text, fundo branco@12% (`#FFFFFF1F`): −10s `RotateCcw` · Pausar (só ícone, 42×40, fundo accent, `PauseFilled` on-accent) · +10s `RotateCw` · Áudio `AudioLines` · Legendas `Captions` · Transmitir `Cast` · Próximo `SkipForward`. Botão em foco: fundo accent + conteúdo on-accent (ou anel 2.9) |
Teclas: ◀▶ pulam 10 s, OK mostra/esconde, Menu abre áudio e legendas.

### TV 13 Configurações (`Wk2so`)
Menu lateral (Config ativo). Conteúdo padding 32/48, gap 10: título 32/800; duas colunas de peso igual, gap 24, cada uma gap 7 com rótulo 11/700 muted + itens 2.12.
| Coluna | Itens |
|---|---|
| CONTA E APP | Conectar aparelhos (`Link`, em foco) · NBR Play Premium (`Crown` violet, "Sincronizar Minha lista, progresso e histórico") · Canais (`Library`) · Privacidade (`ShieldCheck`) · Sair (`LogOut` error) |
| INTERFACE | Capas (`Image`, chave on) · Animações (`Sparkles`, "Animação dos cards", on) · Fotos do elenco (`Users`, on) · Modo de tela (`Monitor`, "Saída 4K e taxa de quadros automáticas") · Atualizações (`RefreshCw`, "Baixando índice atualizado…") |

### TV 13b Configurações — conteúdo e sobre (`wwjuk`)
Mesma estrutura da TV 13.
| Coluna | Itens |
|---|---|
| CONTEÚDO | Duração mínima (`Timer`, em foco, valor "◀  10 min  ▶" 12/700 accent) · Índice de filmes e séries (`Database`, "Ativo" success) · Teste de conexão (`Activity`) · Modo de tela (`Monitor`, "Igualar resolução do conteúdo", "Automático" text-2) |
| SOBRE | Iluminação da capa: desfoque nativo (`Sun`, chave off) · Atualizações (`Download`, "Versão 0.6.0 · 0.7.0 disponível", "0.7.0" accent) · Ver introdução novamente (`RotateCcw`) · Sair da conta (`LogOut` error, título error) |

---

## 5. Movimento (do mapa de navegação do protótipo)
| Situação | Efeito |
|---|---|
| Biblioteca → Detalhe | elemento compartilhado: capa cresce até o hero, 240 ms ease-out; título sobe 16 dp |
| Detalhe → Player | fade para preto 200 ms; controles somem após 3 s |
| Sheets/diálogos | slide-up 220 ms + escurecimento 0→70% |
| Troca de aba | cápsula desliza 160 ms; ícone vira Filled |
| Pressionar card/botão | escala 0,97 em 80 ms |
| Foco TV | anel 2.9 + escala 1,04 em 160 ms |
| Carregamento | skeleton com brilho; capas entram com fade 160 ms |
| Minha lista | pulso do coração + aviso "Adicionado à Minha lista" 2 s |
| Episódio assistido | check verde entra com escala; barra da temporada anima |
| Movimento reduzido | tudo vira corte seco |

## 6. Demais telas do celular

Padrões que se repetem aqui (além da seção 2):
- **Topo de subtela**: linha gap 10–12, `ChevronLeft` 22–24 text + título 22/700 text (altura 27). Fica logo abaixo da status bar; conteúdo com padding horizontal 20 (24 nas de entrada).
- **Topo de entrada** (login): linha space-between de 44: `ChevronLeft` 22 · logo NBR Play 56×44 · espaço 22.
- **Rodapé de ações**: coluna gap 12, padding inferior 24, presa ao fundo (um espaço flexível empurra): primário + secundário, 342/350 de largura.
- **Botão de ação com ícone** (telas novas): altura 50, raio 25, gap 8, ícone 18, texto 15/700. Primário accent/on-accent; secundário fill + contorno border-subtle, conteúdo text.
- **Estado vazio/erro** (11, 52, 53, 54): coluna centralizada gap 16; círculo 96, raio 48, ícone 40; título 22/700 (20/700 na 11) centralizado; texto 14/400 lh1.45 text-2 centralizado; botões com ícone.
  Círculo: neutro surface-alt + ícone accent; aviso warning@12% + contorno warning@33% + ícone warning; erro error@12% + contorno error@33% + ícone error; busca accent@12% + contorno accent@33%.
- **Diálogo central** (35, 36, 56): escurecimento preto@80% (`#000000CC`); cartão 342 de largura, padding 24, gap 14, raio 24, surface, contorno border-subtle, centralizado. Ícone em círculo 56 (raio 28, cor@15%, ícone 26). Título 20/700 centralizado; texto 14/400 lh1.45 text-2 centralizado; botões primário + secundário com 294 de largura.
- **Opção selecionável** (sheets e listas): padding 12–16, raio 14, gap 12. Selecionada: fundo accent@8% e contorno 1,5–2 accent. Demais: surface-alt e contorno border-subtle. Título 15/600 text; descrição 12/400 muted (gap 2).
- **Rádio**: 20, raio 10, contorno 2 border (muted); marcado: contorno 2 accent e miolo de 10 accent (ou preenchido de accent).
- **Selo de status**: padding 3/8, raio 8, fundo cor@13% (`22`), texto 11/700 da cor; ícone 12 opcional, gap 5.
  Cores: success "Disponível/Ativo", warning "Buscando/Vencendo", accent "Pedido/Teste", error "Não encontrado/Bloqueado", violet "Família/Infantil".
- **Segmentos** (25, 26): container de 44, padding 4, raio 22, surface, contorno border-subtle; 3 segmentos de peso igual, altura 36, raio 18; ativo accent + texto 13/700 on-accent; inativo transparente + 13/700 text-2.
- **Abas em caixa** (46, 57): container de 42–44, padding 4, raio 14, surface; aba ativa surface-alt + 13/600 text, raio 10; inativa transparente + 13/500 muted.
- **Chave (variante nova, 44/46)**: 46×28, raio 14, padding 3; ligada fundo accent + bolinha 22 **bg** (escura); desligada branco@15% + bolinha 22 text. (A chave das Configurações, 2.11, usa bolinha branca; padronize uma das duas.)

### 01 Splash (`jttBv`)
Imagem cheia (arte da marca). No fundo, centralizado, padding inferior 72, gap 16: "Preparando seu conteúdo…" 13/400 text-2; barra 140×4, raio 2, trilho fill, progresso accent. Saída: fade-through 300 ms.

### 02–04 Onboarding (`tN0lo`, `Xg4bL`, `YJV1l`)
Padding 8/24/32/24. Topo space-between de 44: logo 56×44 + "Pular" 14/600 muted.
Ilustração (peso 1, centro): órbita de 240 (gradiente radial accent@20% → transparente), núcleo de 120 surface-alt com contorno 2 accent, ícone 48 accent (02 `Clapperboard`; 03/04 seguem o mesmo modelo com outro ícone e texto).
Texto (gap 12): título 28/700 lh1.2 text; descrição 16/400 lh1.45 text-2.
Pontos: gap 6, padding vertical 24; ativo 24×8 accent, demais 8×8 border, raio 4 (estica de 8 para 24 ao trocar, slide 280 ms). Botão primário "Avançar".

### 05 Login QR (`TZYQD`)
Gap 20. Topo de entrada. Título 28/700 + "Escaneie com o Telegram no seu celular:" 15/400 text-2 (gap 8).
QR: cartão 220×220 branco, raio 20, `QrCode` 170 em bg; estado (gap 8): ponto 8 warning + "Aguardando conexão…" 13/400 warning.
Passos: caixa padding 16, gap 12, raio 16, surface, contorno border-subtle; passo = número em círculo 24 fill (12/700 accent) + texto 14/400 text-2, gap 12.
Botão secundário "Entrar com telefone".

### 06 Login telefone (`QHnlr`)
Título 28/700 + "Número do Brasil (+55) — …" 15/400 text-2. Campo: altura 56, padding 0/16, gap 10, raio 14, surface, contorno 2 accent (em foco); "+55" 17/600 muted + número 17/500 text. Aviso 13/400 muted. Rodapé: "Continuar" (primário) + "Entrar com QR Code" (secundário).

### 07 Login código (`Xiiro`)
Título "Confirme o código" + texto 15/400 text-2. Cinco caixas 52×64, gap 10, centralizadas, raio 14, surface, contorno 1 border; a caixa atual tem contorno 2 accent; dígito 26/700 text. "Reenviar código" 14/600 accent centralizado. Rodapé: "Confirmar".

### 08 Verificação em duas etapas (`GxLTe`)
Campo de senha (56, raio 14, contorno 2 accent): "••••" 20/700 + `Eye` 20 muted. Erro: `CircleAlert` 16 error + 13/400 error, gap 8. Rodapé: "Entrar".

### 09 Seleção de canais (`CVoga`)
Topo: "Seleção de Canais" 28/700 + `RefreshCw` 22 accent. Subtítulo 15/400 text-2. Lista gap 10:
- Canal: padding 14, gap 12, raio 16, altura 72. Selecionado accent@8% + contorno 2 accent; demais surface + border-subtle.
- Avatar 44 círculo surface-alt, inicial 18/700 accent. Nome 16/600 text + "Filmes e séries · 1.842 itens" 12/400 muted.
- Estado (pílula de 28, padding 0/12, raio 14): "Selecionado" accent + 12/700 on-accent; "Selecionar" fill + 12/700 text-2.
Rodapé: "2 canais selecionados · Limpar" 13/400 muted centralizado + "Continuar".

### 11 Biblioteca sem canal (`ERvyQ`)
Estado vazio neutro (`Library`), título 20/700 "Nenhum canal selecionado", texto 14/400 text-2; botão primário 280 "Escolher canal" com 16 acima. Tab bar.

### 12 Busca (`m057jJ`)
Padding 0/24, gap 16. Título "Buscar" 28/700.
Campo: altura 52, padding 0/18, gap 10, raio 26, surface, contorno 2 accent; `Search` 20 accent; placeholder 14/400 muted.
Chips de filtro: Tipo, Gênero, Ano. Cabeçalho "Buscas recentes" 18/700 + "Limpar" 13/600 accent. Recentes: linhas de 44, gap 12, `History` 18 muted + 15/400 text-2 (gap 4 entre linhas). "Em alta no seu catálogo" 18/700 + fileira de PosterCard (gap 12). Tab bar.

### 13 Busca com resultados e filtros (`S3WSf6`)
Gap 14. Campo com o texto digitado 15/500 text. Chips ativos accent/on-accent com "✕" no texto (use `X` 14 on-accent). "4 resultados" 13/400 muted.
Resultado: padding 10, gap 12, raio 14, surface, contorno border-subtle; capa 56×80 raio 8; título 15/600 text + meta 12/400 muted (gap 4); selo de qualidade padding 2/8, raio 6, fill, 11/700 text-2. Lista gap 12.

### 19 Reportar problema (`rVXr0`)
Fundo = detalhe do filme. Sheet 2.13 alto (700): título "Reportar problema" 20/700; subtítulo 13/400 text-2; opções selecionáveis com rádio (Não reproduz, Problema no áudio [selecionada], Problema na legenda, Vídeo errado), padding 12, título 15/600, descrição 12/400 muted. Campo de detalhes: altura 64, padding 12, raio 14, surface-alt, contorno border-subtle, placeholder 13/400 muted. Botão primário "Enviar".

### 20 Filme — abrindo e falha (`DYOIr`)
Detalhe do filme com: botão primário de fundo **error**, texto "Falhou — tentar de novo" (branco). Aviso no fim: padding 12, gap 10, raio 12, error@10%, contorno 1 error; `CircleAlert` 18 error + 12/400 text-2.

### 23 Player — carregando (`f8SzG`)
Centro, gap 16: anel de 64 accent girando (`Loader` ou indicador circular); "Abrindo…" 16/700 text; "Carregando o início do vídeo · 38%" 12/400 text-2.

### 24 Player — rede e erro (`YICW7`)
Barra superior da 21. Cartão central 400 de largura: padding 20, gap 12, raio 20, surface@95%, contorno 1 warning, itens centralizados. `WifiOff` 32 warning; "Conexão instável" 17/700; texto 13/400 text-2 centralizado; botão secundário 360 "Tentar de novo".

### 25 Meu — minha lista (`faVqz`)
Gap 18. Título "Meu" 28/700, com atalhos à direita na mesma altura (gap 8): círculos 36 fill com ícone 18 text (`Download`, `MessageSquarePlus`, `Bell` com ponto 8 accent no canto) e perfil 36 com raio 11, gradiente 135° accent→cyan, inicial 15/800 bg.
Segmentos (Continuar / Minha lista / Histórico). Grade de 3 PosterCard (gap 10, linhas 14). Tab bar (Meu ativo).

### 26 Meu — histórico (`jMJxJ`)
Topo: "Meu" + "Limpar tudo" 13/600 error. Segmentos (Histórico ativo). Grupos por dia: rótulo 13/700 muted ("Hoje", "Ontem").
Item: padding 10, gap 12, raio 14, surface, contorno border-subtle, altura 76; miniatura 96×56 raio 8 com trilho 3 (branco@20%) e barra accent; título 14/600 text + linha 11/400 (accent "parou em…", success "concluído"), gap 3; `X` 16 muted.

### 29 Conectar aparelhos (`sqKaK`)
Topo de subtela. Texto 14/400 lh1.45 text-2. Cartão do código: padding 24, gap 12, raio 20, surface, contorno 2 accent, centralizado: "Seu código" 12/700 muted; código 44/800 ls4 accent; "Aguardando confirmação · expira em 9:41" 12/400 warning.
"ou digite o código…" 13/400 text-2 centralizado. Campo 56, padding 0/16, raio 14, surface, contorno border, placeholder 15/400 muted. Rodapé: "Conectar" + "Gerar outro código".

### 30 Aparelhos conectados (`x3wwdh`)
Aviso de sucesso: padding 14, gap 10, raio 14, success@10%, contorno 1 success; `CircleCheck` 20 success + 13/400 text-2. Texto 13/400 text-2.
Aparelho: altura 50, padding 14, gap 12, raio 14, surface; este aparelho contorno 2 accent, demais border-subtle; ícone 22 accent (`Smartphone`/`Tv`); nome 14/600 text; "Desconectar" 12/600 error à direita.

### 31 Premium — planos (`Zqibq`)
Fundo gradiente vertical `#2A1F5C` → bg. Gap 16. Selo 72 círculo violet@20% + contorno 2 violet, `Crown` 34 violet. Título 26/800 lh1.15; texto 14/400 lh1.45 text-2.
Plano: padding 16, gap 12, raio 18, altura 71; nome 17/700 + "30 dias" 12/400 muted; preço 18/800 à direita. Normal surface + border-subtle; destacado (Anual) violet@12% + contorno 2 violet, preço violet, selo "economize 24%" (padding 2/8, raio 8, success, 10/800 on-accent).
Nota 12/400 muted centralizada. Botão primário com fundo **violet** e texto branco "Pagar com Pix".

### 32 Premium — Pix (`MqD4p`)
Gap 14. Resumo: padding 14, raio 14, surface, contorno border-subtle, space-between: "Anual · R$ 89,90" 14/700 + "365 dias" 12/400 muted.
QR: cartão 200 branco raio 18, `QrCode` 156 bg; legenda 12/400 muted. Instrução 13/400 lh1.4 text-2.
Copia e cola: altura 48, padding 0/14, raio 12, surface-alt, contorno border-subtle; código 12/400 muted + `Copy` 18 accent.
Aguardando: ponto 8 warning + 12/400 warning, centralizado. Rodapé: "Copiar código Pix" + "Cancelar".

### 33 Premium — ativo (`AY5Ta`)
Fundo gradiente vertical `#1C3A2E` → bg; tudo centralizado, gap 16. Selo 112 círculo success@15% + contorno 2 success, `CircleCheck` 56 success. "Pagamento confirmado" 26/800; texto 14/400 lh1.45 text-2 centralizado. Ações (24 acima): "Conectar aparelhos" + "Voltar".

### 34 Política de privacidade (`T9slBV`)
Gap 14. Subtítulo "Como o app trata seus dados" 20/700. Seções em acordeão: altura 45, padding 14, gap 10, raio 12, surface, contorno border-subtle; título 14/600 text + `ChevronDown` 16 muted (aberto: `ChevronUp` e texto 13/400 lh1.45 text-2 abaixo). Rodapé 12/400 muted.

### 35 Atualização disponível (`SjXwO`)
Diálogo central: ícone `Download` em accent@15%; "Nova versão disponível"; texto; "Atualizar agora" + "Depois".

### 36 Sair da conta (`t9tda`)
Diálogo central: ícone `LogOut` em error@15%; "Sair da conta?"; texto; botão primário **error** com texto branco "Sair" + secundário "Cancelar".

### 37 Teste de conexão (`anDxp`)
Padding 0/20, gap 16. Texto 14/400 lh1.45 text-2. Cartão de teste: padding 14, gap 12, raio 16, surface, contorno border-subtle (em andamento: 2 accent).
Linha: ícone 20 accent (`Wifi`, `Globe`, `Send`, `Gauge`); título 15/700 text + resultado 12/400 (success; accent "Testando…"); estado à direita `CircleCheck` 20 success / `Loader` 20 accent / `CircleX` 20 error.
Velocidade tem ainda trilho 6, raio 3, fill + barra accent e "24,6 Mbps até agora" 12/400 text-2. Rodapé: "Testar de novo" + "Fechar".

### 38 Duração mínima (`c41Rs`)
Fundo = 28. Sheet 2.13 (gap 12): título "Duração mínima"; texto 13/400 text-2. Opções em linha gap 8, cada uma de peso igual × 48, raio 14: selecionada accent + 13/700 on-accent; demais surface-alt + border-subtle + 13/700 text-2 (Off, 5, 10, 20, 40 min). Nota 12/400 muted. Botão primário "Aplicar".

### 39 Atualizações e índice (`L5hYVZ`)
Cartão da nova versão: padding 16, gap 10, raio 18, accent@7%, contorno 2 accent. `Download` 20 accent + "NBR Play 0.7.0 disponível" 15/700; texto 12/400 lh1.4 text-2. Passos com ícone 16 + 12/400 (gap 10): `CircleCheck` success "Baixado (48 MB)", `ShieldCheck` accent "Verificando…", `Settings` muted "Permita esta fonte…" (muted). Botão primário "Instalar".
Rótulo "ÍNDICE DE FILMES E SÉRIES" 11/700 ls1 muted. Estados do índice (60 de altura, padding 14, gap 12, raio 14, surface, contorno border-subtle): `Loader` accent "Baixando índice atualizado…", `CircleAlert` error "Falha ao baixar", `CircleCheck` success "Ativo"; título 14/600 + detalhe 11/400 muted.

### 40 Prévia do card (`NBaoo`)
Texto 14/400 text-2. Prévia: dois cards lado a lado (gap 14, padding vertical 12, centralizados), capa 150×225 raio 14 contorno border-subtle, legenda 12/400 muted. Carregando: gradiente 120° surface-alt → `#2A3342` → surface-alt (brilho animado) com barra 100×10 raio 5 branco@12% no fundo. Pronta: imagem.
Grupo "AJUSTES" (2.11): Iluminação da capa (`Sun`, chave) e Animação dos cards (`Sparkles`, chave).

### 41 Player — pular abertura (`gbhNq`)
Player 21 sobre imagem (gradiente preto@20% → preto@80%). Extras:
- Título no canto (28,24): "A Casa do Dragão" 15/700 + "T2 · E3 · 0:52 de 1:04:20" 12/500 text-2.
- Botão "Pular abertura" à direita, acima da barra: altura 44, padding 13/20, gap 10, raio 26, fundo text (claro), `SkipForward` 18 bg + 15/700 bg; sombra preto@50% y=6 blur 20.
- Aviso à esquerda: padding 8/12, gap 8, raio 16, preto@50%; `Sparkles` 14 accent + 12/600 text.
- Trilho fino no rodapé da tela (controles ocultos): 4, branco@15%, assistido accent.

### 42 Player — próximo episódio (`eVXVj`)
Player 21 + "Créditos" no canto (15/700 + "Créditos finais" 12/500 text-2).
Cartão à direita (392 de largura): padding 14, gap 14, raio 20, surface@95%, contorno border-subtle, sombra preto@67% y=10 blur 30. Miniatura 150×84 raio 12 com play central (36, preto@67%, `PlayFilled` 16). Texto (gap 4): "PRÓXIMO EPISÓDIO EM 8s" 10/700 ls1 accent; "T2 · E4 · O Rei Vermelho" 15/700; sinopse 12/400 muted.
Ações abaixo (gap 10, botões de 46, raio 23): "Assistir agora" com contagem regressiva (fundo accent@33% e preenchimento accent que avança da esquerda; `PlayFilled` 16 + 14/700 on-accent); "Ver créditos" branco@12% + 14/600 text.

### 43 Quem está assistindo (`kQOKZ`)
Padding 60/28/40/28, gap 36, centralizado. Título 26/700. Grade 2×2 (linhas gap 28, itens espalhados): avatar 112, raio 31, gradiente 135° (accent→cyan, rose→warning, violet→accent), inicial 45/800 bg; nome 15/600 text (gap 10); perfil infantil com selo "Infantil" violet. "Adicionar": fill + contorno 2 border, `Plus` 36 text-2, nome text-2.
"Gerenciar perfis": padding 12/22, gap 8, raio 24, contorno border, `Pencil` 16 + 14/600 text-2. Nota 12/400 muted centralizada.

### 44 Novo perfil (`xPG0t`)
Padding 4/20/24/20, gap 18. Avatar 104 raio 30 (gradiente) centralizado. Campo "NOME" (rótulo 11/700 ls1 muted, gap 6): altura 50, padding 0/14, raio 14, surface, contorno 1,5 accent, 16/500 text.
"COR DO AVATAR": círculos de 36, gap 12, com os gradientes; selecionado com contorno 3 text.
Grupo de controle (2.11 sem ícone, padding 14, títulos 15/600, descrições 12/400): Perfil infantil (chave), Classificação máxima (valor "12 anos" 14/600 accent + chevron), Pedir PIN para sair (chave), Reproduzir próximo episódio (chave desligada). Botão "Salvar perfil" (52, raio 26, accent, 15/700).

### 45 Biblioteca infantil (`r9I3p`)
Topo: "Infantil" 28/700 + perfil atual (pílula 36, padding 4/10/4/4, gap 8, raio 20, fill; avatar 28 raio 9; nome 13/600; `ChevronDown` 14 muted).
Faixa: padding 14, gap 12, raio 16, violet@12% + contorno violet@33%; `ShieldCheck` 22 violet; "Modo infantil ligado" 14/700 + 12/400 text-2.
Seções com título 17/700 ("Animações", "Aventuras") e fileiras de PosterCard gap 12.

### 46 Downloads (`A84mbo`)
Armazenamento: padding 14, gap 8, raio 16, surface, contorno border-subtle; "NBR Play usa 6,4 GB" 13/600 + "18,2 GB livres" 13/500 muted; barra 8 raio 4 branco@12% com app accent e outros branco@25%.
Abas em caixa (Baixados · 4 / Na fila · 1). Item (64, gap 12): miniatura 112×64 raio 10 (com barra de progresso 4 se baixando); título 14/600 + linha 12/400 (muted; error se falhou); estado em círculo 36: `Check` success em success@13%, `PauseFilled` accent em fill, `RotateCw` error em fill.
"Baixar só no Wi-Fi": cartão padding 12/14, raio 16, surface, chave.

### 47 Baixar — escolher qualidade (`auAke`)
Fundo = detalhe. Sheet (raio 24, padding 10/20/28/20, gap 12, sem contorno; alça branco@25%): título 19/700; subtítulo 13/400 muted. Opções selecionáveis com rádio à direita (Alta · 1080p / Média · 720p / Econômica · 480p; descrição com tamanho). Botão "Baixar 2,1 GB" com `Download` 18.

### 48 Notificações (`W7Qgtq`)
Topo de subtela + "Marcar como lidas" 13/600 accent à direita. Rótulos de grupo 11/700 ls1 muted (HOJE, ESTA SEMANA).
Notificação: padding 12, gap 12, raio 16; não lida surface + contorno border-subtle e ponto 8 accent à direita; lida transparente. Capa 52×72 raio 10 com selo de ícone 22 (círculo, ícone 12 bg) no canto: accent `Tv`, success `Check`, violet `Sparkles`, warning `PlayFilled`, cyan `Download`. Texto (gap 4): título 14/600, corpo 12/400 text-2, tempo 11/500 muted.
Preferências: altura 42, padding 12/14, gap 10, raio 14, fill; `BellRing` 18 text-2 + 13/600 text-2 + chevron.

### 49 Coleção — saga (`PQOvA`)
Hero de 330 atrás (imagem + gradiente transparente → bg@93%). Topo: botões 40 círculo preto@40% com ícone 22 (voltar e seguir). Selo "COLEÇÃO · 7 FILMES" (padding 4/10, raio 8, accent@15%, 11/700 ls1 accent). Título 26/800.
Progresso: "Você viu 3 de 7" 12/600 text-2 + "Ordem de lançamento" 12/600 accent; barra 6 raio 3 branco@12% + accent.
Filme (gap 12): número 22/800 (muted se visto, text se não); capa 84×50 raio 8; título 14/600 + "2002 · 1h40min" 12/400 muted; estado 32 círculo: visto success@13% + `Check` 16 success; próximo accent + `PlayFilled` on-accent; demais fill + `PlayFilled` text.

### 50 Pedidos de título (`FA38V`)
Texto 13/400 text-2. Botão "Fazer um pedido" com `Plus`. Rótulo "MEUS PEDIDOS". Pedido: padding 12, gap 12, raio 16, surface, contorno border-subtle; capa 48×68 raio 8; título 14/600 + meta 12/400 muted + selo de status (gap 5); votos à direita: `ThumbsUp` 16 text-2 + 11/600 text-2. Ícones dos selos: `Check`, `Loader`, `Clock` (ainda não está em NbrIcons), `X`.

### 51 Novo pedido (`pLHGz`)
Busca: altura 50, padding 0/14, gap 10, raio 14, surface, contorno 1,5 accent. Rótulo "RESULTADOS DO TMDB". Resultado: padding 10, gap 12, raio 14; selecionado accent@8% + contorno 1,5 accent + `CircleCheck` 22 accent; demais transparente + `Circle` 22 muted; capa 44×64; meta em warning quando já está no acervo.
"OBSERVAÇÃO (OPCIONAL)": campo 76 de altura, padding 14, raio 14, surface, contorno border-subtle, 14/400 text-2. Nota 12/500 muted centralizada; botão "Enviar pedido".

### 52 Sem internet (`awE07`) · 53 Canal sem acesso (`czpuv`) · 54 Busca sem resultado (`MzKyB`)
Estado vazio (padrão acima), padding 4/28/24/28.
- 52: aviso `WifiOff`; "Ver downloads" (primário, `Download`) + "Tentar de novo" (secundário, `RotateCw`).
- 53: erro `Lock`; "Pedir novo convite" (`Send`) + "Escolher outro canal" (`Library`).
- 54: campo de busca no topo (48, raio 24, contorno 1,5 accent, `Search` 18 muted, texto 15/500, `X` 18 muted); ícone `SearchX` (busca); título 22/700 com o termo; "Pedir este título" (`Plus`); "Talvez você queira" 12/600 muted + chips 13/600 text.

### 55 Minha assinatura (`kXr6c`)
Cartão do plano: padding 18, gap 10, raio 20, gradiente 135° violet@25% → accent@10%, contorno violet@40%. "Família" 22/800 + selo "ATIVA" (padding 4/10, raio 10, success@15%, 11/700 ls1 success); "R$ 24,90 por mês · até 4 telas" 13/500 text-2; `Calendar` 16 text-2 + "Renova em 03/12 · Pix automático" 13/600 text.
"TELAS EM USO · 2 DE 4" + medidor: 4 barras de peso igual × 8, gap 6, raio 4 (usadas accent, livres branco@12%).
Aparelhos: grupo 2.11 (ícones `Tv`, `Smartphone`, `Tablet`; linha de status 12/400 success/muted; "Encerrar" pílula 27, padding 6/10, raio 10, fill, 12/600). "Histórico de pagamentos" (`Receipt`). Botão "Trocar de plano"; "Cancelar assinatura" 13/600 error.

### 56 Limite de telas atingido (`o7eLq`)
Detalhe do filme + escurecimento preto@75% + diálogo (350, padding 22, gap 12): ícone `MonitorX` warning em warning@15%; título 19/700; texto 13/400 lh1.4 text-2.
Lista gap 8: tela (padding 8/10, gap 10, raio 12, surface-alt): `Tv` 18 text-2; nome 13/600 + título 11/400 muted; "Encerrar" (padding 5/10, raio 9; primeiro accent/on-accent, demais fill/text, 11/700).
Ações (gap 10, 44 de altura, raio 22): "Mais telas" fill + 14/600 text; "Agora não" contorno border-subtle + 14/600 text-2.

### 57 Admin — painel (`g7ifne`)
Topo: "Administração" 22/700 + selo ADMIN à direita (padding 4/9, gap 5, raio 10, violet@15%, `Shield` 13 violet, 11/700 ls1 violet).
Métricas 2×2 (gap 10): cartão padding 14, gap 4, raio 16, surface, contorno border-subtle; rótulo 12/500 muted, número 26/800 text, variação 11/600 (success/muted/warning/error).
Abas em caixa (Usuários / Pedidos / Aparelhos). Busca: 44, padding 0/14, gap 10, raio 22, surface; `Search` 16 muted, 14/400 muted, `SlidersHorizontal` 16 text-2.
Lista (grupo 2.11, padding 10/14): avatar 36 círculo cor@20% com iniciais 14/700 da cor; nome 14/600 + plano 12/400 muted; selo de status. Tab bar.

### 58 Admin — usuário (`i6BVC`)
Perfil: avatar 60 (cor@20%, iniciais 23/700), nome 18/700, telefone mascarado 12/400 muted, selos (gap 6).
Grupos 2.11 com ícone 18 text-2 sem caixa, padding 11/14: PLANO (`CreditCard`, ação "+30 dias" accent pílula 27); APARELHOS · 3 DE 4 (`Tv`/`Smartphone`, ação "Sair" fill); ATIVIDADE (`History`, `MessageSquareWarning`).
Ações (gap 10, 48, raio 24): "Encerrar sessões" fill + 14/600; "Bloquear acesso" error@13% + contorno error@40% + 14/700 error.

### 59 Admin — pedidos (`AOIKc`)
Filtros em pílulas de 30 (padding 7/12, raio 16): ativo accent + 13/600 on-accent; demais fill + 13/600 text-2.
Pedido: padding 12, gap 10, raio 16, surface, contorno border-subtle; capa 40×58 raio 8; título 14/600 + meta 12/400 muted; votos "14" 17/800 accent + "votos" 10/600 muted. Ações (34 de altura, raio 17, gap 8): "Marcar disponível" accent + 12/700 on-accent; "Buscando" fill + 12/600 text; recusar 34 círculo fill com `X` 16 error. Nota 12/500 muted centralizada.

---

## 7. Demais telas da TV (valores já ÷2)

### TV 01 Splash (`MeBkr`)
Imagem cheia; no fundo (padding inferior 48, gap 10): texto 12/400 text-2 + barra 180×3, raio 1,5, fill + accent.

### TV 02–04 Onboarding (`M4eefM`, `RwN9y`, `vpEbo`)
Padding 48, gap 48, duas colunas. Esquerda (456, space-between vertical): logo 60×48; título 32/800 lh1.1 + texto 14/400 lh1.45 text-2 (gap 12); rodapé (gap 14): pontos 22×7 ativo / 7×7, gap 5, raio 3,5; ações gap 10: "Avançar" primário 180×36 (foco) + "Pular" secundário 100×36; dica "Use ◀ ▶ no controle para navegar" 11/400 muted.
Direita: órbita 280 com núcleo 130 (surface-alt, contorno 2 accent), ícone 55 accent.

### TV 05 Login QR (`EPIxS`)
Padding 48/60, gap 60, centro. Esquerda (500, gap 16): logo; título 32/800; "Método 1 · QR Code (recomendado)" 14/700 accent; passos gap 10 (número em círculo 24 fill, 12/800 accent; texto 14/400 text-2, gap 10); "Prefere receber um código?…" 11/400 muted; secundário 220×36 "Entrar com telefone" (foco).
Direita: QR 240 branco raio 16 (`QrCode` 190); estado ponto 7 warning + 12/400 warning.

### TV 06 Seleção de canais (`Rzr7o`)
Padding 36/48, gap 16. Topo: título 32/800 + secundário 120×36 "Atualizar". Texto 14/400 text-2.
Grade de 4 cartões (gap 14): padding 16, gap 10, raio 14; selecionado accent@8% + contorno accent (o focado com anel 2.9); demais surface + border-subtle. Avatar 48 círculo surface-alt, inicial 22/800 accent; nome 17/700; detalhe 11/400 muted; pílula 24 (padding 0/12, raio 12): "Selecionado" accent/on-accent ou "Selecionar" fill/text-2, 11/700.
Ações (gap 10): "Continuar" primário 180 + "Limpar" secundário 110 + "2 canais selecionados" 12/400 muted.

### TV 08 Busca (`tuRp3`)
Menu lateral (Busca ativa). Padding 32/36, gap 32, duas colunas.
- Teclado (320, gap 12): "Buscar" 32/800; campo 38 de altura, padding 0/14, gap 7, raio 19, surface, contorno border, `Search` 15 accent, texto 14/500. Teclas 44×38, raio 8, fill, contorno border-subtle, 14/600 text, grade 6 colunas com gap 6 (tecla focada: anel 2.9). Linha de ações (gap 6): "Espaço" e "⌫ Apagar" fill, "Concluir" accent/on-accent, 102×38, 12/700.
- Resultados (gap 12): chips Tipo/Gênero/Ano + "4 resultados" 11/400 muted; grade de pôsteres 100×150 raio 10, gap 12, título 11/600 (até 2 linhas).

### TV 12 Meu (`Pj8eZ`)
Padding 32/36, gap 16. Topo space-between: "Meu" 32/800 + chips de segmento (Continuar / Minha lista ativo / Histórico) gap 6. Fileira de pôsteres 100×150 gap 12. Dica "Aperte OK em um título para abrir · segure OK…" 11/400 muted.

### TV 14 Conectar aparelhos (`c4MhI`)
Padding 48/60, gap 60. Esquerda (460, gap 14): título 32/800; texto 14/400 lh1.5 text-2; passos (número em círculo 24, texto 13/400 text-2, gap 9); ações gap 10: "Gerar outro código" secundário 220 (foco) + "Voltar" 110.
Direita: cartão do código 320 de largura, padding 28, gap 12, raio 20, surface, contorno 1,5 accent; "Seu código" 12/700 muted; código 55/800 ls4 accent; aguardando 12/400 warning.

### TV 15 Premium — Pix (`vnC8t`)
Fundo gradiente horizontal `#2A1F5C` → bg. Padding 36/60, gap 48.
- Planos (512, gap 12): `Crown` 28 violet + "NBR Play Premium" 28/800 (gap 10); texto 13/400 lh1.5 text-2; plano padding 14, gap 10, raio 12 (nome 16/700, "30 dias" 11/400 muted, preço 17/800); anual violet@12% + contorno 2 violet + brilho violet@40% blur 12, selo "economize 24%" 9/800 on-accent em success (padding 1,5/6, raio 6). Nota 11/400 muted.
- Pix (280, padding 20, gap 10, raio 16, surface, contorno border-subtle, centro): "Pagar com Pix · Anual R$ 89,90" 13/700; QR 190 branco raio 12 (`QrCode` 150); instrução 11/400 lh1.4 text-2 centralizada; aguardando ponto 6 warning + 10/400 warning; "Cancelar" secundário 240 (foco).

### TV 16 Sair da conta (`vdyzo`)
Escurecimento preto@80%; diálogo 380 de largura, padding 28, gap 12, raio 18, surface, contorno border-subtle. Ícone em círculo 48 error@15%, `LogOut` 22 error; "Sair da conta?" 22/800; texto 13/400 text-2 centralizado; ações gap 10: "Cancelar" secundário 150 (foco inicial) + "Sair" 150 fundo error, 14/700 on-accent.

### TV 17 Teste de conexão (`OuReh`)
Escurecimento preto@80%; painel 480, padding 28, gap 12, raio 18, surface, contorno border-subtle. Título 22/800. Linha de teste: padding 10/12, gap 10, raio 10, surface-alt; ícone 18 accent; título 14/700 + resultado 11/400 (success/accent); estado 18. Ações: "Testar de novo" primário 180 (foco) + "Fechar" secundário 120.

### TV 18 Modo de tela (`Ka9MV`)
Escurecimento preto@80% e painel lateral à direita: 320 de largura, altura cheia, padding 36/28, gap 10, surface, contorno esquerdo border-subtle. Título 22/800; texto 11/400 lh1.45 text-2.
Opção (49 de altura, padding 9/12, gap 8, raio 10): selecionada/focada accent@10% + anel 2.9, título 14/700 accent, `Check` 16 accent; demais surface-alt + border-subtle, título 14/700 text; descrição 10/400 muted. Opções: Automático, 4K · 60 Hz, 1080p · 60 Hz, Desligado.

### TV 19 Quem está assistindo (`o8J1WA`)
Centro, gap 40. Título 28/700. Perfis em linha (gap 32): avatar 110, raio 32, gradiente, inicial 48/800 bg; nome 15/500 text-2 (gap 10). Focado: avatar 120, contorno 3 accent + brilho accent@53% blur 12, nome 15/700 text. Selo "Infantil" (padding 3/7, raio 6, violet@20%, 10/700 violet). "Adicionar": fill + contorno 1,5 border, `Plus` 36 text-2.
"Gerenciar perfis": padding 9/18, gap 7, raio 20, contorno border, `Pencil` 13 + 13/600 text-2.

### TV 20 Próximo episódio (`WH1Pu`)
Player TV 11 sobre imagem (gradiente horizontal preto@13% → preto@87%). "Créditos" no canto (48,36): título 18/700 + "Créditos finais" 12/500 text-2.
Cartão (412 de largura, canto inferior direito): padding 16, gap 14, raio 16, surface@95%, contorno border-subtle. Topo (gap 14): miniatura 160×90 raio 10; "PRÓXIMO EPISÓDIO EM 8s" 10/700 ls1 accent, título 18/700, sinopse 11/400 muted (gap 5). Ações (gap 10, 40 de altura, raio 20): "Assistir agora" 185 accent, `PlayFilled` 14 + 14/700 on-accent, em foco com contorno 2 branco + brilho accent@53%; "Ver créditos" branco@12% + 14/600 text.
Dica no rodapé: `CircleDot` 12 muted + "OK assiste agora · Voltar continua os créditos" 11/500 muted.

### TV 21 Coleção (`csXHF`)
Fundo imagem + gradiente horizontal bg@98% (35%) → bg@40%. Menu lateral. Bloco de informação (380 de largura, a partir de x 112, y 85, gap 12):
selo "COLEÇÃO · 7 FILMES" (padding 4/8, raio 6, accent@15%, 10/700 ls1 accent); título 32/800; texto 13/400 lh1.4 text-2; barra 260×5 raio 2,5 branco@15% + accent.
Lista (gap 7): linha padding 8/12, gap 10, raio 10, branco@4%; número 16/800 (muted se visto); título 13/600 text; estado 14: `Check` success, `PlayFilled` text-2. Linha focada: accent@12% + anel 2.9 (brilho accent@53%), número text, `PlayFilled` accent.

---

## 8. Cobertura
- **Celular:** todas as telas do protótipo. 01–09, 10, 11–13, 14–18, 19, 20, 21–24, 25, 26, 28, 28b, 29–40, 41, 42, 43–59 (não existe tela 27 no protótipo). Em 03 e 04 só foi lido o padrão da 02 (mesmo layout, muda ícone e texto).
- **TV:** todas as telas, de TV 01 a TV 21, mais a 13b. Em TV 03 e 04 só foi lido o padrão da TV 02.
- **Ícones citados que ainda não existem em `NbrIcons`:** `Clock` (59/50) e `ChevronUp` (acordeão aberto). Os demais estão no app ou na lista "ainda sem uso" de `docs/ICONES.md`.
