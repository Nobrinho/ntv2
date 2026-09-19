package com.ntv2.app.core.storage

import android.os.SystemClock
import android.util.Log
import com.ntv2.app.core.telegram.media.StorageFileKind
import com.ntv2.app.core.telegram.media.TdlibStorageGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Checagem de espaço usada pelo player (antes de tocar e quando o disco aperta durante o filme). */
interface PlaybackStorageGuard {
    /** Garante espaço para iniciar; se faltar, limpa o que puder. Retorna o estado final (null = desconhecido). */
    suspend fun ensureSpaceForPlayback(): StorageSnapshot?

    /** O player parou de baixar por falta de espaço: libera caches (não toca no vídeo ativo). */
    fun onLowStorageDuringPlayback()
}

/**
 * Limpeza automática do armazenamento (Camada B do plano de memória):
 *  - na abertura: apaga vídeos órfãos de sessões anteriores e aplica tetos (vídeos → 0, imagens → 150 MB);
 *  - periodicamente: mantém o teto de imagens;
 *  - em emergência (disco perto do limiar do sistema): imagens → 40 MB e limpa o cache do Coil.
 */
class StorageJanitor(
    private val storageGateway: TdlibStorageGateway,
    private val pending: PendingFileDeletions,
    private val deviceStorage: DeviceStorage,
    private val clearImageDiskCache: () -> Unit,
    private val tdlibFilesDir: File?,
    private val verboseLogging: Boolean
) : PlaybackStorageGuard {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile
    private var lastEmergencyAt = 0L

    fun start() {
        scope.launch {
            storageGateway.awaitStorageReady()
            runCatching { sweepOnStartup() }.onFailure { Log.e(TAG, "limpeza inicial falhou", it) }
            while (true) {
                delay(PERIODIC_TRIM_MS)
                runCatching {
                    mutex.withLock {
                        storageGateway.optimizeStorage(setOf(StorageFileKind.IMAGE), StorageBudget.IMAGE_CACHE_MAX_BYTES, 0)
                    }
                }
            }
        }
    }

    private suspend fun sweepOnStartup() = mutex.withLock {
        val before = deviceStorage.snapshot()
        val orphans = pending.orphans().filterNot(pending::isActiveInThisSession)
        orphans.forEach { fileId ->
            runCatching { storageGateway.deleteStoredFile(fileId) }
            pending.markDeleted(fileId)
        }
        // Vídeos antigos (inclusive órfãos de antes deste registro existir). A imunidade protege
        // um vídeo que o usuário tenha aberto enquanto a limpeza rodava.
        val videos = storageGateway.optimizeStorage(setOf(StorageFileKind.VIDEO), 0L, VIDEO_IMMUNITY_SECONDS)
        val images = storageGateway.optimizeStorage(setOf(StorageFileKind.IMAGE), StorageBudget.IMAGE_CACHE_MAX_BYTES, 0)
        val after = deviceStorage.snapshot()
        Log.i(
            TAG,
            "limpeza inicial: órfãos=${orphans.size} vídeos=${mb(videos)} imagens=${mb(images)} " +
                "livre ${mb(before?.freeBytes)} → ${mb(after?.freeBytes)} de ${mb(after?.totalBytes)}" +
                tdlibUsage()
        )
    }

    override suspend fun ensureSpaceForPlayback(): StorageSnapshot? {
        val snapshot = deviceStorage.snapshot() ?: return null
        if (snapshot.canStartPlayback) return snapshot
        emergencyTrim()
        return deviceStorage.snapshot()
    }

    override fun onLowStorageDuringPlayback() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmergencyAt < EMERGENCY_COOLDOWN_MS) return
        lastEmergencyAt = now
        scope.launch { runCatching { emergencyTrim() } }
    }

    private suspend fun emergencyTrim() = mutex.withLock {
        val before = deviceStorage.snapshot()
        runCatching { clearImageDiskCache() }
        val images = storageGateway.optimizeStorage(
            setOf(StorageFileKind.IMAGE),
            StorageBudget.IMAGE_CACHE_EMERGENCY_BYTES,
            0
        )
        val after = deviceStorage.snapshot()
        Log.i(
            TAG,
            "limpeza de emergência: imagens=${mb(images)} livre ${mb(before?.freeBytes)} → ${mb(after?.freeBytes)}" +
                tdlibUsage()
        )
    }

    /** Tamanho das pastas do TDLib (só em debug: percorrer a árvore custa I/O). */
    private fun tdlibUsage(): String {
        if (!verboseLogging) return ""
        val dir = tdlibFilesDir ?: return ""
        val parts = dir.listFiles()?.filter { it.isDirectory }?.map { sub ->
            "${sub.name}=${mb(sub.walkTopDown().filter { it.isFile }.sumOf { it.length() })}"
        }.orEmpty()
        return " | tdlib: " + parts.joinToString(" ")
    }

    private fun mb(bytes: Long?): String = if (bytes == null) "?" else "${bytes / (1024L * 1024L)}MB"

    private companion object {
        const val TAG = "NtvStorage"
        const val VIDEO_IMMUNITY_SECONDS = 10 * 60
        const val PERIODIC_TRIM_MS = 6L * 60L * 60L * 1000L
        const val EMERGENCY_COOLDOWN_MS = 60_000L
    }
}
