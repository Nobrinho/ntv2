package com.ntv2.app.core.multipart

import kotlin.math.abs

/**
 * Escolhe, entre as mensagens vizinhas de uma parte, as que formam o MESMO arquivo dividido.
 * Pura (sem TDLib): o gateway só alimenta com os candidatos que encontrou.
 */
object PartResolver {

    /** Arquivo de uma mensagem candidata a parte. [sizeBytes] vem do TDLib (0 = desconhecido). */
    data class Candidate(
        val messageId: Long,
        val fileId: Int,
        val fileName: String,
        val sizeBytes: Long
    )

    /**
     * Partes de [anchor] entre os [candidates]. Mensagem repetida conta uma vez. Se o mesmo índice
     * aparece em duas mensagens (reenvio), vale a mais próxima de [anchorMessageId] — o reenvio
     * típico fica ao lado do original. Sem nenhuma parte do arquivo, devolve um grupo vazio.
     */
    fun resolve(
        anchor: PartName,
        anchorMessageId: Long,
        candidates: List<Candidate>
    ): PartGrouper.Group<Candidate> {
        val nearestFirst = candidates
            .distinctBy { it.messageId }
            .sortedBy { abs(it.messageId - anchorMessageId) }
        return PartGrouper.group(nearestFirst) { it.fileName }.groups
            .firstOrNull { it.baseName == anchor.baseName && it.total == anchor.total }
            ?: PartGrouper.Group(anchor.baseName, anchor.total, emptyMap())
    }
}
