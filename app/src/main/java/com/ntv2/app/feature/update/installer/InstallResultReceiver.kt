package com.ntv2.app.feature.update.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File

/** Recebe o resultado da sessão aberta por [AppUpdateInstaller]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.confirmationIntent() ?: return fallback(context, intent, status)
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { fallback(context, intent, status) }
            }
            // Sucesso: o processo é substituído pela versão nova. Cancelado: o usuário recusou.
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> fallback(context, intent, status)
        }
    }

    private fun fallback(context: Context, intent: Intent, status: Int) {
        Log.w(
            TAG,
            "Instalação por sessão falhou (status $status): " +
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        )
        val file = intent.getStringExtra(EXTRA_APK_PATH)?.let(::File)?.takeIf { it.isFile } ?: return
        runCatching { AppUpdateInstaller.openSystemInstaller(context, file) }
            .onFailure { Log.w(TAG, "Instalador do sistema indisponível", it) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.confirmationIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else getParcelableExtra(Intent.EXTRA_INTENT)

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val EXTRA_APK_PATH = "com.ntv2.app.extra.APK_PATH"
    }
}
