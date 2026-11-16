#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/falloc.h>
#include <unistd.h>

/*
 * Libera os blocos de disco de [offset, offset+length) sem mudar o tamanho do arquivo
 * (FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE). A leitura nessa faixa passa a retornar zeros.
 * Retorna 0 em sucesso ou -errno (ex.: -EOPNOTSUPP se o sistema de arquivos não suporta).
 */
JNIEXPORT jint JNICALL
Java_com_ntv2_app_core_player_io_NativeFileIo_punchHole(JNIEnv *env, jobject thiz, jint fd,
                                                       jlong offset, jlong length) {
    (void) env;
    (void) thiz;
    if (fd < 0 || offset < 0 || length <= 0) return -EINVAL;
    // fallocate64: no armeabi-v7a o off_t tem 32 bits. Com fallocate, offsets acima de 2 GB viravam
    // negativos (falha) e acima de 4 GB davam a volta e apagavam o início do arquivo (cabeçalho).
    int result = fallocate64(fd, FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE, (off64_t) offset, (off64_t) length);
    return result == 0 ? 0 : -errno;
}
