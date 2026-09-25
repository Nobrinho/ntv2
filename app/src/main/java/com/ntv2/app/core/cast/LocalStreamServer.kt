@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ntv2.app.core.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

private const val TAG = "NtvCastServer"

/**
 * Servidor HTTP mínimo na rede local que entrega o vídeo do Telegram ao Chromecast. O Chromecast só
 * toca a partir de uma URL; aqui ele recebe os bytes pela mesma fonte do player local (que baixa do
 * TDLib sob demanda), com suporte a Range para o Chromecast pular pelo arquivo.
 *
 * Serve UM arquivo por vez: [serve] troca o arquivo atual e devolve a URL para o Chromecast.
 */
class LocalStreamServer(
    context: Context,
    private val dataSourceFactory: DataSource.Factory
) {
    private val appContext = context.applicationContext
    private var serverSocket: ServerSocket? = null
    private val workers = Executors.newCachedThreadPool()

    @Volatile private var current: Served? = null

    private data class Served(val fileId: Int, val sourceUri: String, val totalBytes: Long, val mimeType: String, val token: String)

    /** Publica o arquivo e devolve a URL acessível pela rede local (null se não houver Wi‑Fi/IP). */
    @Synchronized
    fun serve(fileId: Int, sourceUri: String, totalBytes: Long, mimeType: String): String? {
        val ip = localIpv4() ?: return null
        val socket = serverSocket ?: ServerSocket(0).also {
            serverSocket = it
            thread(name = "ntv-cast-server", isDaemon = true) { acceptLoop(it) }
        }
        // Token aleatório no caminho: outro aparelho da rede não consegue adivinhar a URL.
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        current = Served(fileId, sourceUri, totalBytes, mimeType, token)
        return "http://$ip:${socket.localPort}/v/$token"
    }

    @Synchronized
    fun stop() {
        current = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                break
            }
            workers.execute { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 30_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
                    .mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() } }
                    .toMap()
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: return
                val path = parts.getOrNull(1) ?: return
                val out = socket.getOutputStream()
                val served = current
                if (served == null || path != "/v/${served.token}") {
                    writeHead(out, "404 Not Found", listOf("Content-Length" to "0"))
                    return
                }
                val total = served.totalBytes
                val (start, end) = parseRange(headers["range"], total)
                if (start >= total) {
                    writeHead(out, "416 Range Not Satisfiable", listOf("Content-Range" to "bytes */$total", "Content-Length" to "0"))
                    return
                }
                val length = end - start + 1
                val partial = headers.containsKey("range")
                writeHead(
                    out,
                    if (partial) "206 Partial Content" else "200 OK",
                    buildList {
                        add("Content-Type" to served.mimeType)
                        add("Accept-Ranges" to "bytes")
                        add("Content-Length" to length.toString())
                        if (partial) add("Content-Range" to "bytes $start-$end/$total")
                        // O receptor do Chromecast é uma página web: precisa de CORS.
                        add("Access-Control-Allow-Origin" to "*")
                    }
                )
                if (method == "HEAD") return
                streamRange(served, start, length, out)
            }.onFailure { e ->
                if (e !is SocketException) Log.w(TAG, "falha servindo o Chromecast", e)
            }
        }
    }

    private fun streamRange(served: Served, start: Long, length: Long, out: OutputStream) {
        val source = dataSourceFactory.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(Uri.parse(served.sourceUri)).setPosition(start).build())
            val buffer = ByteArray(128 * 1024)
            var remaining = length
            while (remaining > 0) {
                val n = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, n)
                remaining -= n
            }
            out.flush()
        } finally {
            runCatching { source.close() }
        }
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long> {
        val last = total - 1
        val spec = header?.removePrefix("bytes=")?.split(",")?.firstOrNull()?.trim() ?: return 0L to last
        val (a, b) = spec.split("-", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when {
            a.isEmpty() -> (total - (b.toLongOrNull() ?: 0L)).coerceAtLeast(0L) to last // sufixo: últimos N bytes
            else -> (a.toLongOrNull() ?: 0L) to (b.toLongOrNull()?.coerceAtMost(last) ?: last)
        }
    }

    private fun writeHead(out: OutputStream, status: String, headers: List<Pair<String, String>>) {
        val sb = StringBuilder("HTTP/1.1 $status\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun localIpv4(): String? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val props = runCatching { cm.getLinkProperties(cm.activeNetwork) }.getOrNull() ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }
}
