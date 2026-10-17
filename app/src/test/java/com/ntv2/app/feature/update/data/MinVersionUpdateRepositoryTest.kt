package com.ntv2.app.feature.update.data

import com.ntv2.app.feature.update.domain.AppUpdate
import com.ntv2.app.feature.update.domain.UpdateAvailability
import com.ntv2.app.feature.update.domain.UpdateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinVersionUpdateRepositoryTest {

    private val update = AppUpdate(
        versionCode = 31, versionName = "0.4.25", minimumAndroidSdk = 21,
        downloadUrl = "https://x/app.apk", sizeBytes = 1, sha256 = "a", required = false,
        minimumSupportedVersionCode = 0, notes = emptyList()
    )

    private fun repo(result: UpdateAvailability, minimum: Long, installed: Long = 28) =
        MinVersionUpdateRepository(
            delegate = object : UpdateRepository {
                override suspend fun check() = result
            },
            minVersionCode = { minimum },
            installedVersionCode = installed
        )

    @Test
    fun `abaixo do min_version_code a atualizacao vira obrigatoria`() = runTest {
        val r = repo(UpdateAvailability.Available(update, mandatory = false), minimum = 29).check()
        assertTrue((r as UpdateAvailability.Available).mandatory)
    }

    @Test
    fun `no minimo ou sem config nada muda`() = runTest {
        val atMin = repo(UpdateAvailability.Available(update, false), minimum = 28).check()
        assertFalse((atMin as UpdateAvailability.Available).mandatory)
        val noConfig = repo(UpdateAvailability.Available(update, false), minimum = 0).check()
        assertFalse((noConfig as UpdateAvailability.Available).mandatory)
        assertEquals(UpdateAvailability.UpToDate, repo(UpdateAvailability.UpToDate, minimum = 99).check())
    }
}
