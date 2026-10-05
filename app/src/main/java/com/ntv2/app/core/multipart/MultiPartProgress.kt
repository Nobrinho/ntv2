package com.ntv2.app.core.multipart

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Situação de uma parte para o mapa do painel de rede. */
enum class PartStatus { WATCHED, CURRENT, DOWNLOADED, PARTIAL, PENDING }

/** Uma célula do mapa de partes; [fraction] (0..1) é o quanto da parte já está no aparelho. */
data class PartCell(val index: Int, val status: PartStatus, val fraction: Float)

/**
 * O que a tela precisa saber de um filme dividido: onde se está, quanto já foi baixado do filme todo e o que há
 * adiante. Parte assistida (e possivelmente descartada do disco) conta como concluída, então [downloadedBytes]
 * nunca anda para trás quando o app libera espaço.
 */
data class PartsSnapshot(
    val partCount: Int,
    /** Índice (de 0) da parte que o player está lendo. */
    val currentPart: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val currentPartSize: Long,
    val currentPartDownloaded: Long,
    /** Bytes já baixados nas partes DEPOIS da atual (pré-carga da emenda e adiante). */
    val aheadBytes: Long,
    val cells: List<PartCell>
) {
    /** Número da parte para mostrar (de 1). */
    val currentPartNumber: Int get() = currentPart + 1

    val currentPartFraction: Float
        get() = if (currentPartSize > 0L) (currentPartDownloaded.toFloat() / currentPartSize).coerceIn(0f, 1f) else 0f

    /** [cells] agrupadas em no máximo [maxCells] blocos (filmes com dezenas de partes). */
    fun groupedCells(maxCells: Int = MAX_CELLS): List<PartCell> = MultiPartProgress.group(cells, maxCells)

    companion object {
        const val MAX_CELLS = 40
    }
}

object MultiPartProgress {

    /**
     * Agrega o estado do filme. [rawDownloaded] = bytes que o TDLib reporta em cada parte (0 se a parte não foi
     * aberta ou foi descartada); [currentPart] = parte em leitura (índice de 0, fixado ao intervalo válido).
     */
    fun snapshot(map: VirtualFileMap, rawDownloaded: List<Long>, currentPart: Int): PartsSnapshot {
        val count = map.partCount
        require(rawDownloaded.size == count) { "Esperava $count partes, vieram ${rawDownloaded.size}" }
        val current = currentPart.coerceIn(0, count - 1)
        var downloaded = 0L
        var ahead = 0L
        val cells = ArrayList<PartCell>(count)
        for (i in 0 until count) {
            val size = map.partSize(i)
            val have = rawDownloaded[i].coerceIn(0L, size)
            val fraction = have.toFloat() / size
            when {
                i < current -> {
                    downloaded += size
                    cells += PartCell(i, PartStatus.WATCHED, 1f)
                }
                i == current -> {
                    downloaded += have
                    cells += PartCell(i, PartStatus.CURRENT, fraction)
                }
                else -> {
                    downloaded += have
                    ahead += have
                    cells += PartCell(
                        i,
                        when {
                            have >= size -> PartStatus.DOWNLOADED
                            have > 0L -> PartStatus.PARTIAL
                            else -> PartStatus.PENDING
                        },
                        fraction
                    )
                }
            }
        }
        return PartsSnapshot(
            partCount = count,
            currentPart = current,
            totalBytes = map.totalSize,
            downloadedBytes = downloaded,
            currentPartSize = map.partSize(current),
            currentPartDownloaded = rawDownloaded[current].coerceIn(0L, map.partSize(current)),
            aheadBytes = ahead,
            cells = cells
        )
    }

    /** Junta células vizinhas em no máximo [maxCells] blocos, preservando o que importa (a atual aparece sempre). */
    fun group(cells: List<PartCell>, maxCells: Int): List<PartCell> {
        if (maxCells <= 0 || cells.size <= maxCells) return cells
        val size = (cells.size + maxCells - 1) / maxCells           // teto da divisão
        return cells.chunked(size).mapIndexed { bloco, grupo ->
            val status = when {
                grupo.any { it.status == PartStatus.CURRENT } -> PartStatus.CURRENT
                grupo.all { it.status == PartStatus.WATCHED } -> PartStatus.WATCHED
                grupo.all { it.status == PartStatus.WATCHED || it.status == PartStatus.DOWNLOADED } -> PartStatus.DOWNLOADED
                grupo.any { it.status != PartStatus.PENDING } -> PartStatus.PARTIAL
                else -> PartStatus.PENDING
            }
            PartCell(bloco, status, grupo.map { it.fraction }.average().toFloat())
        }
    }
}

/**
 * Soma só os aumentos de um valor que pode cair (parte descartada do disco). Serve para medir velocidade e
 * progresso sem que uma liberação de espaço vire "velocidade negativa" nem esconda o download seguinte.
 */
class MonotonicCounter {
    private var last = 0L
    private var total = 0L

    fun feed(value: Long): Long {
        if (value > last) total += value - last
        last = value
        return total
    }
}

/**
 * Progresso da parte em leitura, para detectar download parado: trocar de parte já conta como andamento
 * (a emenda abriu outro arquivo), e dentro da parte soma os bytes novos. Pré-carga da PRÓXIMA parte não mascara
 * um travamento da atual, porque só a parte ativa entra aqui.
 */
class ActivePartCounter {
    private var lastPart = -1
    private var last = 0L
    private var total = 0L

    fun feed(part: Int, bytes: Long): Long {
        if (part != lastPart) {
            lastPart = part
            total += 1
        } else if (bytes > last) {
            total += bytes - last
        }
        last = bytes
        return total
    }
}

/**
 * Parte que o player está LENDO em cada filme dividido, escrita pelo data source composto (nos mesmos pontos
 * que geram os logs `NtvParts`) e lida pelo coordinator para montar o [PartsSnapshot].
 */
class MultiPartPlaybackState {
    private val flows = ConcurrentHashMap<Int, MutableStateFlow<Int>>()

    private fun flow(firstFileId: Int): MutableStateFlow<Int> = flows.getOrPut(firstFileId) { MutableStateFlow(0) }

    /** Índice (de 0) da parte em leitura do filme identificado por [firstFileId]. */
    fun currentPart(firstFileId: Int): StateFlow<Int> = flow(firstFileId)

    fun set(firstFileId: Int, part: Int) {
        flow(firstFileId).value = part
    }

    fun reset(firstFileId: Int) {
        flows.remove(firstFileId)
    }
}
