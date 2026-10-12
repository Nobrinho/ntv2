package com.ntv2.app.feature.update.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerifierTest {
    @Test
    fun `mesma assinatura e compativel`() {
        assertTrue(signaturesCompatible(setOf("aa"), setOf("aa")))
    }

    @Test
    fun `rotacao de chave com historico em comum e compativel`() {
        assertTrue(signaturesCompatible(setOf("aa", "bb"), setOf("bb")))
    }

    @Test
    fun `assinaturas diferentes divergem`() {
        assertFalse(signaturesCompatible(setOf("aa"), setOf("bb")))
    }

    @Test
    fun `certificado ilegivel no apk baixado nao acusa divergencia`() {
        assertTrue(signaturesCompatible(emptySet(), setOf("aa")))
    }

    @Test
    fun `certificado ilegivel no app instalado nao acusa divergencia`() {
        assertTrue(signaturesCompatible(setOf("aa"), emptySet()))
    }
}
