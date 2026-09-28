package com.ntv2.app.core.player.io

import androidx.media3.common.C
import kotlin.math.min

/**
 * Decisão pura de leitura do arquivo parcial, isolada de Android/media3-DataSpec para permitir
 * testes unitários JVM. Dado quantos bytes CONTÍGUOS já estão no disco a partir da posição de
 * leitura, decide se dá para ler agora (e quanto), se chegou ao fim, ou se é preciso aguardar.
 *
 * Origem: o bug que travava MKV — a leitura deve usar os bytes contíguos a partir da posição
 * (GetFileDownloadedPrefixSize), não o total baixado (que pode ser esparso).
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
     * @param readableBytes bytes contíguos no disco a partir da posição de leitura.
     * @param bytesRemaining limite restante do DataSpec, ou C.LENGTH_UNSET se ilimitado.
     * @param requestedLength quanto o chamador quer ler.
     * @param isComplete se o download do arquivo foi concluído.
     */
    fun plan(
        readableBytes: Long,
        bytesRemaining: Long,
        requestedLength: Int,
        isComplete: Boolean
    ): Plan {
        if (readableBytes > 0L) {
            val maxByAvailability = min(readableBytes, requestedLength.toLong())
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
