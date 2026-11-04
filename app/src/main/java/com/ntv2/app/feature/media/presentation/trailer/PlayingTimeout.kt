package com.ntv2.app.feature.media.presentation.trailer

/**
 * Prazo para o trailer começar a tocar, contado só com a tela em primeiro plano: cada trecho em
 * primeiro plano é somado com [consume]; em segundo plano o prazo fica parado.
 */
internal class PlayingTimeout(private val totalMs: Long) {
    private var usedMs = 0L

    val remainingMs: Long get() = (totalMs - usedMs).coerceAtLeast(0L)

    fun consume(elapsedMs: Long) {
        usedMs += elapsedMs.coerceAtLeast(0L)
    }
}
