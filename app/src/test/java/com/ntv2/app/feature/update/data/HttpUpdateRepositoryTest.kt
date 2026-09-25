package com.ntv2.app.feature.update.data

import com.ntv2.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpUpdateRepositoryTest {
    private val repository = HttpUpdateRepository("https://example.invalid/update.json")

    @Test
    fun `parseia manifesto valido`() {
        val update = repository.parseManifest(validManifest())

        assertEquals(5L, update.versionCode)
        assertEquals("0.4.0", update.versionName)
        assertEquals(2, update.notes.size)
        assertEquals(false, update.required)
    }

    @Test
    fun `recusa aplicativo diferente`() {
        val json = validManifest().replace(BuildConfig.APPLICATION_ID, "com.example.other")
        assertThrows(IllegalArgumentException::class.java) { repository.parseManifest(json) }
    }

    @Test
    fun `recusa download sem https`() {
        val json = validManifest().replace("https://github.com", "http://github.com")
        assertThrows(IllegalArgumentException::class.java) { repository.parseManifest(json) }
    }

    @Test
    fun `recusa hash malformado`() {
        val json = validManifest().replace("a".repeat(64), "abc")
        assertThrows(IllegalArgumentException::class.java) { repository.parseManifest(json) }
    }

    private fun validManifest() = """
        {
          "schemaVersion": 1,
          "applicationId": "${BuildConfig.APPLICATION_ID}",
          "versionCode": 5,
          "versionName": "0.4.0",
          "minimumAndroidSdk": 23,
          "downloadUrl": "https://github.com/Nobrinho/ntv2/releases/download/v0.4.0/ntv-0.4.0.apk",
          "sizeBytes": 12345,
          "sha256": "${"a".repeat(64)}",
          "required": false,
          "minimumSupportedVersionCode": 4,
          "notes": ["Melhoria A", "Correção B"]
        }
    """.trimIndent()
}
