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
    int result = fallocate(fd, FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE, (off_t) offset, (off_t) length);
    return result == 0 ? 0 : -errno;
}
