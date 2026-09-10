package com.ntv2.app.core.player.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaybackCacheManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun writeFile(dir: File, name: String, bytes: Int, lastModified: Long): File {
        val f = File(dir, name)
        f.writeBytes(ByteArray(bytes))
        f.setLastModified(lastModified)
        return f
    }

    @Test
    fun `apara os mais antigos ate ficar abaixo do alvo e preserva o ativo`() {
        val dir = temp.newFolder("cache")
        val manager = PlaybackCacheManager(
            cacheDir = dir,
            maxBytes = 1_000,
            trimTargetBytes = 600,
            maxFiles = 10
        )
        val old = writeFile(dir, "old.partial", 500, 1_000L)
        val mid = writeFile(dir, "mid.partial", 500, 2_000L)
        val active = writeFile(dir, "active.partial", 500, 3_000L)
        manager.markActivePlaybackFile(active)

        manager.trimIfNeeded()

        assertFalse("mais antigo deve ser removido", old.exists())
        assertFalse("segundo mais antigo removido para chegar ao alvo", mid.exists())
        assertTrue("arquivo ativo nunca e removido", active.exists())
    }

    @Test
    fun `nao apara quando esta dentro dos limites`() {
        val dir = temp.newFolder("cache")
        val manager = PlaybackCacheManager(
            cacheDir = dir,
            maxBytes = 10_000,
            trimTargetBytes = 8_000,
            maxFiles = 10
        )
        val f = writeFile(dir, "a.partial", 500, 1_000L)

        manager.trimIfNeeded()

        assertTrue(f.exists())
    }

    @Test
    fun `apara por excesso de arquivos mesmo dentro do limite de bytes`() {
        val dir = temp.newFolder("cache")
        val manager = PlaybackCacheManager(
            cacheDir = dir,
            maxBytes = 1_000_000,
            trimTargetBytes = 1_000_000,
            maxFiles = 2
        )
        val f1 = writeFile(dir, "f1.partial", 10, 1_000L)
        val f2 = writeFile(dir, "f2.partial", 10, 2_000L)
        val f3 = writeFile(dir, "f3.partial", 10, 3_000L)

        manager.trimIfNeeded()

        assertFalse("excedente mais antigo removido", f1.exists())
        assertTrue(f2.exists())
        assertTrue(f3.exists())
    }
}
