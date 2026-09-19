package com.ntv2.app.core.player.io

import kotlin.math.max
import kotlin.math.min

/**
 * Janela deslizante no disco (lógica pura, testável na JVM).
 *
 * O TDLib grava o vídeo num arquivo único e nunca apaga o que já foi assistido: um filme de 3 GB
 * visto até o fim ocupava 3 GB e o Fire OS avisava "armazenamento baixo". Aqui decidimos que
 * trecho JÁ LIDO pelo player pode ter os blocos liberados (punch hole), mantendo:
 *  - [headPinBytes] do início (cabeçalho/moov/SeekHead — relidos a cada prepare, retry ou troca de áudio);
 *  - [tailPinBytes] do fim (índice do MKV/moov no fim);
 *  - [keepBehindBytes] atrás do ponto de leitura (buffer do ExoPlayer + voltar alguns segundos).
 *
 * O trecho liberado é sempre um intervalo único [headPinBytes, evictedEnd), crescendo conforme a
 * reprodução avança. Ler dentro dele exige baixar de novo (rebase), pois o TDLib ainda o
 * considera baixado.
 */
data class DiskWindowPolicy(
    val headPinBytes: Long,
    val tailPinBytes: Long,
    val keepBehindBytes: Long,
    val minEvictBytes: Long
) {
    /**
     * Faixa a liberar agora ou null. Só avança quando a leitura atual é CONTÍNUA há mais que
     * [keepBehindBytes] (reprodução de fato) — leituras curtas no fim do arquivo (índice do MKV,
     * moov no fim) não disparam o descarte de tudo que está antes delas.
     */
    fun evictionRange(
        evictedEnd: Long,
        readPosition: Long,
        streamStart: Long,
        fileSize: Long
    ): LongRange? {
        if (fileSize <= 0L) return null
        if (readPosition - streamStart <= keepBehindBytes) return null
        val start = max(evictedEnd, headPinBytes)
        val end = min(readPosition - keepBehindBytes, fileSize - tailPinBytes)
        if (end - start < minEvictBytes) return null
        return start until end
    }

    /** true se [position] caiu num trecho já liberado do disco. */
    fun isEvicted(position: Long, evictedEnd: Long): Boolean =
        position >= headPinBytes && position < evictedEnd

    /**
     * Limita os bytes legíveis a partir de [position] para não atravessar o trecho liberado
     * (o TDLib responderia que eles existem, mas no disco agora são zeros).
     */
    fun clampReadable(position: Long, readable: Long, evictedEnd: Long): Long {
        if (evictedEnd <= headPinBytes) return readable
        if (isEvicted(position, evictedEnd)) return 0L
        if (position < headPinBytes && position + readable > headPinBytes) return headPinBytes - position
        return readable
    }
}
