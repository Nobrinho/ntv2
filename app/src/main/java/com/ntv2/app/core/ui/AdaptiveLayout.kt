package com.ntv2.app.core.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Immutable
data class AdaptiveLayoutInfo(
    val widthDp: Int,
    val heightDp: Int,
    val isTv: Boolean,
    val isPortrait: Boolean
) {
    val isCompact: Boolean get() = widthDp < 600
    val isMedium: Boolean get() = widthDp in 600 until 840
    val isExpanded: Boolean get() = widthDp >= 840
    val useTvLayout: Boolean get() = isTv || (isExpanded && !isPortrait)
    val usePhoneLayout: Boolean get() = !useTvLayout && isCompact
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
