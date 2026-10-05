package com.ntv2.app.feature.playback.presentation

import com.ntv2.app.core.multipart.PartsSnapshot

/**
 * Textos de um filme dividido em partes para o carregamento e o painel "Estado da rede". Funções puras (a
 * tela só as exibe), para testar na JVM sem Compose.
 */
internal object PartsTexts {

    /** "Parte 3/12 — 412 MB de 1,79 GB": a parte em leitura. */
    fun currentPartLine(parts: PartsSnapshot): String =
        "Parte ${parts.currentPartNumber}/${parts.partCount} — " +
            "${formatBytes(parts.currentPartDownloaded)} de ${formatBytes(parts.currentPartSize)}"

    /** "Filme todo: 5,4 GB de 70,5 GB": o que já foi baixado do filme inteiro (partes assistidas contam como feitas). */
    fun wholeMovieLine(parts: PartsSnapshot): String =
        "Filme todo: ${formatBytes(parts.downloadedBytes)} de ${formatBytes(parts.totalBytes)}"

    /** "à frente 2,3 GB" (pré-carga nas partes seguintes); vazio se nada foi baixado adiante. */
    fun aheadText(parts: PartsSnapshot): String =
        if (parts.aheadBytes > 0L) "à frente ${formatBytes(parts.aheadBytes)}" else "nada baixado à frente"

    /** Linha do painel de rede: "Parte 5/12 · atual 1,2 GB de 1,79 GB · à frente 2,3 GB". */
    fun networkLine(parts: PartsSnapshot): String =
        "Parte ${parts.currentPartNumber}/${parts.partCount} · atual " +
            "${formatBytes(parts.currentPartDownloaded)} de ${formatBytes(parts.currentPartSize)} · ${aheadText(parts)}"

    /** "12 partes · 21,5 GB" (resumo do filme dividido). */
    fun summary(parts: PartsSnapshot): String = "${parts.partCount} partes · ${formatBytes(parts.totalBytes)}"

    /** Dica enquanto o player espera o começo: o índice do MKV pode estar no fim do arquivo (última parte). */
    fun indexHint(parts: PartsSnapshot?): String =
        if (parts == null) {
            "Este vídeo precisa baixar mais dados antes de começar (índice no fim do arquivo)."
        } else {
            "Este filme precisa ler o índice no fim do arquivo (parte ${parts.partCount} de ${parts.partCount}) " +
                "antes de começar."
        }
}
