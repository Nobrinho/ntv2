# NBR Play (ntv2)

App Android para **TV (Android TV / Fire TV) e celular** que organiza canais do Telegram como uma
biblioteca de filmes e séries, com reprodução progressiva pelo TDLib. O app é um agregador e organizador:
não hospeda nem vende conteúdo.

Versão atual: **0.9.2** (`versionCode` 37). Histórico e próximos passos: [docs/ROADMAP.md](docs/ROADMAP.md).

## Recursos

- **Biblioteca** por canal, com abas Filmes e Séries. A grade do canal NBRlib vem do índice, e "Carregar mais"
  e "Atualizar" usam o mesmo índice.
- **Trilhas no topo da grade:** Continuar assistindo, Recomendados e Porque você viu.
- **Busca** com teclado próprio na TV (sem o teclado do sistema) e filtros por:
  - tipo;
  - gênero;
  - ano, com autocompletar;
  - diretor;
  - nota mínima no IMDb ou no Rotten Tomatoes;
  - elenco.
- **Detalhes:**
  - logo do título, sinopse e elenco (pessoas com foto primeiro);
  - notas, chips de ano, gênero e diretor;
  - favoritar, reportar e "Porque você viu".
- **Prévia de trailer** (YouTube oficial, IFrame API):
  - toca no fundo dos detalhes a 50% do volume;
  - o botão Trailer continua o mesmo vídeo em tela cheia, com som e legenda em português;
  - no fim, a tela volta aos detalhes.
- **Séries:** temporadas e episódios. OK no episódio abre os detalhes dele (Assistir/Continuar/Recomeçar).
- **Player** (media3/ExoPlayer):
  - download progressivo do Telegram, com janela no disco que libera o trecho já assistido;
  - áudio por software para AC3/EAC3/DTS/TrueHD;
  - fallback de Dolby Vision perfil 7 para HEVC;
  - filmes em partes, tocados como um arquivo só;
  - legendas, Chromecast e retomar de onde parou.
- **Perfis** ("Quem está assistindo?") e perfil Kids.
- **Atualização automática** pelo `update.json` publicado no GitHub Pages.
- **Navegação da TV:**
  - um único anel de foco que desliza entre os itens, inclusive entre telas sobrepostas;
  - o foco volta ao card de origem ao fechar uma tela;
  - o card focado fica sempre inteiro na tela.

## Arquitetura

| Parte | Onde | O que faz |
|---|---|---|
| App | `app/` | Kotlin + Jetpack Compose (Compose for TV), Clean Architecture por feature, DI manual em `di/AppContainer.kt` |
| Decoder de áudio | `ffmpeg-decoder/` | FFmpeg estático (AC3/EAC3/DTS/TrueHD) para o media3. Recompilar com a skill `ntv2-ffmpeg-rebuild` |
| Backend | repo `ntv2-backend` | Supabase: catálogo (filmes, séries, episódios, `trailer_url`, notas), `search_catalog`, `app_config`, reportes |
| Bot de publicação | repo `agente_filmes` | Publica no canal, grava o catálogo (`catalog-upsert`) e gera o `index.json` |
| Site | `site/` | Página de instalação e `update.json` (GitHub Pages) |

Pacotes em `app/src/main/java/com/ntv2/app/`:

- `core/`:
  - `telegram/` (TDLib) e `player/` (data sources progressivos, ExoPlayer);
  - `storage/` (orçamento e limpeza de disco) e `multipart/` (filmes em partes);
  - `catalog/` e `backend/` (Supabase);
  - `recommendations/`, `ui/` (marca, anel de foco `FocusGlide`, componentes), `database/` (Room) e `navigation/`.
- `feature/`:
  - `media/` (biblioteca, busca, detalhes, séries, trailer);
  - `playback/`, `auth/`, `channels/`, `profiles/`, `settings/`, `splash/`, `update/` e `onboarding/`.

Fontes de dados de um título, em ordem: catálogo do backend, índice do canal (`index.json` no Pages) e legenda
do post no Telegram ([brand/CHANNEL_FORMAT.md](brand/CHANNEL_FORMAT.md)). A skill `ntv2-architecture` guia onde colocar código
novo.

## Configuração local

Credenciais ficam em `~/.gradle/gradle.properties` (o `gradle.properties` do projeto é versionado):

```properties
telegramApiId=123456
telegramApiHash=...
supabaseUrl=https://<projeto>.supabase.co
supabaseAnonKey=...
```

Sem `telegramApiId`/`telegramApiHash` o build usa o gateway falso do Telegram (`TELEGRAM_REAL_ENABLED=false`).
Veja também [gradle.properties.example](gradle.properties.example).

## Build e testes

```bash
./gradlew :app:compileDebugKotlin
```

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:installDebug
```

- `minSdk` 23 (Fire OS 6+), `targetSdk`/`compileSdk` 35.
- Testes JVM em `app/src/test`; instrumentados em `app/src/androidTest` (rodar com `am instrument`, não com
  `connectedAndroidTest`, que desinstala o app).
- Emulador usado no desenvolvimento: AVD `Television_1080p`. O emulador não decodifica HEVC 4K nem Dolby Vision:
  teste esses vídeos em aparelho real.
- Debug e release têm assinaturas diferentes: trocar uma pela outra exige desinstalar e perde o login do Telegram.
- Logs úteis no Fire TV: `adb logcat -s NtvPlayer NtvDownload NtvDiskWindow NtvTrailer NtvGrid` (skill `ntv2-firetv-logs`).

## Release

`scripts/release.sh <patch|minor|major|X.Y.Z>` sobe a versão, cria o commit `chore(release): X.Y.Z` e a tag `vX.Y.Z`.
O push da tag dispara `.github/workflows/release.yml`, que testa, assina o APK, cria a GitHub Release e publica
o `update.json`. Detalhes e secrets em [docs/UPDATES.md](docs/UPDATES.md) (skill `ntv2-release`).

## Documentação

| Documento | Assunto |
|---|---|
| [docs/ROADMAP.md](docs/ROADMAP.md) | O que já foi entregue, o que está em aberto e a ordem planejada |
| [docs/UPDATES.md](docs/UPDATES.md) | Fluxo de atualização, `update.json` e secrets do CI |
| [docs/SERIES_EPISODES.md](docs/SERIES_EPISODES.md) | Contrato de séries e episódios no índice |
| [docs/PLANO-CATALOGO-BACKEND.md](docs/PLANO-CATALOGO-BACKEND.md) | Catálogo no Supabase, Telegram só com o vídeo |
| [docs/PLANO-MIGRACAO-NBRPLAY-NBRLIB.md](docs/PLANO-MIGRACAO-NBRPLAY-NBRLIB.md) | Migração do acervo do NBR Play para o NBRlib (concluída) |
| [docs/PROTOTIPO-ESPEC.md](docs/PROTOTIPO-ESPEC.md) | Medidas do protótipo (pen.dev) em dp, celular e TV |
| [docs/ICONES.md](docs/ICONES.md) | Ícones próprios do app |
| [docs/superpowers/specs/2026-11-03-trailer-preview-design.md](docs/superpowers/specs/2026-11-03-trailer-preview-design.md) | Prévia de trailer (desenho e estado atual) |
| [brand/CHANNEL_FORMAT.md](brand/CHANNEL_FORMAT.md) | Padrão de postagem que o app lê |
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | Política de privacidade |
