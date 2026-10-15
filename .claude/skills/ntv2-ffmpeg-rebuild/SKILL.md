---
name: ntv2-ffmpeg-rebuild
description: Recompila as libs estáticas do FFmpeg do módulo :ffmpeg-decoder (áudio por software AC3/EAC3/DTS/TrueHD) no WSL com NDK r26b, ou adiciona novos codecs. Use quando precisar de um decoder novo ou atualizar o FFmpeg.
---

# Recompilar o FFmpeg do `:ffmpeg-decoder`

O módulo empacota o `FfmpegAudioRenderer` do Media3 (tag **1.5.1**) + FFmpeg **6.0** para arm64-v8a, armeabi-v7a, x86 e x86_64. Decoders atuais: `ac3 eac3 dca mlp truehd`. Hardware primeiro; FFmpeg é só fallback por faixa/aparelho.

A árvore (headers + `.a`) em `ffmpeg-decoder/src/main/jni/ffmpeg/android-libs/*` **é versionada**: só refaça o build para adicionar codecs ou mudar a versão. Para compilar o app, nada disso é necessário.

## Build das libs (WSL Ubuntu-24.04, sem sudo)

1. NDK **r26b** extraído com `unzip` de verdade (ou extrator Python que preserve symlinks e `chmod`). `python -m zipfile` quebra symlinks (`clang` para `clang-17`) e bits de execução, e dá "Permission denied"/"clang-17 not found".
2. FFmpeg em `release/6.0`; script `libraries/decoder_ffmpeg/src/main/jni/build_ffmpeg.sh` da tag 1.5.1 do Media3.
3. **Patch obrigatório:** adicionar `--disable-x86asm` ao `COMMON_OPTIONS` (evita nasm no x86; ARM mantém NEON). O `make` vem do NDK (`$NDK/prebuilt/linux-x86_64/bin`).
4. Rodar (faz as 4 ABIs de uma vez; 23 = minSdk):
   ```
   build_ffmpeg.sh <FFMPEG_MODULE_PATH=.../src/main> <NDK> linux-x86_64 23 <decoders...>
   ```
5. Substituir `ffmpeg-decoder/src/main/jni/ffmpeg/android-libs/*` pelos novos `.a` e headers.

## Ambiente nativo no Windows (Gradle compila o JNI)

NDK `26.1.10909125` + CMake `3.22.1`, instalados pelo cmdline-tools **novo** (o antigo dá "SDK XML version 4"). JDK: `C:\Program Files\Android\Android Studio1\jbr`.

## Validação

- `./gradlew :ffmpeg-decoder:assembleDebug` e depois `:app:compileDebugKotlin`.
- Teste de reprodução com faixa do codec novo em aparelho real (o emulador não serve para decodificação pesada de mídia).
- Não gere/instale release sem pedido do usuário. Avise quanto o APK cresceu com as libs novas.
