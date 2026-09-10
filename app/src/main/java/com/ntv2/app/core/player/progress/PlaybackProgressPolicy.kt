package com.ntv2.app.core.player.progress

/**
 * Regras puras de "salvar/retomar posição", isoladas para teste JVM.
 * - Não retoma/salva posições triviais (muito perto do início).
 * - Trata posições perto do fim como "assistido" → limpa (para não retomar no finzinho).
 */
object PlaybackProgressPolicy {

    /** Abaixo disso é começo demais para valer a pena retomar/salvar. */
    const val MIN_POSITION_MS = 10_000L

    /** A esta distância do fim, considera-se assistido até o final. */
    const val END_GUARD_MS = 10_000L

    sealed interface SaveAction {
        data class Save(val positionMs: Long, val durationMs: Long) : SaveAction
        data object Clear : SaveAction
        data object Ignore : SaveAction
    }

    fun onProgress(positionMs: Long, durationMs: Long): SaveAction {
        if (durationMs <= 0L) return SaveAction.Ignore
        return when {
            positionMs >= durationMs - END_GUARD_MS -> SaveAction.Clear
            positionMs < MIN_POSITION_MS -> SaveAction.Ignore
            else -> SaveAction.Save(positionMs, durationMs)
        }
    }

    /** Posição de retomada válida, ou 0 se não houver nada útil salvo. */
    fun resumePosition(savedMs: Long, durationMs: Long): Long {
        if (savedMs < MIN_POSITION_MS) return 0L
        val upperBound = if (durationMs > 0L) durationMs - END_GUARD_MS else Long.MAX_VALUE
        return if (savedMs < upperBound) savedMs else 0L
    }
}
