package com.ntv2.app.core.telegram.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdlibKeyPlanTest {

    @Test
    fun `instalacao nova cria o banco ja cifrado`() {
        assertEquals(listOf(TdlibKeySource.NEW), TdlibKeyPlan.attempts(false, false, databaseExists = false))
    }

    @Test
    fun `chave ativa e usada sozinha`() {
        assertEquals(listOf(TdlibKeySource.ACTIVE), TdlibKeyPlan.attempts(true, false, databaseExists = true))
        // Depois de sair da conta o TDLib apaga o banco: a mesma chave cria o próximo.
        assertEquals(listOf(TdlibKeySource.ACTIVE), TdlibKeyPlan.attempts(true, false, databaseExists = false))
    }

    @Test
    fun `banco antigo em claro abre sem chave e e cifrado em seguida`() {
        assertEquals(listOf(TdlibKeySource.LEGACY_EMPTY), TdlibKeyPlan.attempts(false, false, databaseExists = true))
        assertTrue(TdlibKeyPlan.needsEncryption(TdlibKeySource.LEGACY_EMPTY))
    }

    @Test
    fun `migracao interrompida tenta a pendente e depois sem chave`() {
        assertEquals(
            listOf(TdlibKeySource.PENDING, TdlibKeySource.LEGACY_EMPTY),
            TdlibKeyPlan.attempts(false, true, databaseExists = true)
        )
        assertFalse(TdlibKeyPlan.needsEncryption(TdlibKeySource.PENDING))
    }

    @Test
    fun `chave perdida com banco existente tenta sem chave antes de recriar`() {
        // Keystore sem a chave (ex.: restaurado em outro aparelho): só resta tentar em claro; se der
        // 401, o gateway recria o banco.
        assertEquals(listOf(TdlibKeySource.LEGACY_EMPTY), TdlibKeyPlan.attempts(false, false, databaseExists = true))
    }

    @Test
    fun `so o banco em claro precisa ser cifrado`() {
        assertFalse(TdlibKeyPlan.needsEncryption(TdlibKeySource.ACTIVE))
        assertFalse(TdlibKeyPlan.needsEncryption(TdlibKeySource.NEW))
    }
}
