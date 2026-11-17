package com.ntv2.app.feature.settings.presentation

import com.ntv2.app.core.backend.link.DeviceLinkRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeKeypadLogicTest {

    @Test
    fun `as teclas cobrem o alfabeto do servidor sem repetir`() {
        val keys = CodeKeypadLogic.ROWS.joinToString("")
        assertEquals(DeviceLinkRepository.ALPHABET, keys)
        assertTrue(CodeKeypadLogic.ROWS.all { it.length <= 8 })
    }

    @Test
    fun `acrescenta ate seis caracteres e para`() {
        var code = ""
        "ABC234XYZ".forEach { code = CodeKeypadLogic.append(code, it) }
        assertEquals("ABC234", code)
        assertEquals(DeviceLinkRepository.CODE_LENGTH, code.length)
    }

    @Test
    fun `apagar tira o ultimo e nao quebra no vazio`() {
        assertEquals("AB", CodeKeypadLogic.backspace("ABC"))
        assertEquals("", CodeKeypadLogic.backspace("A"))
        assertEquals("", CodeKeypadLogic.backspace(""))
    }
}
