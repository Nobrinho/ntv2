package com.ntv2.app.core.multipart

/**
 * Posição de leitura de UMA abertura (open) de um filme dividido: em que parte está, quanto falta
 * da faixa pedida, e as decisões de troca de parte. Puro (sem Android) para ser testável na JVM; o
 * data source composto só traduz estas decisões em aberturas de arquivo.
 *
 * @param length bytes pedidos pelo player; [UNSET] = até o fim do filme.
 */
class MultiPartCursor(
    private val map: VirtualFileMap,
    startPosition: Long,
    length: Long = UNSET
) {
    companion object {
        const val UNSET = -1L
    }

    /** Posição global de leitura. */
    var position: Long = startPosition
        private set

    /** Bytes que ainda faltam da faixa pedida, ou [UNSET]. */
    var remaining: Long = if (length == UNSET) UNSET else minOf(length, map.totalSize - startPosition)
        private set

    /** Parte (índice de 0) em que a leitura está. */
    var part: Int = requireNotNull(map.locate(startPosition)) {
        "Posição $startPosition fora do arquivo (${map.totalSize} bytes)"
    }.part
        private set

    /** Offset dentro da parte atual. */
    val offsetInPart: Long get() = position - map.partStart(part)

    /** Bytes até o fim da parte atual. */
    val bytesToPartEnd: Long get() = map.partEnd(part) - position

    val isLastPart: Boolean get() = part == map.partCount - 1

    /** Leu tudo o que foi pedido (ou chegou ao fim do filme). */
    val isDone: Boolean get() = remaining == 0L || position >= map.totalSize

    /**
     * Quanto pedir à parte atual: o que falta da faixa, limitado ao fim da parte. [UNSET] se a
     * faixa é aberta — aí a parte só devolve o fim (EOF) ao chegar no seu último byte.
     */
    fun lengthForCurrentPart(): Long =
        if (remaining == UNSET) UNSET else minOf(remaining, bytesToPartEnd)

    /** Registra [bytes] lidos da parte atual. */
    fun consume(bytes: Int) {
        require(bytes >= 0 && bytes <= bytesToPartEnd) { "Leitura de $bytes passa do fim da parte $part" }
        position += bytes
        if (remaining != UNSET) remaining -= bytes
    }

    /** Terminou a parte atual e ainda há o que ler na próxima. */
    val canAdvance: Boolean get() = !isLastPart && bytesToPartEnd == 0L && !isDone

    /**
     * Passa para a próxima parte. Devolve a parte que ficou DOIS passos atrás (já assistida e fora
     * do alcance de um pulo curto para trás), que pode ser descartada do disco; null se não há.
     * A parte logo anterior fica: um pulo de 10 s para trás logo depois da emenda a reaproveita.
     */
    fun advance(): Int? {
        check(canAdvance) { "Não dá para avançar: parte $part, ${bytesToPartEnd} bytes até o fim" }
        val discard = (part - 1).takeIf { it >= 0 }
        part += 1
        return discard
    }

    /**
     * Próxima parte a pré-baixar: quando faltam [aheadBytes] ou menos para o fim da parte atual,
     * para a emenda não travar esperando o começo da parte seguinte. null se não é hora ou se esta
     * é a última.
     */
    fun partToPrefetch(aheadBytes: Long): Int? =
        if (!isLastPart && !isDone && bytesToPartEnd <= aheadBytes) part + 1 else null
}
