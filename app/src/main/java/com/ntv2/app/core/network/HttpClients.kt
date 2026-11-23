package com.ntv2.app.core.network

import android.content.Context
import com.ntv2.app.R
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Cliente OkHttp base do app: criar UM só (AppContainer.httpClient) e derivar com `newBuilder()` quando
 * um uso precisar de prazos ou dispatcher próprios — assim todos dividem o mesmo pool de conexões e
 * de threads (recomendação da documentação do OkHttp).
 *
 * - TrustManager que confia nas CAs do sistema + raízes Amazon/Starfield embutidas (o image.tmdb.org
 *   usa CloudFront/Amazon; alguns aparelhos, como Fire TV antigos, não têm essas raízes).
 * - DNS que prefere IPv4 (evita timeout em redes/emuladores com IPv6 quebrado).
 */
fun buildBaseOkHttpClient(context: Context): OkHttpClient {
    val base = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .dns(Ipv4PreferredDns)
    return runCatching {
        val cf = CertificateFactory.getInstance("X.509")
        val extras = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            listOf(R.raw.amazon_root_ca1 to "amazon", R.raw.starfield_g2 to "starfield").forEach { (res, alias) ->
                context.resources.openRawResource(res).use { setCertificateEntry(alias, cf.generateCertificate(it)) }
            }
        }
        val alg = TrustManagerFactory.getDefaultAlgorithm()
        val customTms = TrustManagerFactory.getInstance(alg).apply { init(extras) }.trustManagers
        val systemTms = TrustManagerFactory.getInstance(alg).apply { init(null as KeyStore?) }.trustManagers
        val managers = (customTms + systemTms).filterIsInstance<X509TrustManager>()
        val composite = CompositeX509TrustManager(managers)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(composite), null) }
        base.sslSocketFactory(ssl.socketFactory, composite).build()
    }.getOrElse {
        android.util.Log.e("Ntv2Ssl", "Falha ao montar OkHttp com CAs embutidas; usando padrão", it)
        base.build()
    }
}

/** Prefere endereços IPv4 (evita timeout em redes/emuladores com IPv6 quebrado). */
private object Ipv4PreferredDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val v4 = all.filterIsInstance<Inet4Address>()
        return if (v4.isNotEmpty()) v4 else all
    }
}

/** Aceita o certificado se QUALQUER trust manager (sistema ou raízes embutidas) confiar nele. */
// Não relaxa a validação: só delega aos validadores padrão (sistema + raízes Amazon/Starfield).
@android.annotation.SuppressLint("CustomX509TrustManager")
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
