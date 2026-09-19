cmake_minimum_required(VERSION 3.22.1)
project(ntv2io C)

# Utilitários de E/S de arquivo do player (libera no disco o trecho já assistido do vídeo).
add_library(ntv2io SHARED ntv2_fileio.c)
target_link_libraries(ntv2io log)
# Páginas de 16 KB (Android 15+): o NDK r26 alinha a 4 KB por padrão.
target_link_options(ntv2io PRIVATE "-Wl,-z,max-page-size=16384")
