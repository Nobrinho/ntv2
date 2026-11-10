package com.ntv2.app.core.player.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClampDownloadRangeTest {
    @Test fun `dentro do arquivo passa igual`() = assertEquals(10L to 20L, clampDownloadRange(10, 20, 100))
    @Test fun `passa do fim e cortado`() = assertEquals(90L to 10L, clampDownloadRange(90, 50, 100))
    @Test fun `comecando no fim e descartado`() = assertNull(clampDownloadRange(100, 10, 100))
    @Test fun `depois do fim e descartado`() = assertNull(clampDownloadRange(150, 10, 100))
    @Test fun `tamanho invalido e descartado`() { assertNull(clampDownloadRange(0, 0, 100)); assertNull(clampDownloadRange(-1, 5, 100)) }
    @Test fun `tamanho desconhecido nao corta`() = assertEquals(500L to 20L, clampDownloadRange(500, 20, null))
}
