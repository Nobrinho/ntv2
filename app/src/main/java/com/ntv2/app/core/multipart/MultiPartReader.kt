package com.ntv2.app.core.multipart

import java.io.IOException

/** Leitura sequencial de UMA parte já aberta; devolve [MultiPartReader.END] no fim. */
interface PartStream {
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun close()
}

/**
 * Lê um filme dividido em partes como se fosse um arquivo só: a faixa pedida ([startPosition],
 * [length]) é global; o leitor abre a parte certa, troca de parte na emenda, pré-baixa a parte
 * seguinte e descarta a de dois passos atrás. Sem Android nem TDLib: quem usa dá as funções que
 * de fato abrem, pré-baixam e descartam partes ([MultiPartDataSource] no player). As decisões
 * de qual parte/quando vêm de [MultiPartCursor].
 *
 * @param openPart abre a parte [part] a partir de [offset]; [partLength] = [MultiPartCursor.UNSET]
 *   para ler até o fim da parte (faixa aberta), senão quantos bytes pedir.
 * @param prefetchPart pede o início da parte [part] em segundo plano.
 * @param discardPart apaga do disco a parte [part] (já assistida, fora do alcance de um pulo curto).
 */
class MultiPartReader(
    private val map: VirtualFileMap,
    startPosition: Long,
    length: Long,
    private val prefetchAheadBytes: Long,
    private val openPart: (part: Int, offset: Long, partLength: Long) -> PartStream,
    private val prefetchPart: (part: Int) -> Unit,
    private val discardPart: (part: Int) -> Unit
) {
    companion object {
        const val END = -1
    }

    private val cursor = MultiPartCursor(map, startPosition, length)
    private var stream: PartStream? = null
    private var prefetched = -1

    init {
        openCurrent()
    }

    /** Bytes que faltam da faixa pedida, ou [MultiPartCursor.UNSET]. */
    val remaining: Long get() = cursor.remaining

    /** Posição global da próxima leitura. */
    val position: Long get() = cursor.position

    /** Parte (índice de 0) em que a leitura está. */
    val part: Int get() = cursor.part

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (cursor.isDone) return END
            // Terminou esta parte: segue para a próxima sem depender do EOF da parte.
            if (cursor.canAdvance) {
                switchToNextPart()
                continue
            }
            val current = stream ?: return END
            val wanted = minOf(
                length.toLong(),
                cursor.bytesToPartEnd,
                cursor.remaining.takeIf { it >= 0L } ?: Long.MAX_VALUE
            ).toInt()
            val read = current.read(buffer, offset, wanted)
            if (read == END) {
                // A parte disse que acabou, mas o mapa diz que ainda há bytes: não pula dados em
                // silêncio — erro, e o player reabre na posição (recuperação de I/O).
                throw IOException(
                    "A parte ${cursor.part + 1} de ${map.partCount} acabou antes do esperado " +
                        "(faltavam ${cursor.bytesToPartEnd} bytes)"
                )
            }
            cursor.consume(read)
            prefetchNextIfNear()
            return read
        }
    }

    fun close() {
        closeStream()
    }

    private fun switchToNextPart() {
        closeStream()
        cursor.advance()?.let(discardPart)
        openCurrent()
    }

    private fun openCurrent() {
        val length = cursor.lengthForCurrentPart()
        stream = openPart(cursor.part, cursor.offsetInPart, length)
    }

    private fun prefetchNextIfNear() {
        val next = cursor.partToPrefetch(prefetchAheadBytes) ?: return
        if (next == prefetched) return
        prefetched = next
        prefetchPart(next)
    }

    private fun closeStream() {
        runCatching { stream?.close() }
        stream = null
    }
}
