package com.ntv2.app.feature.media.presentation.trailer

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** Extrai o ID (11 caracteres) de links do YouTube; só java.net para rodar na JVM. */
object YouTubeIds {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun videoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        val candidate = when (host) {
            "youtube.com", "www.youtube.com", "m.youtube.com" -> when {
                uri.path == "/watch" -> queryParam(uri.rawQuery, "v")
                uri.path?.startsWith("/embed/") == true -> uri.path.removePrefix("/embed/").substringBefore('/')
                else -> null
            }
            "youtu.be" -> uri.path?.removePrefix("/")?.substringBefore('/')
            else -> null
        }
        return candidate?.takeIf { ID.matches(it) }
    }

    private fun queryParam(query: String?, name: String): String? =
        query?.split('&')
            ?.firstNotNullOfOrNull { part ->
                val key = part.substringBefore('=')
                if (key == name && part.contains('=')) part.substringAfter('=') else null
            }
}

/** A prévia só roda com ID válido, as três chaves ligadas, sem economia de dados e sem falha na sessão. */
fun shouldPreviewTrailer(
    videoId: String?,
    userEnabled: Boolean,
    remoteEnabled: Boolean,
    showCovers: Boolean,
    dataSaver: Boolean,
    failedIds: Set<String>,
): Boolean = videoId != null &&
    userEnabled && remoteEnabled && showCovers &&
    !dataSaver &&
    videoId !in failedIds

/** IDs que falharam nesta sessão; só em memória e seguro entre threads. */
object TrailerFailures {
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val ids: Set<String> get() = failed.toSet()

    fun add(id: String) {
        failed.add(id)
    }
}

/**
 * Por que a prévia não entrou. Só falha que se repetiria igual (vídeo bloqueado fora do YouTube,
 * renderizador que cai, aparelho sem WebView) bloqueia o vídeo até reabrir o app; demora para começar
 * pode ser rede ou decoder ocupado, e a próxima abertura dos detalhes tenta de novo.
 */
enum class TrailerFailure(val blocksSession: Boolean) {
    TIMEOUT(blocksSession = false),
    PLAYER_ERROR(blocksSession = true),
    RENDERER_GONE(blocksSession = true),
    NO_WEBVIEW(blocksSession = true);

    /** Guarda o ID na lista da sessão quando a falha bloqueia. */
    fun record(id: String) {
        if (blocksSession) TrailerFailures.add(id)
    }
}
