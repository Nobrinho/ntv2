package com.ntv2.app.feature.update.data

import android.os.Build
import com.ntv2.app.BuildConfig
import com.ntv2.app.feature.update.domain.AppUpdate
import com.ntv2.app.feature.update.domain.UpdateAvailability
import com.ntv2.app.feature.update.domain.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class HttpUpdateRepository(
    private val manifestUrl: String,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : UpdateRepository {
    override suspend fun check(): UpdateAvailability = withContext(Dispatchers.IO) {
        val url = URL("$manifestUrl?installedVersion=${BuildConfig.VERSION_CODE}")
        require(url.protocol == "https") { "O manifesto de atualização deve usar HTTPS" }
        val connection = connectionFactory(url).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            check(connection.responseCode in 200..299) {
                "Servidor de atualização respondeu ${connection.responseCode}"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val update = parseManifest(body)
            when {
                Build.VERSION.SDK_INT < update.minimumAndroidSdk ->
                    UpdateAvailability.UnsupportedDevice(update.minimumAndroidSdk)
                update.versionCode <= BuildConfig.VERSION_CODE.toLong() -> UpdateAvailability.UpToDate
                else -> UpdateAvailability.Available(
                    update,
                    update.isMandatoryFor(BuildConfig.VERSION_CODE.toLong())
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseManifest(body: String): AppUpdate {
        val json = JSONObject(body)
        require(json.getInt("schemaVersion") == 1) { "Versão do manifesto não suportada" }
        require(json.getString("applicationId") == BuildConfig.APPLICATION_ID) {
            "Manifesto pertence a outro aplicativo"
        }
        val downloadUrl = json.getString("downloadUrl")
        val uri = URI(downloadUrl)
        require(uri.scheme == "https" && uri.host in ALLOWED_DOWNLOAD_HOSTS) {
            "Endereço de download não permitido"
        }
        val sha256 = json.getString("sha256").lowercase()
        require(SHA_256.matches(sha256)) { "SHA-256 inválido" }
        val size = json.getLong("sizeBytes")
        require(size in 1L..MAX_APK_SIZE_BYTES) { "Tamanho do APK inválido" }
        val versionCode = json.getLong("versionCode")
        require(versionCode > 0L) { "Código de versão inválido" }
        val notesJson = json.optJSONArray("notes")
        val notes = buildList {
            if (notesJson != null) for (index in 0 until notesJson.length()) {
                add(notesJson.getString(index))
            }
        }
        return AppUpdate(
            versionCode = versionCode,
            versionName = json.getString("versionName"),
            minimumAndroidSdk = json.optInt("minimumAndroidSdk", 23),
            downloadUrl = downloadUrl,
            sizeBytes = size,
            sha256 = sha256,
            required = json.optBoolean("required", false),
            minimumSupportedVersionCode = json.optLong("minimumSupportedVersionCode", 0L),
            notes = notes
        )
    }

    private companion object {
        val SHA_256 = Regex("^[0-9a-f]{64}$")
        const val MAX_APK_SIZE_BYTES = 2L * 1024L * 1024L * 1024L
        val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "githubusercontent.com"
        )
    }
}
