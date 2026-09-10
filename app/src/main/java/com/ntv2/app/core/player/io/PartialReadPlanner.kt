package com.ntv2.app.core.player.io

import androidx.media3.common.C
import kotlin.math.min

/**
 * Decisão pura de leitura do arquivo parcial, isolada de Android/media3-DataSpec para permitir
 * testes unitários JVM. É o núcleo que decide, dada a fronteira contígua disponível e a posição
 * de leitura, se dá para ler agora (e quanto), se chegou ao fim, ou se é preciso aguardar bytes.
 *
 * Foi extraída daqui o bug que travava MKV: a leitura deve usar a fronteira CONTÍGUA
 * (downloadOffset + downloadedPrefixSize), não o total baixado (que pode ser esparso).
 */
internal object PartialReadPlanner {

    sealed interface Plan {
        /** Há [maxBytes] bytes contíguos disponíveis para ler agora. */
        data class Read(val maxBytes: Int) : Plan
        /** Nada mais a ler: download concluído e posição no fim do disponível. */
        data object EndOfInput : Plan
        /** Ainda não há bytes na posição atual e o download não terminou: aguardar. */
        data object Wait : Plan
    }

    /**
     * @param contiguousReadableStart início da região contígua baixada (downloadOffset).
     * @param contiguousReadableEnd fim da região contígua (downloadOffset + prefixo).
     * @param readPosition posição atual de leitura.
     * @param bytesRemaining limite restante do DataSpec, ou C.LENGTH_UNSET se ilimitado.
     * @param requestedLength quanto o chamador quer ler.
     * @param isComplete se o download do arquivo foi concluído.
     */
    fun plan(
        contiguousReadableStart: Long,
        contiguousReadableEnd: Long,
        readPosition: Long,
        bytesRemaining: Long,
        requestedLength: Int,
        isComplete: Boolean
    ): Plan {
        // Só é seguro ler se a posição está DENTRO da região contígua baixada. Ler antes do início
        // (offset à frente após um seek) retornaria lixo do disco → extrator falha (varint inválido).
        val withinRegion = readPosition >= contiguousReadableStart && readPosition < contiguousReadableEnd
        if (withinRegion) {
            val canRead = contiguousReadableEnd - readPosition
            val maxByAvailability = min(canRead, requestedLength.toLong())
            val maxToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                maxByAvailability
            } else {
                min(maxByAvailability, bytesRemaining)
            }
            return Plan.Read(maxToRead.toInt())
        }
        if (isComplete) {
            return Plan.EndOfInput
        }
        return Plan.Wait
    }
}
