package com.ntv2.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
