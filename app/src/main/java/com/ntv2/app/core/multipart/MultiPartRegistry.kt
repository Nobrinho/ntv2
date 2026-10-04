package com.ntv2.app.core.multipart

import java.util.concurrent.ConcurrentHashMap

/** Uma parte de um filme dividido, na ordem de reprodução: arquivo no TDLib e tamanho em bytes. */
data class PartRef(val fileId: Int, val sizeBytes: Long)

/** Quem sabe dizer, para o `fileId` de um filme, de que partes ele é feito (null = arquivo único). */
fun interface PartsLookup {
    fun partsOf(fileId: Int): List<PartRef>?

    /**
     * Tamanho exato de [fileId] se ele é uma parte de um filme dividido já registrado; null senão.
     * O data source usa para saber onde a parte termina sem esperar o TDLib marcá-la completa.
     */
    fun sizeOfPart(fileId: Int): Long? = null
}

/**
 * Partes dos filmes divididos que o usuário abriu, indexadas pelo `fileId` da parte 1 (que é o
 * `fileId` que o resto do app já usa para o filme). Em memória: é preenchido ao abrir o filme
 * ([com.ntv2.app.feature.media.domain.MultiPartPreparer]) e consultado pelo player.
 */
class MultiPartRegistry : PartsLookup {
    private val byFirstFileId = ConcurrentHashMap<Int, List<PartRef>>()
    private val sizeByPartFileId = ConcurrentHashMap<Int, Long>()

    /** Registra [parts] (em ordem); o filme passa a ser identificado pelo fileId da 1ª parte. */
    fun register(parts: List<PartRef>) {
        require(parts.size >= PartName.MIN_PARTS) { "Filme dividido precisa de ao menos 2 partes" }
        require(parts.all { it.sizeBytes > 0L }) { "Tamanho de parte desconhecido: $parts" }
        require(parts.map { it.fileId }.toSet().size == parts.size) { "Partes repetidas: $parts" }
        forget(parts.first().fileId)
        byFirstFileId[parts.first().fileId] = parts.toList()
        parts.forEach { sizeByPartFileId[it.fileId] = it.sizeBytes }
    }

    override fun partsOf(fileId: Int): List<PartRef>? = byFirstFileId[fileId]

    override fun sizeOfPart(fileId: Int): Long? = sizeByPartFileId[fileId]

    fun forget(fileId: Int) {
        byFirstFileId.remove(fileId)?.forEach { sizeByPartFileId.remove(it.fileId) }
    }
}

/** Todos os fileIds do filme: as partes se for dividido, senão só o próprio. */
fun PartsLookup?.allFileIds(fileId: Int): List<Int> =
    this?.partsOf(fileId)?.map { it.fileId } ?: listOf(fileId)

/** URI que o ExoPlayer abre para um filme dividido (o arquivo único usa `tgfile://video/<id>`). */
object MultiPartUris {
    private const val PREFIX = "tgfile://multi/"

    fun forFirstFileId(fileId: Int): String = "$PREFIX$fileId"

    /** fileId da parte 1 se [uri] for de um filme dividido; null senão. */
    fun firstFileIdOf(uri: String): Int? =
        uri.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.substringBefore('?')?.toIntOrNull()
}
