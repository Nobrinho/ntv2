package com.ntv2.app.core.multipart

import java.util.Locale

/**
 * Nome de uma parte de um arquivo dividido por bytes: `<arquivo original>.partNNofMM`
 * (ex.: `Filme.2020.mkv.part03of11`). O mesmo formato é gerado por `tools/ntv2_split.py` — manter
 * os dois em sincronia (os exemplos dos testes de ambos os lados são os mesmos).
 *
 * O total vai no nome para o app saber quantas partes esperar; e `cat *.part*` junta o arquivo
 * original fora do app. [index] começa em 1 (como no nome); o resto do pacote usa índices de 0.
 */
data class PartName(val baseName: String, val index: Int, val total: Int) {

    /** Nome do arquivo desta parte, no formato canônico (zeros à esquerda). */
    val fileName: String get() = format(baseName, index, total)

    companion object {
        /** Menos que isso não é um arquivo dividido. */
        const val MIN_PARTS = 2

        private val REGEX = Regex("^(.+)\\.part(\\d+)of(\\d+)$", RegexOption.IGNORE_CASE)

        /** Interpreta [fileName]; null se não for nome de parte ou se índice/total forem inválidos. */
        fun parse(fileName: String?): PartName? {
            val match = REGEX.matchEntire(fileName.orEmpty().trim()) ?: return null
            val index = match.groupValues[2].toIntOrNull() ?: return null
            val total = match.groupValues[3].toIntOrNull() ?: return null
            if (total < MIN_PARTS || index !in 1..total) return null
            return PartName(match.groupValues[1], index, total)
        }

        /** Largura dos números: no mínimo 2 dígitos, ou os dígitos do total se forem mais. */
        fun format(baseName: String, index: Int, total: Int): String {
            val width = maxOf(2, total.toString().length)
            return String.format(Locale.ROOT, "%s.part%0${width}dof%0${width}d", baseName, index, total)
        }
    }
}
