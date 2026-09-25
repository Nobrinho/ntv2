package com.ntv2.app.feature.update.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ntv2.app.feature.update.data.ApkDownloadManager
import java.io.File

enum class InstallLaunchResult { PERMISSION_REQUIRED, INSTALLER_OPENED }

class AppUpdateInstaller(private val context: Context) {
    fun launch(file: File): InstallLaunchResult {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return InstallLaunchResult.PERMISSION_REQUIRED
        }

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
        return InstallLaunchResult.INSTALLER_OPENED
    }
}
