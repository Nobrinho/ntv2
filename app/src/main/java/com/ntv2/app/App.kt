package com.ntv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ntv2.app.core.navigation.AppNavHost
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrTheme
import com.ntv2.app.di.AppContainer

@Composable
fun Ntv2App(
    appContainer: AppContainer
) {
    // UI desenhada para fundo escuro (textos em branco). Fixa o esquema escuro e um fundo
    // sólido para não depender do modo claro/escuro do dispositivo.
    NbrTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandColors.Background)
        ) {
            AppNavHost(appContainer = appContainer)
        }
    }
}
