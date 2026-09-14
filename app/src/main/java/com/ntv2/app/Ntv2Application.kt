package com.ntv2.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ntv2.app.di.AppContainer
import com.ntv2.app.di.DefaultAppContainer
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class Ntv2Application : Application(), ImageLoaderFactory {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }

    // ImageLoader global do Coil, otimizado para TV (Fire TV tem RAM/CPU limitados):
    // - RGB_565 nas capas: metade da memória por bitmap.
    // - Sem crossfade: evita frames extras de fade ao rolar a grade.
    // - Cache de memória (25% da RAM) + disco (200 MB): menos re-decode.
    // - OkHttp com TrustManager que confia nas CAs do sistema + raízes Amazon/Starfield
    //   (o image.tmdb.org usa CloudFront/Amazon; alguns aparelhos não têm essas raízes).
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { buildOkHttpClient() }
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

    private fun buildOkHttpClient(): OkHttpClient {
        return runCatching {
            val cf = CertificateFactory.getInstance("X.509")
            val extras = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                listOf(R.raw.amazon_root_ca1 to "amazon", R.raw.starfield_g2 to "starfield").forEach { (res, alias) ->
                    resources.openRawResource(res).use { setCertificateEntry(alias, cf.generateCertificate(it)) }
                }
            }
            val alg = TrustManagerFactory.getDefaultAlgorithm()
            val customTms = TrustManagerFactory.getInstance(alg).apply { init(extras) }.trustManagers
            val systemTms = TrustManagerFactory.getInstance(alg).apply { init(null as KeyStore?) }.trustManagers
            val managers = (customTms + systemTms).filterIsInstance<X509TrustManager>()
            val composite = CompositeX509TrustManager(managers)
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(composite), null) }
            OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, composite)
                .dns(Ipv4PreferredDns)
                .build()
        }.getOrElse {
            android.util.Log.e("Ntv2Ssl", "Falha ao montar OkHttp com CAs embutidas; usando padrão", it)
            OkHttpClient()
        }
    }

    /** Prefere endereços IPv4 (evita timeout em redes/emuladores com IPv6 quebrado). */
    private object Ipv4PreferredDns : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val all = okhttp3.Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<java.net.Inet4Address>()
            return if (v4.isNotEmpty()) v4 else all
        }
    }

    /** Aceita o certificado se QUALQUER trust manager (sistema ou raízes embutidas) confiar nele. */
    private class CompositeX509TrustManager(
        private val managers: List<X509TrustManager>
    ) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            var last: CertificateException? = null
            for (tm in managers) {
                try {
                    tm.checkServerTrusted(chain, authType)
                    return
                } catch (e: CertificateException) {
                    last = e
                }
            }
            throw last ?: CertificateException("Nenhum trust manager disponível")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            managers.firstOrNull()?.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            managers.flatMap { it.acceptedIssuers.toList() }.toTypedArray()
    }
}
