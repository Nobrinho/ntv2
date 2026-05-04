package com.ntv2.app

import android.app.Application
import com.ntv2.app.di.AppContainer
import com.ntv2.app.di.DefaultAppContainer

class Ntv2Application : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
