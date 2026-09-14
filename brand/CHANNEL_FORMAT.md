# Padrão de postagem — Nbr PLAY

O app lê **legendas com rótulos** (`Rótulo: valor`). O canal é um **banco de dados do app** —
poste **sem emoji, o mais enxuto possível**. A ordem é livre e **todo campo é opcional** (o que
faltar simplesmente não aparece na tela). Acentos, emoji e um `|` antes do rótulo são tolerados
(caso venham de outro canal), mas não são necessários. Linhas só com `@canais` ou só com
`#hashtags` são ignoradas.

Há dois formatos, e os dois continuam funcionando:

- **Simples (compatível):** canais como os Polemic. Só título/ano/gêneros/áudio/diretor/sinopse.
- **Rico (recomendado no seu canal):** todos os campos abaixo + imagens (pôster e fundo) por URL.
  Como o texto rico ultrapassa o limite de **1024 caracteres** de legenda de mídia, poste em
  **duas mensagens**: uma **mensagem de texto** com os metadados (limite 4096) e, **logo abaixo,
  o vídeo**. O app pareia o texto com o vídeo seguinte (por adjacência).

---

## Formato rico — modelo para copiar

**Mensagem 1 = TEXTO** com os metadados (remova as linhas que não tiver). **Mensagem 2 = o VÍDEO**,
logo abaixo (sem precisar de legenda):

```
Título: Duna: Parte 2
Original: Dune: Part Two
Tipo: Filme
Ano: 2024
Duração: 166
Nota: 8.4
Classificação: 14
Gêneros: Ficção científica, Aventura, Drama
Categoria: Lançamentos
Coleção: Duna
País: EUA
Áudio: Dublado, Legendado
Qualidade: 1080p, Dual
Diretor: Denis Villeneuve
Estúdio: Legendary Pictures
Elenco: Timothée Chalamet::https://suacdn/tc.jpg; Zendaya::https://suacdn/z.jpg; Rebecca Ferguson::https://suacdn/rf.jpg
Pôster: https://suacdn/duna2-poster.jpg
Fundo: https://suacdn/duna2-backdrop.jpg
Trailer: https://suacdn/duna2-trailer.mp4
TMDB: 693134
Tags: deserto, épico, ficção
Sinopse: Paul Atreides se une a Chani e aos Fremen enquanto busca vingança contra os
conspiradores que destruíram sua família...
```

Poste o texto acima e, **em seguida, o arquivo de vídeo** (sem outra mensagem no meio). A capa vem
da URL em `Pôster:` — não precisa anexar foto.

> **Por quê duas mensagens:** legenda de mídia no Telegram = **1024 caracteres**; mensagem de texto
> = **4096**. O formato rico (com URLs de elenco/pôster/fundo) não cabe em 1024.

---

## Campos

### Usados HOJE na tela de detalhes
| Rótulo | Sinônimos aceitos | Formato / valores | Onde aparece |
|---|---|---|---|
| `Título` | `Filme`, `Title` | texto | Título grande |
| `Original` | `Título original` | texto | Subtítulo cinza |
| `Ano` | `Lançamento`, `Year` | 4 dígitos (2024) | Linha meta |
| `Duração` | `Duracao`, `Runtime` | **minutos** (166 → "2h 46min") | Linha meta |
| `Nota` | `Avaliação`, `Rating` | 0–10, decimal (8.4) | ★ na linha meta |
| `Classificação` | `Faixa`, `Idade` | `L`, `10`, `12`, `14`, `16`, `18` | Selo na linha meta |
| `Gêneros` | `Gênero`, `Genres` | lista por vírgula | Linha de gêneros |
| `País` | `Pais`, `Country` | texto | (meta/detalhes) |
| `Áudio` | `Audio` | `Dublado`, `Legendado`, `Dual` (vírgula) | (meta/detalhes) |
| `Diretor` | `Director` | texto | Créditos |
| `Elenco` | `Cast` | `Nome::urlFoto; Nome::urlFoto; Nome` | Fileira de rostos |
| `Sinopse` | `Synopsis` | texto (várias linhas) | Sinopse |
| `Pôster` | `Poster`, `Capa` | URL de imagem **retrato 2:3** | Capa do card |
| `Fundo` | `Backdrop`, `Capa de fundo` | URL de imagem **paisagem 16:9** | Fundo da tela |

- **Elenco com foto:** cada ator é `Nome::URL`, separados por `;`. Ator sem `::URL` mostra só o
  nome (inicial colorida). O usuário pode **desligar as fotos** nas Configurações (vira só nomes).
- **Fundo ausente:** se não houver `Fundo:`, o app usa o **pôster desfocado** como fundo (é o caso
  dos canais Polemic).

### Reservados para DEPOIS (pode já postar; o app passa a usar quando implementarmos)
| Rótulo | Formato | Uso futuro |
|---|---|---|
| `Tipo` | `Filme` \| `Série` \| `Documentário` \| `Episódio` | Filtro/organização por tipo |
| `Categoria` | lista por vírgula (ex.: Lançamentos, Ação) | Trilhas/fileiras temáticas na home |
| `Coleção` | texto (ex.: Duna) | Agrupar franquia/saga |
| `Temporada` / `Episódio` | número | Séries |
| `Estúdio` | texto | Detalhes |
| `Trailer` | URL | Botão "Assistir trailer" |
| `TMDB` | id numérico | Enriquecer/deduplicar |
| `Tags` | lista por vírgula | Busca / relacionados |

---

## Regras
- **Imagens (Pôster/Fundo/fotos do elenco):** hospede em um endereço público e estável; use a URL
  completa (`https://...`). Recomendado: **pôster 2:3** (ex.: 600×900) e **fundo 16:9** (ex.:
  1280×720). Fotos do elenco quadradas (ex.: 140×140) ficam melhores.
- **Duas mensagens por filme**: texto (metadados) + vídeo logo abaixo. Não coloque outra mensagem
  entre eles. O modo antigo (foto do pôster + vídeo) também continua funcionando.
- **Não poste "Não informado"** — simplesmente **omita a linha** do campo que você não tem (o app
  esconde o que não existir).
- O **nome do arquivo do vídeo não importa** — os dados vêm da legenda.
- Campos podem faltar; a tela some com o que não existir.

---

## Exemplo mínimo (só o essencial)
```
Título: Cidade de Deus
Ano: 2002
Gêneros: Drama, Crime
Sinopse: A história da Cidade de Deus, no Rio de Janeiro...
Pôster: https://suacdn/cdd-poster.jpg
```
(o vídeo do filme vai nessa mesma mensagem)

---

## Créditos
Ao usar dados/imagens do TMDB, mantenha a atribuição: “Este produto usa a API do TMDB, mas não é
endossado nem certificado pelo TMDB.”
