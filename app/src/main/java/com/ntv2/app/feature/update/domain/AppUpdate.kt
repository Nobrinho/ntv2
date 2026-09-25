package com.ntv2.app.feature.update.domain

data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val minimumAndroidSdk: Int,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val required: Boolean,
    val minimumSupportedVersionCode: Long,
    val notes: List<String>
) {
    fun isMandatoryFor(installedVersionCode: Long): Boolean =
        required || installedVersionCode < minimumSupportedVersionCode
}

sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability
    data class Available(val update: AppUpdate, val mandatory: Boolean) : UpdateAvailability
    data class UnsupportedDevice(val minimumAndroidSdk: Int) : UpdateAvailability
}

interface UpdateRepository {
    suspend fun check(): UpdateAvailability
}
