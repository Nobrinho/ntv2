package com.ntv2.app.feature.media.domain

/** Um episódio "a seguir" já resolvido (fileId pronto), para o autoplay ao terminar o atual. */
data class UpNextEpisode(
    val mediaId: String,
    val fileId: Int,
    val title: String,
    val channelName: String,
    val durationSeconds: Int,
    val fileName: String?,
    val thumbnailPath: String?
)

/**
 * Fila "próximo episódio" em memória (singleton). A Biblioteca registra, ao iniciar um episódio, o
 * próximo da mesma série (já resolvido no TDLib) sob a chave do episódio atual. A tela de reprodução
 * consulta por [mediaId] ao chegar ao fim e oferece/reproduz o seguinte. Fora de séries fica vazia.
 */
class UpNextQueue {
    private val map = java.util.concurrent.ConcurrentHashMap<String, UpNextEpisode>()

    fun put(afterMediaId: String, next: UpNextEpisode) {
        map[afterMediaId] = next
    }

    fun nextAfter(mediaId: String): UpNextEpisode? = map[mediaId]

    fun clear(mediaId: String) {
        map.remove(mediaId)
    }
}
