package com.ntv2.app.feature.update.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.ntv2.app.feature.update.domain.AppUpdate
import java.io.File

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data object Waiting : ApkDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ApkDownloadState
    data class Complete(val file: File) : ApkDownloadState
    data class Failed(val reason: String) : ApkDownloadState
}

class ApkDownloadManager(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun start(update: AppUpdate): Long {
        val parent = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val required = update.sizeBytes * 2L + 100L * 1024L * 1024L
        require(StatFs(parent.absolutePath).availableBytes >= required) {
            "Espaço insuficiente para baixar e instalar a atualização"
        }
        updateDirectory().listFiles()?.forEach { old ->
            if (old.name != fileName(update.versionCode)) old.delete()
        }
        fileFor(update.versionCode).delete()
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Atualização do NTV ${update.versionName}")
            .setDescription("Baixando atualização")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${fileName(update.versionCode)}"
            )
        return manager.enqueue(request)
    }

    fun status(downloadId: Long, versionCode: Long): ApkDownloadState {
        if (downloadId < 0L) return ApkDownloadState.Idle
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return ApkDownloadState.Failed("Download não encontrado")
            return when (it.int(DownloadManager.COLUMN_STATUS)) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> ApkDownloadState.Waiting
                DownloadManager.STATUS_RUNNING -> ApkDownloadState.Downloading(
                    it.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    it.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = File(updateDirectory(), fileName(versionCode))
                    if (file.isFile) ApkDownloadState.Complete(file)
                    else ApkDownloadState.Failed("O arquivo baixado não foi encontrado")
                }
                else -> ApkDownloadState.Failed("Falha no download (código ${it.int(DownloadManager.COLUMN_REASON)})")
            }
        }
    }

    fun cancel(downloadId: Long) {
        if (downloadId >= 0L) manager.remove(downloadId)
    }

    fun fileFor(versionCode: Long): File = File(updateDirectory(), fileName(versionCode))

    private fun updateDirectory(): File =
        File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "updates")
            .apply { mkdirs() }

    private fun fileName(versionCode: Long) = "ntv-update-$versionCode.apk"

    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
