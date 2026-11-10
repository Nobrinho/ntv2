package com.ntv2.app.core.ui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTypeTest {

    @Test
    fun `sistema de TV e sempre Tv, qualquer tamanho`() {
        assertEquals(DeviceType.Tv, deviceTypeOf(isTvSystem = true, smallestWidthDp = 540))
        assertEquals(DeviceType.Tv, deviceTypeOf(isTvSystem = true, smallestWidthDp = 960))
    }

    @Test
    fun `menor lado a partir de 600 dp e Tablet`() {
        assertEquals(DeviceType.Tablet, deviceTypeOf(isTvSystem = false, smallestWidthDp = 600))
        assertEquals(DeviceType.Tablet, deviceTypeOf(isTvSystem = false, smallestWidthDp = 800)) // Vaio TL10
    }

    @Test
    fun `menor lado abaixo de 600 dp e Phone`() {
        assertEquals(DeviceType.Phone, deviceTypeOf(isTvSystem = false, smallestWidthDp = 360))
        assertEquals(DeviceType.Phone, deviceTypeOf(isTvSystem = false, smallestWidthDp = 599))
    }

    private fun keys(
        type: DeviceType,
        keyboard: Int = Configuration.KEYBOARD_NOKEYS,
        hardHidden: Int = Configuration.HARDKEYBOARDHIDDEN_UNDEFINED,
        navigation: Int = Configuration.NAVIGATION_NONAV,
        navHidden: Int = Configuration.NAVIGATIONHIDDEN_UNDEFINED,
        touch: Int = Configuration.TOUCHSCREEN_FINGER
    ) = hasKeyNavigationOf(type, keyboard, hardHidden, navigation, navHidden, touch)

    @Test
    fun `TV ou aparelho sem toque navega por teclas`() {
        assertTrue(keys(DeviceType.Tv))
        assertTrue(keys(DeviceType.Phone, touch = Configuration.TOUCHSCREEN_NOTOUCH))
    }

    @Test
    fun `tablet so de toque nao navega por teclas`() {
        assertFalse(keys(DeviceType.Tablet))
    }

    @Test
    fun `teclado ou controle fisico conectado habilita`() {
        assertTrue(keys(DeviceType.Tablet, keyboard = Configuration.KEYBOARD_QWERTY, hardHidden = Configuration.HARDKEYBOARDHIDDEN_NO))
        assertTrue(keys(DeviceType.Phone, navigation = Configuration.NAVIGATION_DPAD, navHidden = Configuration.NAVIGATIONHIDDEN_NO))
    }

    @Test
    fun `teclado escondido nao conta`() {
        assertFalse(keys(DeviceType.Tablet, keyboard = Configuration.KEYBOARD_QWERTY, hardHidden = Configuration.HARDKEYBOARDHIDDEN_YES))
    }
}
