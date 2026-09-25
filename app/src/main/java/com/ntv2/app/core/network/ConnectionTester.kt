package com.ntv2.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URL

/** Resultado de uma etapa do teste. */
sealed interface CheckResult {
    data object Pending : CheckResult
    data object Running : CheckResult
    data class Ok(val value: String, val good: Boolean = true) : CheckResult
    data class Failed(val reason: String) : CheckResult
}

data class ConnectionReport(
    val network: CheckResult = CheckResult.Pending,
    val internet: CheckResult = CheckResult.Pending,
    val telegram: CheckResult = CheckResult.Pending,
    val speed: CheckResult = CheckResult.Pending,
    /** Resumo final (null enquanto roda). */
    val verdict: String? = null,
    val verdictGood: Boolean = false
) {
    val running: Boolean get() = verdict == null
}

/**
 * Diagnóstico da conexão do aparelho: tipo de rede, internet (latência), Telegram (ping pelo
 * TDLib) e velocidade de download. Emite o relatório a cada etapa para a tela ir preenchendo.
 */
class ConnectionTester(
    context: Context,
    private val gateway: TdlibPlaybackGateway
) {
    private val appContext = context.applicationContext

    fun run(): Flow<ConnectionReport> = flow {
        var r = ConnectionReport(network = CheckResult.Running)
        emit(r)

        val networkType = networkType()
        r = r.copy(
            network = networkType?.let { CheckResult.Ok(it) } ?: CheckResult.Failed("Sem rede conectada"),
            internet = CheckResult.Running
        )
        emit(r)
        if (networkType == null) {
            emit(r.copy(internet = CheckResult.Failed("Sem rede"), telegram = CheckResult.Failed("Sem rede"),
                speed = CheckResult.Failed("Sem rede"), verdict = "Aparelho sem rede. Verifique o Wi‑Fi ou o cabo."))
            return@flow
        }

        val internetMs = latencyMs(INTERNET_PROBE_URL)
        r = r.copy(
            internet = internetMs?.let { CheckResult.Ok("$it ms", good = it < 300) } ?: CheckResult.Failed("Sem acesso à internet"),
            telegram = CheckResult.Running
        )
        emit(r)

        val telegramMs = runCatching { gateway.pingTelegramMs() }.getOrNull()
        r = r.copy(
            telegram = telegramMs?.let { CheckResult.Ok("$it ms", good = it < 500) } ?: CheckResult.Failed("Telegram não respondeu"),
            speed = CheckResult.Running
        )
        emit(r)

        val mbps = downloadMbps()
        r = r.copy(
            speed = mbps?.let { CheckResult.Ok(formatMbps(it), good = it >= GOOD_MBPS) } ?: CheckResult.Failed("Não foi possível medir")
        )
        val (verdict, good) = verdict(internetMs, telegramMs, mbps)
        emit(r.copy(verdict = verdict, verdictGood = good))
    }.flowOn(Dispatchers.IO)

    private fun networkType(): String? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return null
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cabo (Ethernet)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Outra"
        }
    }

    /** Menor de 3 tentativas (a 1ª inclui DNS/TLS e costuma ser mais lenta). */
    private fun latencyMs(url: String): Long? =
        (1..3).mapNotNull { runCatching { timedRequest(url) }.getOrNull() }.minOrNull()

    private fun timedRequest(url: String): Long {
        val start = System.nanoTime()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            useCaches = false
        }
        try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
        return (System.nanoTime() - start) / 1_000_000
    }

    /** Baixa até [SPEED_TEST_BYTES] ou por no máximo [SPEED_TEST_MAX_MS]; o que vier primeiro. */
    private fun downloadMbps(): Double? = runCatching {
        val conn = (URL(SPEED_TEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            useCaches = false
        }
        try {
            conn.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                val start = System.nanoTime()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if ((System.nanoTime() - start) / 1_000_000 >= SPEED_TEST_MAX_MS) break
                }
                val seconds = (System.nanoTime() - start) / 1e9
                if (total <= 0L || seconds <= 0.0) null else total * 8 / seconds / 1_000_000
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun verdict(internetMs: Long?, telegramMs: Long?, mbps: Double?): Pair<String, Boolean> = when {
        internetMs == null -> "Rede conectada, mas sem acesso à internet. Reinicie o roteador." to false
        telegramMs == null -> "Internet ok, mas o Telegram não respondeu. Pode ser bloqueio da rede ou instabilidade." to false
        mbps == null -> "Conexão ok, mas não foi possível medir a velocidade." to true
        mbps >= 25 -> "Conexão ótima: filmes em 1080p e 4K devem abrir rápido." to true
        mbps >= GOOD_MBPS -> "Conexão boa: filmes em 1080p devem rodar bem." to true
        mbps >= 5 -> "Conexão razoável: prefira versões 720p; 1080p pode demorar a iniciar." to false
        else -> "Conexão fraca: os vídeos vão demorar para iniciar e podem travar." to false
    }

    private fun formatMbps(v: Double): String =
        if (v >= 10) "${v.toInt()} Mbps" else String.format(java.util.Locale("pt", "BR"), "%.1f Mbps", v)

    private companion object {
        const val INTERNET_PROBE_URL = "https://www.gstatic.com/generate_204"
        const val SPEED_TEST_BYTES = 25_000_000
        const val SPEED_TEST_URL = "https://speed.cloudflare.com/__down?bytes=$SPEED_TEST_BYTES"
        const val SPEED_TEST_MAX_MS = 8_000L
        const val GOOD_MBPS = 10.0
    }
}
