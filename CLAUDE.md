# NTV (ntv2) — instruções para o Claude

## Documentação de bibliotecas: usar o Context7 na versão do projeto

Antes de escrever ou sugerir código que use a API de uma biblioteca externa (Media3/ExoPlayer, Compose,
Compose for TV, Room, DataStore, Coil, OkHttp, Navigation, Cast, Coroutines, supabase-js do site etc.),
consulte a documentação pelo Context7 **na versão que o projeto usa**, não na mais recente.

1. Pegue a versão em `gradle/libs.versions.toml` (ou no `build.gradle.kts` do módulo; o site usa o
   supabase-js fixado na URL do jsDelivr em `site/admin/`).
2. Resolva o ID da biblioteca (`resolve-library-id`) e consulte com a versão fixada:
   `query-docs` com `/org/projeto/versão` (ex.: `/androidx/media/1.5.1`). Se a versão exata não
   estiver indexada, use a mais próxima abaixo dela e avise.
3. Não use APIs que só existem em versões mais novas que a do projeto (ex.: Coil 3 quando o app usa
   Coil 2.x). Se a solução exigir subir uma versão, proponha a atualização em vez de assumir.

Quando o Context7 não se aplica (a fonte de verdade é o código local):
- TDLib: `app/src/main/java/org/drinkless/tdlib/TdApi.java`.
- Módulo `:ffmpeg-decoder` (FFmpeg 6.0 compilado à mão) e `libntv2io` (`app/src/main/cpp`).
- Comportamento específico do Fire TV: logs reais do aparelho.
