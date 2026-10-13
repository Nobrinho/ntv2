package com.ntv2.app.core.player.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOwnershipTest {
    @Test
    fun `tela do video ativo pode encerrar a sessao`() {
        assertTrue(SessionOwnership.mayRelease(activeFileId = 10, ownerFileId = 10))
    }

    @Test
    fun `tela antiga nao derruba o episodio que ja comecou`() {
        assertFalse(SessionOwnership.mayRelease(activeFileId = 20, ownerFileId = 10))
    }

    @Test
    fun `sem sessao ativa a liberacao e permitida`() {
        assertTrue(SessionOwnership.mayRelease(activeFileId = null, ownerFileId = 10))
    }

    @Test
    fun `abrir outro video com um ativo e troca`() {
        assertTrue(SessionOwnership.isSwitch(activeFileId = 10, nextFileId = 20))
    }

    @Test
    fun `nova tentativa do mesmo arquivo nao e troca`() {
        assertFalse(SessionOwnership.isSwitch(activeFileId = 10, nextFileId = 10))
    }

    @Test
    fun `sem video ativo nao ha troca`() {
        assertFalse(SessionOwnership.isSwitch(activeFileId = null, nextFileId = 10))
    }
}
