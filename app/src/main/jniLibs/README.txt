Place native TDLib binaries here:
- app/src/main/jniLibs/arm64-v8a/libtdjni.so
- app/src/main/jniLibs/armeabi-v7a/libtdjni.so

Alternative name supported by runtime loader:
- libtdjsonjava.so

When telegramApiId/telegramApiHash are set, the Gradle task verifyTdlibNativeLibs
requires at least one of those .so files per ABI.
