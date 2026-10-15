---
name: ntv2-release
description: Publica uma nova versão do NTV (bump de versionName/versionCode, commit chore(release), tag vX.Y.Z e push que dispara o workflow de atualização). Use quando o usuário pedir para lançar/publicar/fazer release ou subir versão.
---

# Release do NTV

A publicação é feita por `scripts/release.sh`. O push da tag `v*` dispara `.github/workflows/release.yml`, que testa, assina o APK, cria a GitHub Release e publica o `update.json` no Pages. Isso é **público e difícil de desfazer**: só execute com pedido explícito do usuário.

## Passos

1. Confirme que a árvore está limpa e que a branch é `main` (`git status`, `git branch --show-current`). O script recusa se não for.
2. Valide antes de publicar: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Se falhar, pare e mostre a saída.
3. Descubra o tipo de bump com o usuário se não foi dito: `patch` (padrão), `minor`, `major` ou versão exata `X.Y.Z`.
4. Rode `scripts/release.sh <bump> --dry-run` e mostre a linha `Versão: A -> B` e a tag.
5. Peça confirmação clara para publicar. Só então rode `scripts/release.sh <bump>` (ele pergunta `[s/N]`; responda `s` apenas depois do sim do usuário).
6. Informe o link de Actions que o script imprime. Não faça polling do workflow.

## Regras do projeto

- **Não** rode `:app:assembleRelease` nem instale o `app-release.apk` por conta própria durante o desenvolvimento; só sob pedido.
- Mensagem de commit do bump é sempre `chore(release): X.Y.Z` (o script já faz isso). **Sem Co-Authored-By** do Claude nos commits.
- Release e debug têm assinaturas diferentes: instalar uma sobre a outra exige desinstalar (perde o login do Telegram). Avise antes de sugerir instalar.
- Documentação do fluxo e dos secrets: `docs/UPDATES.md`.
