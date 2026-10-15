---
name: ntv2-architecture
description: Arquitetura de software do NTV (app Android/Fire TV de vídeo via Telegram). Use ao planejar uma feature nova, decidir onde colocar código, criar módulos/camadas, revisar acoplamento ou propor refatoração. Entrega decisão, estrutura e riscos antes de codar.
---

# Arquitetura de software: NTV

Aplicar **antes** de escrever código em mudanças que tocam mais de um pacote ou criam conceito novo. Para ajuste pequeno, não precisa.

## Mapa atual (confirme no código antes de afirmar; pode ter mudado)

- Módulos Gradle: `:app` e `:ffmpeg-decoder` (áudio por software).
- `app/src/main/java/com/ntv2/app/`
  - `core/`: infraestrutura compartilhada: `telegram`, `player`, `network`, `database`, `storage`, `library`, `multipart` (filmes em partes), `recommendations`, `preferences`, `cast`, `navigation`, `ui`.
  - `feature/`: telas e fluxos: `auth`, `channels`, `media`, `playback`, `settings`, `splash`, `update`.
  - `di/`: injeção de dependências.
- Tooling: `scripts/` (release), `tools/` (cortador de filmes), `docs/`, `site/`, workflow de release em `.github/`.

## Princípios deste projeto

1. **`feature` depende de `core`, nunca o contrário**; `feature` não importa outra `feature` (use `core/navigation` ou um contrato em `core`).
2. **Dispositivo fraco primeiro** (Fire TV Stick, 4 núcleos MTK): memória, overdraw e threads contam. Trabalho pesado fora da main thread; listas e imagens enxutas.
3. **Falha de decoder/rede é caminho normal**: o player tem fallback (software em janelas, modo de tela 4K, "não suportado"). Capacidades novas de mídia devem degradar, não travar.
4. **Mídia = URL/arquivo virtual, não download síncrono** (busca, filmes em partes; índice local como evolução).
5. **Privacidade/LGPD**: dados pessoais (favoritos, histórico) ficam locais; qualquer envio externo é decisão explícita do usuário.
6. **Compatibilidade**: minSdk 23; Android 9 e anteriores se comportam diferente, cheque as APIs.

## Roteiro de resposta

1. **Contexto**: o que o pedido muda e quais pacotes toca (leia o código, não suponha).
2. **Opções**: 2 ou 3 desenhos, com custo, risco e impacto no Fire TV. Dê uma **recomendação** única.
3. **Estrutura proposta**: pacotes/classes novos, contratos (interfaces) entre `core` e `feature`, fluxo de dados e threading, o que persiste e onde.
4. **Riscos e testes**: o que pode quebrar (decoder, concorrência, migração do banco, update/assinatura) e como validar (unit test, `am instrument`, aparelho real).
5. **Passos incrementais**: etapas pequenas que compilam e testam sozinhas.
6. Só então implementar, com `compileDebugKotlin` e `testDebugUnitTest` a cada etapa.

## Não fazer

- Não remover itens/infos da tela sem perguntar (reposicionar é ok).
- Não introduzir dependência pesada sem justificar tamanho de APK e custo no Fire TV.
- Não mudar migração de banco nem assinatura/atualização sem plano de rollback.
