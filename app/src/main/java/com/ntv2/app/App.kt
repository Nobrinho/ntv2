package com.ntv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.ntv2.app.core.navigation.AppNavHost
import com.ntv2.app.di.AppContainer

@Composable
fun Ntv2App(
    appContainer: AppContainer
) {
    // UI desenhada para fundo escuro (textos em branco). Fixa o esquema escuro e um fundo
    // sólido para não depender do modo claro/escuro do dispositivo.
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101014))
        ) {
            AppNavHost(appContainer = appContainer)
        }
    }
}
