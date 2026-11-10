package com.ntv2.app.core.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * O que o aparelho É (não muda ao girar a tela). Para decidir o LAYOUT use [AdaptiveLayoutInfo.useTvLayout]
 * / [AdaptiveLayoutInfo.usePhoneLayout], que dependem do tamanho/orientação atuais: um tablet deitado usa
 * o layout de TV, mas continua sendo [Tablet] (tem toque, login por telefone, PiP, Cast...).
 */
enum class DeviceType {
    /** Celular: menor lado da tela < 600 dp. */
    Phone,
    /** Tablet: menor lado da tela >= 600 dp, sem ser TV. */
    Tablet,
    /** TV / Fire TV / Android TV: sistema em modo TV ou com suporte a apps de TV (leanback). */
    Tv
}

@Immutable
data class AdaptiveLayoutInfo(
    val widthDp: Int,
    val heightDp: Int,
    val deviceType: DeviceType,
    val isPortrait: Boolean,
    /**
     * Há como navegar por teclas: TV (controle remoto) ou teclado/controle físico conectado (ex.: tablet com
     * controle USB). Use para decisões de foco, não de layout.
     */
    val hasKeyNavigation: Boolean = deviceType == DeviceType.Tv
) {
    val isTv: Boolean get() = deviceType == DeviceType.Tv
    val isTablet: Boolean get() = deviceType == DeviceType.Tablet
    val isPhone: Boolean get() = deviceType == DeviceType.Phone

    val isCompact: Boolean get() = widthDp < 600
    val isExpanded: Boolean get() = widthDp >= 840
    val useTvLayout: Boolean get() = isTv || (isExpanded && !isPortrait)
    val usePhoneLayout: Boolean get() = !useTvLayout && isCompact

    /** Área segura da TV (overscan): 5% da largura nas laterais e 5% da altura em cima e embaixo. */
    val tvSafeHorizontal: Dp get() = (widthDp * 0.05f).dp
    val tvSafeVertical: Dp get() = (heightDp * 0.05f).dp
}

/** Regra pura (testável) do tipo de aparelho. */
internal fun deviceTypeOf(isTvSystem: Boolean, smallestWidthDp: Int): DeviceType = when {
    isTvSystem -> DeviceType.Tv
    smallestWidthDp >= 600 -> DeviceType.Tablet
    else -> DeviceType.Phone
}

/** Regra pura (testável): há navegação por teclas físicas? */
internal fun hasKeyNavigationOf(
    deviceType: DeviceType,
    keyboard: Int,
    hardKeyboardHidden: Int,
    navigation: Int,
    navigationHidden: Int,
    touchscreen: Int
): Boolean {
    if (deviceType == DeviceType.Tv || touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) return true
    val keys = keyboard != Configuration.KEYBOARD_NOKEYS && hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    val dpad = (navigation == Configuration.NAVIGATION_DPAD || navigation == Configuration.NAVIGATION_TRACKBALL) &&
        navigationHidden != Configuration.NAVIGATIONHIDDEN_YES
    return keys || dpad
}

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val hasLeanback = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val deviceType = deviceTypeOf(
        isTvSystem = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || hasLeanback,
        smallestWidthDp = configuration.smallestScreenWidthDp
    )
    return AdaptiveLayoutInfo(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
        deviceType = deviceType,
        isPortrait = configuration.screenHeightDp >= configuration.screenWidthDp,
        hasKeyNavigation = hasKeyNavigationOf(
            deviceType = deviceType,
            keyboard = configuration.keyboard,
            hardKeyboardHidden = configuration.hardKeyboardHidden,
            navigation = configuration.navigation,
            navigationHidden = configuration.navigationHidden,
            touchscreen = configuration.touchscreen
        )
    )
}
