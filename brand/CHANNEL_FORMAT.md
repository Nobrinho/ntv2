# Padrão de postagem — Nbr PLAY

Para o app captar **título, pôster e sinopse** com 100% de acerto, poste cada filme em **duas mensagens, nesta ordem**:

1. **FOTO (pôster) com a legenda padronizada** abaixo.
2. **VÍDEO** do filme, **imediatamente depois** (sem nenhuma outra mensagem no meio).

## Legenda da FOTO (pôster)

Use rótulos com `:` (emoji antes é opcional). Ordem livre; campos opcionais podem faltar.

```
🎬 Título: <nome do filme>
📅 Ano: <ano>
🎭 Gêneros: <g1, g2>
🗣 Áudio: <Dublado | Legendado | Dual>
🎬 Diretor: <nome>
📝 Sinopse: <texto da sinopse, pode ter várias linhas>
```

Também aceitos: `Filme:` (equivale a `Título:`), `Lançamento:` (equivale a `Ano:`).
Linhas só com `@canais` ou só com `#hashtags` são ignoradas.

## Regras

- **Pôster**: imagem **retrato 2:3** (ex.: 1000×1500). O app mostra o card em retrato.
- **Adjacência**: o vídeo tem que vir **logo após** a foto (o app pareia por proximidade).
- O **nome do arquivo do vídeo não importa** — o título vem da legenda.
- Se preferir uma mensagem só, dá para pôr a mesma legenda **no próprio vídeo** (o app também lê),
  mas aí a capa será o *frame* do vídeo (sem pôster retrato).

## Exemplo

Mensagem 1 (foto do pôster) — legenda:
```
🎬 Título: Fator de Risco
📅 Ano: 2015
🎭 Gêneros: Drama
🗣 Áudio: Dual
🎬 Diretor: Austin Stark
📝 Sinopse: Após o trágico derramamento de óleo de 2010 no Golfo do México, um político
idealista luta para defender as vítimas locais...
```
Mensagem 2 (logo abaixo): o arquivo de vídeo do filme.
