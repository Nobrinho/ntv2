# Project-specific ProGuard/R8 rules.

# TDLib: a biblioteca nativa (libtdjni) cria e lê os objetos TdApi por JNI, localizando classes,
# campos e construtores pelo NOME. O app também lê TdApi.GIT_COMMIT_HASH por reflexão.
-keep class org.drinkless.tdlib.** { *; }

# Decoder FFmpeg: o JNI procura FfmpegAudioDecoder.growOutputBuffer pelo nome e o
# DefaultRenderersFactory do media3 carrega os renderers FFmpeg por reflexão.
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# libntv2io: o símbolo JNI depende do nome da classe e do método (punch hole do disco).
-keep class com.ntv2.app.core.player.io.NativeFileIo {
    native <methods>;
}
