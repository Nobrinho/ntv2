package com.ntv2.app.feature.update.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.ntv2.app.feature.update.data.ApkDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class InstallLaunchResult { PERMISSION_REQUIRED, INSTALLER_OPENED }

/**
 * Instala a atualização por uma sessão do PackageInstaller. No Android 12+ a sessão pede
 * USER_ACTION_NOT_REQUIRED: como o NTV atualiza a si mesmo, o sistema pode instalar sem a
 * tela de confirmação. Quando o sistema ainda exige confirmação, o resultado chega em
 * [InstallResultReceiver], que abre a tela. Se a sessão falhar, cai no instalador do sistema
 * (ACTION_VIEW), o fluxo antigo.
 */
class AppUpdateInstaller(private val context: Context) {
    suspend fun launch(file: File): InstallLaunchResult {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return InstallLaunchResult.PERMISSION_REQUIRED
        }

        runCatching { withContext(Dispatchers.IO) { installWithSession(file) } }
            .onFailure { error ->
                Log.w(TAG, "Sessão do PackageInstaller falhou; usando o instalador do sistema", error)
                openSystemInstaller(context, file)
            }
        return InstallLaunchResult.INSTALLER_OPENED
    }

    private fun installWithSession(file: File) {
        val installer = context.packageManager.packageInstaller
        // Sessões que ficaram para trás (instalação interrompida) ocupam espaço.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("ntv-update.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(resultSender(sessionId, file).intentSender)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    private fun resultSender(sessionId: Int, file: File): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .putExtra(InstallResultReceiver.EXTRA_APK_PATH, file.absolutePath)
        // O sistema preenche o status no intent, então ele precisa ser mutável (explícito, então é seguro).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    companion object {
        private const val TAG = "AppUpdateInstaller"

        fun openSystemInstaller(context: Context, file: File) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, ApkDownloadManager.APK_MIME_TYPE)
            }
            check(installIntent.resolveActivity(context.packageManager) != null) {
                "Este aparelho não possui um instalador de APK disponível"
            }
            context.startActivity(installIntent)
        }
    }
}
