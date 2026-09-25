# Atualizações do NTV

O aplicativo consulta `https://nobrinho.github.io/ntv2/update.json`, baixa o APK anunciado,
confere tamanho, SHA-256, identificador do pacote, código de versão e certificado de assinatura,
e então entrega o arquivo ao instalador oficial do Android.

## Preparação obrigatória

Antes da primeira publicação, confirme que a chave configurada no GitHub é a mesma que assinou
os APKs já instalados. Uma chave diferente exige desinstalar a versão anterior e apaga os dados
locais. Guarde uma cópia offline permanente do keystore.

Configure estes GitHub Actions secrets:

- `NTV_SIGNING_KEY_BASE64`
- `NTV_SIGNING_STORE_PASSWORD`
- `NTV_SIGNING_KEY_ALIAS`
- `NTV_SIGNING_KEY_PASSWORD`
- `TELEGRAM_API_ID`
- `TELEGRAM_API_HASH`

O primeiro secret contém o arquivo JKS inteiro codificado em Base64. Nunca adicione o JKS ao Git.

No repositório, configure GitHub Pages com a origem **GitHub Actions**. O environment
`github-pages` também deve permitir publicação pela branch/tag usada para releases.

## Publicar uma versão

1. Aumente `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Faça commit das alterações.
3. Crie uma tag exatamente igual ao nome da versão, por exemplo `v0.4.0`.
4. Envie a tag ao GitHub.

O workflow testa o projeto, gera um APK assinado, calcula o hash, cria a GitHub Release, valida o
arquivo publicado e só então publica o novo `update.json` no GitHub Pages.

## Atualização obrigatória

Por padrão, o workflow publica `required: false`. Para bloquear versões antigas, ajuste a geração
do manifesto no workflow e aumente `minimumSupportedVersionCode`. Erro de rede nunca deve ser
tratado pelo aplicativo como atualização obrigatória.

## Teste antes da distribuição

Instale a versão anterior em um aparelho de teste, publique uma versão com `versionCode` maior e
execute o fluxo inteiro pelo menu Configurações > Atualizações. Confirme que login, canais e
progresso de reprodução permanecem intactos.
