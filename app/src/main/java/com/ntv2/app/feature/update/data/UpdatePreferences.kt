package com.ntv2.app.feature.update.data

import android.content.Context

class UpdatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ntv2_updates", Context.MODE_PRIVATE)

    var lastCheckAt: Long
        get() = prefs.getLong("last_check_at", 0L)
        set(value) { prefs.edit().putLong("last_check_at", value).apply() }

    var ignoredVersionCode: Long
        get() = prefs.getLong("ignored_version_code", 0L)
        set(value) { prefs.edit().putLong("ignored_version_code", value).apply() }

    var pendingDownloadId: Long
        get() = prefs.getLong("pending_download_id", -1L)
        set(value) { prefs.edit().putLong("pending_download_id", value).apply() }

    var pendingVersionCode: Long
        get() = prefs.getLong("pending_version_code", 0L)
        set(value) { prefs.edit().putLong("pending_version_code", value).apply() }

    fun clearDownload() {
        prefs.edit()
            .remove("pending_download_id")
            .remove("pending_version_code")
            .apply()
    }
}
