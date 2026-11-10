package com.ntv2.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ntv2.app.core.navigation.AppNavHost
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrTheme
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.di.AppContainer

@Composable
fun Ntv2App(
    appContainer: AppContainer
) {
    // UI desenhada para fundo escuro (textos em branco). Fixa o esquema escuro e um fundo
    // sólido para não depender do modo claro/escuro do dispositivo.
    NbrTheme {
        val adaptive = rememberAdaptiveLayoutInfo()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandColors.Background)
                // Layout de TV fora de uma TV (tablet deitado): as telas de TV não reservam espaço para a
                // barra de navegação / barra de tarefas do Android, que cobria os botões de baixo.
                // Consome o recuo aqui (as telas que já o tratam não somam de novo); no player com a
                // tela cheia as barras somem e o recuo vira zero.
                .then(
                    if (adaptive.useTvLayout && !adaptive.isTv) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    else Modifier
                )
        ) {
            AppNavHost(appContainer = appContainer)
        }
    }
}
