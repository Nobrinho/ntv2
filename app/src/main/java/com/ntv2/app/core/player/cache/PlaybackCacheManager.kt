package com.ntv2.app.core.player.cache

import java.io.File

class PlaybackCacheManager(
    private val cacheDir: File,
    private val maxBytes: Long,
    private val trimTargetBytes: Long,
    private val maxFiles: Int
) {
    @Volatile
    private var activePlaybackPath: String? = null

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun resolvePlaybackFile(fileId: Int): File {
        return File(cacheDir, "tg_video_$fileId.partial")
    }

    fun touch(file: File) {
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
        }
    }

    fun markActivePlaybackFile(file: File?) {
        activePlaybackPath = file?.absolutePath
    }

    fun trimIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
        val totalBytes = files.sumOf { it.length() }
        if (totalBytes <= maxBytes && files.size <= maxFiles) {
            return
        }

        val sorted = files.sortedBy { it.lastModified() }
        var runningBytes = totalBytes
        var runningCount = files.size
        val activePath = activePlaybackPath
        for (file in sorted) {
            if (runningBytes <= trimTargetBytes && runningCount <= maxFiles) {
                break
            }
            if (activePath != null && file.absolutePath == activePath) {
                continue
            }
            val size = file.length()
            if (file.delete()) {
                runningBytes -= size
                runningCount -= 1
            }
        }
    }
}
