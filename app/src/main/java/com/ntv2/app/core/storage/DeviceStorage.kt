package com.ntv2.app.core.storage

import android.os.StatFs
import java.io.File

data class StorageSnapshot(val freeBytes: Long, val totalBytes: Long) {
    val canDownload: Boolean get() = StorageBudget.canDownload(freeBytes, totalBytes)
    val canStartPlayback: Boolean get() = StorageBudget.canStartPlayback(freeBytes, totalBytes)
}

/** Lê o espaço da partição de dados do app (onde o TDLib grava os vídeos). */
fun interface DeviceStorage {
    fun snapshot(): StorageSnapshot?
}

class StatFsDeviceStorage(private val dir: File) : DeviceStorage {
    override fun snapshot(): StorageSnapshot? = runCatching {
        val stat = StatFs(dir.path)
        StorageSnapshot(freeBytes = stat.availableBytes, totalBytes = stat.totalBytes)
    }.getOrNull()
}

/** Sinaliza que o player parou por falta de espaço (a tela mostra uma mensagem específica). */
class LowStorageException(message: String) : java.io.IOException(message)
