package com.ntv2.app.feature.update.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.ntv2.app.BuildConfig
import com.ntv2.app.feature.update.domain.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** O APK baixado foi assinado por outra chave que a do app instalado (ex.: build de debug). */
class SignatureMismatchException : IllegalArgumentException(
    "O APK não foi assinado pela chave oficial do NTV"
)

class ApkVerifier(private val context: Context) {
    suspend fun verify(file: File, update: AppUpdate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.isFile) { "APK não encontrado" }
            require(file.length() == update.sizeBytes) { "O tamanho do APK não corresponde à publicação" }
            require(file.sha256().equals(update.sha256, ignoreCase = true)) { "SHA-256 do APK inválido" }

            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)) {
                "O arquivo baixado não é um APK válido"
            }
            require(archive.packageName == BuildConfig.APPLICATION_ID) { "O APK pertence a outro aplicativo" }
            require(PackageInfoCompat.getLongVersionCode(archive) == update.versionCode) {
                "A versão interna do APK não corresponde à publicação"
            }
            require(update.versionCode > BuildConfig.VERSION_CODE.toLong()) { "A atualização não é mais nova" }

            val installed = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
            if (archive.signingDigests().intersect(installed.signingDigests()).isEmpty()) {
                throw SignatureMismatchException()
            }
        }
    }

    private fun PackageInfo.signingDigests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val info = signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners.toList()
            else info.signingCertificateHistory.toList()
        } else {
            @Suppress("DEPRECATION")
            signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
