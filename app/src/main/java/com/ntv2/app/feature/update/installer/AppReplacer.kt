package com.ntv2.app.feature.update.installer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ntv2.app.feature.update.data.ApkDownloadManager
import java.io.File

/**
 * Fluxo para trocar um NTV assinado por outra chave (ex.: debug) pela versão oficial.
 * O Android não permite atualizar por cima, então o APK é copiado para a pasta pública
 * Downloads (a pasta do app some junto com a desinstalação) e o diálogo de desinstalação
 * do sistema é aberto. Depois o usuário abre o arquivo salvo para instalar.
 */
class AppReplacer(private val context: Context) {
    /** Copia o APK para Downloads e devolve o nome do arquivo salvo. */
    fun exportToDownloads(file: File, versionName: String): String {
        val name = "NTV-$versionName.apk"
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            resolver.query(
                collection, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(name), null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    runCatching {
                        resolver.delete(Uri.withAppendedPath(collection, cursor.getLong(0).toString()), null, null)
                    }
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, ApkDownloadManager.APK_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val target = requireNotNull(resolver.insert(collection, values)) {
                "Não foi possível salvar o APK em Downloads"
            }
            resolver.openOutputStream(target).use { output ->
                requireNotNull(output) { "Não foi possível salvar o APK em Downloads" }
                file.inputStream().use { it.copyTo(output) }
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            check(dir.isDirectory || dir.mkdirs()) { "Não foi possível salvar o APK em Downloads" }
            file.copyTo(File(dir, name), overwrite = true)
        }
        return name
    }

    fun requestUninstall() {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
