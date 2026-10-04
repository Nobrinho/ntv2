package com.ntv2.app.core.multipart

/**
 * Mapa de um arquivo lógico formado pela concatenação de partes (em ordem) de tamanhos conhecidos.
 * Converte posição global ↔ (parte, offset na parte) e quebra faixas que cruzam partes. Puro, sem
 * Android — é a base do modo composto do data source do player. Índices de parte começam em 0.
 */
class VirtualFileMap(partSizes: List<Long>) {

    /** Posição [offset] dentro da parte [part]. */
    data class Location(val part: Int, val offset: Long)

    /** Trecho de [length] bytes a partir de [offset] dentro da parte [part]. */
    data class Slice(val part: Int, val offset: Long, val length: Long)

    private val sizes: LongArray
    private val starts: LongArray

    init {
        require(partSizes.isNotEmpty()) { "É preciso ao menos uma parte" }
        require(partSizes.all { it > 0L }) { "Tamanho de parte inválido: $partSizes" }
        sizes = partSizes.toLongArray()
        starts = LongArray(sizes.size)
        var acc = 0L
        for (i in sizes.indices) {
            starts[i] = acc
            acc += sizes[i]
        }
    }

    val partCount: Int get() = sizes.size

    val totalSize: Long = sizes.sum()

    fun partSize(part: Int): Long = sizes[part]

    /** Posição global em que a parte começa. */
    fun partStart(part: Int): Long = starts[part]

    /** Posição global logo após o último byte da parte. */
    fun partEnd(part: Int): Long = starts[part] + sizes[part]

    /** Parte e offset de [position]; null se fora do arquivo (negativa ou >= total, ou seja, fim). */
    fun locate(position: Long): Location? {
        if (position < 0L || position >= totalSize) return null
        // Última parte cujo início é <= position (busca binária).
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= position) lo = mid else hi = mid - 1
        }
        return Location(lo, position - starts[lo])
    }

    /** Bytes que faltam para acabar a parte que contém [position]; 0 se fora do arquivo. */
    fun bytesLeftInPart(position: Long): Long {
        val loc = locate(position) ?: return 0L
        return sizes[loc.part] - loc.offset
    }

    /**
     * Quebra a faixa global [position, position+[length]) em trechos por parte, em ordem. A faixa
     * é cortada no fim do arquivo; vazia se [length] <= 0 ou [position] fora do arquivo.
     */
    fun slice(position: Long, length: Long): List<Slice> {
        if (length <= 0L) return emptyList()
        var loc = locate(position) ?: return emptyList()
        var left = minOf(length, totalSize - position)
        val out = ArrayList<Slice>()
        while (left > 0L) {
            val take = minOf(left, sizes[loc.part] - loc.offset)
            out += Slice(loc.part, loc.offset, take)
            left -= take
            if (left > 0L) loc = Location(loc.part + 1, 0L)
        }
        return out
    }

    /** Da [position] até o fim do arquivo. */
    fun sliceToEnd(position: Long): List<Slice> = slice(position, totalSize - position)
}
