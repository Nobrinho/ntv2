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
            var archiveDigests = archive.signingDigests()
            var installedDigests = installed.signingDigests()
            if (archiveDigests.isEmpty() || installedDigests.isEmpty()) {
                // Em alguns aparelhos (ex.: Android 9) o signingInfo de um APK ainda não instalado vem
                // vazio; GET_SIGNATURES funciona em todas as versões.
                @Suppress("DEPRECATION")
                val legacy = PackageManager.GET_SIGNATURES
                context.packageManager.getPackageArchiveInfo(file.absolutePath, legacy)?.let {
                    archiveDigests = it.signingDigests(useLegacy = true)
                }
                installedDigests = context.packageManager
                    .getPackageInfo(BuildConfig.APPLICATION_ID, legacy)
                    .signingDigests(useLegacy = true)
            }
            when (signatureDecision(archiveDigests, installedDigests)) {
                SignatureDecision.UPDATE -> Unit
                SignatureDecision.SWITCH_TO_OFFICIAL -> throw SignatureMismatchException()
                SignatureDecision.REJECT -> throw IllegalArgumentException("O APK não foi assinado pela chave oficial do NTV")
            }
        }
    }

    private fun PackageInfo.signingDigests(useLegacy: Boolean = false): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28 && !useLegacy) {
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

/**
 * Só há divergência quando os dois lados têm certificado e nenhum coincide. Se algum lado não pôde
 * ser lido, não acusa: o instalador do sistema rejeita uma assinatura realmente diferente.
 */
internal fun signaturesCompatible(archive: Set<String>, installed: Set<String>): Boolean =
    archive.isEmpty() || installed.isEmpty() || archive.intersect(installed).isNotEmpty()

/**
 * SHA-256 do certificado da chave de release oficial (`apksigner verify --print-certs`). Fixo no app:
 * o SHA-256 do APK vem do mesmo `update.json`, então não prova sozinho quem assinou.
 */
internal const val OFFICIAL_CERT_SHA256 = "afbfff51820312fa105e93955cf69d3b6e2fbfb8a83ec34c9e4e021b13beac18"

internal enum class SignatureDecision {
    /** Mesma chave do app instalado: o sistema atualiza por cima. */
    UPDATE,
    /** App instalado com outra chave (ex.: debug) e o APK é o oficial: oferece desinstalar e trocar. */
    SWITCH_TO_OFFICIAL,
    /** Chave diferente e não oficial: recusa (quem controlasse o manifesto trocaria o app por outro). */
    REJECT
}

internal fun signatureDecision(
    archive: Set<String>,
    installed: Set<String>,
    official: String = OFFICIAL_CERT_SHA256
): SignatureDecision = when {
    signaturesCompatible(archive, installed) -> SignatureDecision.UPDATE
    official.lowercase() in archive -> SignatureDecision.SWITCH_TO_OFFICIAL
    else -> SignatureDecision.REJECT
}
