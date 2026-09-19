package com.ntv2.app.core.player.io

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/** Libera blocos de disco no meio de um arquivo (punch hole). Ver libntv2io (src/main/cpp). */
interface HolePuncher {
    /** true se liberou [offset, offset+length) no disco. */
    fun punch(path: String, offset: Long, length: Long): Boolean
}

internal object NativeFileIo : HolePuncher {
    private const val TAG = "NtvDiskWindow"

    private val available: Boolean = runCatching { System.loadLibrary("ntv2io") }
        .onFailure { Log.e(TAG, "libntv2io indisponível: janela deslizante desativada", it) }
        .isSuccess

    @Volatile
    private var unsupported = false

    @JvmStatic
    private external fun punchHole(fd: Int, offset: Long, length: Long): Int

    override fun punch(path: String, offset: Long, length: Long): Boolean {
        if (!available || unsupported || length <= 0L) return false
        return runCatching {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                val result = punchHole(pfd.fd, offset, length)
                if (result != 0) {
                    Log.e(TAG, "punch hole falhou (errno=${-result}) em $path")
                    // EOPNOTSUPP(95)/ENOSYS(38): o sistema de arquivos não suporta; não tenta de novo.
                    if (-result == 95 || -result == 38) unsupported = true
                }
                result == 0
            }
        }.getOrElse {
            Log.e(TAG, "punch hole falhou em $path", it)
            false
        }
    }
}
