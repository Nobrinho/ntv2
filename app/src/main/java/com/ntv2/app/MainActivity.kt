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
}
