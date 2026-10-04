package com.ntv2.app.core.multipart

/**
 * Agrupa itens (mensagens/arquivos) que são partes do mesmo arquivo dividido. Lógica pura sobre
 * nomes, testável na JVM. Quem chama decide o que fazer com grupos incompletos.
 */
object PartGrouper {

    /**
     * Partes de um mesmo arquivo. [partsByIndex] é indexado pelo número do nome (1..[total]).
     * Mesmo nome-base com totais diferentes são grupos diferentes.
     */
    data class Group<T>(
        val baseName: String,
        val total: Int,
        val partsByIndex: Map<Int, T>
    ) {
        val isComplete: Boolean get() = partsByIndex.size == total

        /** Índices (1..[total]) que não apareceram. */
        val missing: List<Int> get() = (1..total).filter { it !in partsByIndex }

        /** A parte 1, onde a biblioteca mostra o card do arquivo. */
        val first: T? get() = partsByIndex[1]

        /** As partes presentes, em ordem de índice. */
        val parts: List<T> get() = partsByIndex.toSortedMap().values.toList()
    }

    data class Result<T>(
        /** Em ordem da primeira aparição de cada grupo na lista de entrada. */
        val groups: List<Group<T>>,
        /** Itens que não são partes, na ordem original. */
        val standalone: List<T>
    )

    /**
     * Se o mesmo índice aparece duas vezes no grupo (reenvio), vale o primeiro da lista — quem
     * chama controla a ordem de entrada (ex.: mais novo primeiro).
     */
    fun <T> group(items: List<T>, nameOf: (T) -> String?): Result<T> {
        val order = mutableListOf<Pair<String, Int>>()
        val byKey = HashMap<Pair<String, Int>, MutableMap<Int, T>>()
        val standalone = mutableListOf<T>()
        for (item in items) {
            val part = PartName.parse(nameOf(item))
            if (part == null) {
                standalone += item
                continue
            }
            val key = part.baseName to part.total
            val parts = byKey.getOrPut(key) {
                order += key
                mutableMapOf()
            }
            parts.putIfAbsent(part.index, item)
        }
        val groups = order.map { key -> Group(key.first, key.second, byKey.getValue(key)) }
        return Result(groups, standalone)
    }
}
