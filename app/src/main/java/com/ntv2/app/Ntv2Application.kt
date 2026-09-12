package com.ntv2.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ntv2.app.di.AppContainer
import com.ntv2.app.di.DefaultAppContainer

class Ntv2Application : Application(), ImageLoaderFactory {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }

    // ImageLoader global do Coil, otimizado para TV (Fire TV tem RAM/CPU limitados):
    // - RGB_565 nas capas: metade da memória por bitmap (capas não precisam de alpha).
    // - Sem crossfade: evita frames extras de fade ao rolar a grade.
    // - Cache de memória (25% da RAM) + cache em disco (200 MB): menos re-decode ao rolar/voltar.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
}
