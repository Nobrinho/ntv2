package com.ntv2.app.core.telegram.tdlib

import org.drinkless.tdlib.TdApi

/**
 * Arquivo de vídeo de uma mensagem, venha ela como VÍDEO ou como DOCUMENTO.
 *
 * O Telegram só marca como vídeo o que reconhece para streaming (tipicamente MP4). Filmes em MKV
 * costumam chegar como documento — antes a grade só lia MessageVideo e eles não apareciam. O player
 * toca os dois igual (o fileId é o mesmo tipo de arquivo do TDLib).
 *
 * Documento não traz duração nem dimensões: ficam 0 (desconhecidas) e o player descobre ao abrir.
 */
internal data class VideoFileInfo(
    val file: TdApi.File,
    val fileName: String,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val thumbnail: TdApi.Thumbnail?,
    val caption: String?
)

internal fun videoFileOf(content: TdApi.MessageContent?): VideoFileInfo? = when (content) {
    is TdApi.MessageVideo -> content.video?.let { video ->
        video.video?.let { file ->
            VideoFileInfo(
                file = file,
                fileName = video.fileName.orEmpty(),
                durationSeconds = video.duration,
                width = video.width,
                height = video.height,
                thumbnail = video.thumbnail,
                caption = content.caption?.text
            )
        }
    }
    is TdApi.MessageDocument -> content.document
        ?.takeIf { isVideoDocument(it.fileName, it.mimeType) }
        ?.let { doc ->
            doc.document?.let { file ->
                VideoFileInfo(
                    file = file,
                    fileName = doc.fileName.orEmpty(),
                    durationSeconds = 0,
                    width = 0,
                    height = 0,
                    thumbnail = doc.thumbnail,
                    caption = content.caption?.text
                )
            }
        }
    else -> null
}

internal fun isVideoMessage(content: TdApi.MessageContent?): Boolean = videoFileOf(content) != null

/**
 * Documento que é vídeo: mime "video/…" (ou matroska), ou extensão de vídeo quando o mime é genérico.
 * Um mime explícito de outro tipo (zip, pdf, texto) vence a extensão.
 */
internal fun isVideoDocument(fileName: String?, mimeType: String?): Boolean {
    val mime = mimeType.orEmpty().trim().lowercase()
    if (mime.startsWith("video/") || mime.contains("matroska")) return true
    val genericMime = mime.isEmpty() || mime == "application/octet-stream"
    return genericMime && VIDEO_EXTENSION_REGEX.containsMatchIn(fileName.orEmpty().trim())
}

/**
 * Junta páginas de buscas paralelas no mesmo chat (filtro Vídeo + filtro Documento) numa página só,
 * sem pular mensagens. Lógica pura sobre ids, testável na JVM.
 *
 * Cada busca para num ponto diferente do histórico. Só é seguro entregar o que está ANTES do ponto
 * onde a busca não esgotada parou mais cedo; o resto fica para a próxima página (que recomeça dali).
 */
internal object MessagePageMerge {

    /** Página bruta de uma busca: ids na ordem do TDLib (mais novo → mais antigo). */
    data class RawPage(val ids: List<Long>, val nextFromMessageId: Long)

    /** Ids escolhidos (mais novo → mais antigo) e o cursor da próxima página (0 = fim). */
    data class Selection(val ids: List<Long>, val nextFromMessageId: Long)

    /** Página "para baixo" (offset 0): mensagens mais antigas que o cursor. */
    fun older(pages: List<RawPage>, limit: Int): Selection {
        // Busca não esgotada: não viu nada abaixo do último id que trouxe.
        val boundary = pages
            .filter { it.nextFromMessageId != 0L }
            .maxOfOrNull { it.ids.minOrNull() ?: it.nextFromMessageId }
        val candidates = pages.flatMap { it.ids }.distinct()
            .filter { boundary == null || it >= boundary }
            .sortedDescending()
        val page = candidates.take(limit)
        val next = when {
            page.size < candidates.size -> page.last()
            else -> boundary ?: 0L
        }
        return Selection(page, next)
    }

    /**
     * Página "para cima" (offset negativo): as [limit] mensagens mais novas que [anchor] e mais
     * próximas dele. Uma busca que trouxe algo pode ter parado antes do que existe acima.
     */
    fun newer(pages: List<RawPage>, anchor: Long, limit: Int): Selection {
        val newerPerPage = pages.map { p -> p.ids.filter { it > anchor } }
        val boundary = newerPerPage.filter { it.isNotEmpty() }.minOfOrNull { it.max() }
        val page = newerPerPage.flatten().distinct()
            .filter { boundary == null || it <= boundary }
            .sorted()
            .take(limit)
            .sortedDescending()
        return Selection(page, 0L)
    }
}
