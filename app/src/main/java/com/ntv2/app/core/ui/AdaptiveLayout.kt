package com.ntv2.app.core.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AdaptiveLayoutInfo(
    val widthDp: Int,
    val heightDp: Int,
    val isTv: Boolean,
    val isPortrait: Boolean
) {
    val isCompact: Boolean get() = widthDp < 600
    val isExpanded: Boolean get() = widthDp >= 840
    val useTvLayout: Boolean get() = isTv || (isExpanded && !isPortrait)
    val usePhoneLayout: Boolean get() = !useTvLayout && isCompact

    /** Área segura da TV (overscan): 5% da largura nas laterais e 5% da altura em cima e embaixo. */
    val tvSafeHorizontal: Dp get() = (widthDp * 0.05f).dp
    val tvSafeVertical: Dp get() = (heightDp * 0.05f).dp
}

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isTv = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || hasLeanback
    return AdaptiveLayoutInfo(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
        isTv = isTv,
        isPortrait = configuration.screenHeightDp >= configuration.screenWidthDp
    )
}
