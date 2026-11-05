package com.ntv2.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Conteúdo atrás das barras do sistema (capas sob a barra de status, como no protótipo).
        // Barras transparentes com ícones claros: a UI é sempre escura. No Android 15+ isso já é obrigatório.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val appContainer = (application as Ntv2Application).appContainer
        setContent {
            Ntv2App(appContainer = appContainer)
        }
    }

    // Android 8–11: sair do app com vídeo tocando entra em PiP (12+ usa a auto-entrada).
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.ntv2.app.feature.playback.presentation.PlayerPip.onUserLeaveHint(this)
    }
}
