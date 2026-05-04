package com.ntv2.app

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import com.ntv2.app.core.navigation.AppNavHost
import com.ntv2.app.di.AppContainer

@Composable
fun Ntv2App(
    appContainer: AppContainer
) {
    MaterialTheme {
        AppNavHost(appContainer = appContainer)
    }
}
