package com.ntv2.app.core.backend.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decide QUANDO sincronizar. Uma rodada pendente por vez; cada pedido só pode trazê-la para mais cedo
 * (ou adiá-la, no caso do debounce de edição):
 * - [requestNow]: abertura do app, volta do background, ida para o background.
 * - [LocalChange.EDIT]: debounce curto (várias edições seguidas viram uma rodada).
 * - [LocalChange.PROGRESS]: os ticks do player não adiam nada; abrem no máximo uma rodada a cada
 *   [progressIntervalMs], que sobe só a última posição de cada mídia (é uma linha por mídia).
 * - [LocalChange.PLAYBACK_SETTLED]: pausou/parou — sobe logo depois do próximo salvamento local.
 *
 * Falhas (sem rede, servidor fora) só são reportadas a [onError]: a próxima rodada tenta de novo, e o
 * que não subiu continua acima da marca de push.
 */
class PersonalizationSyncScheduler(
    private val scope: CoroutineScope,
    private val runSync: suspend () -> Unit,
    private val editDebounceMs: Long = 3_000L,
    private val progressIntervalMs: Long = 60_000L,
    private val settledDelayMs: Long = 12_000L,
    private val onError: (Throwable) -> Unit = {}
) {
    private val lock = Any()
    private var pending: Job? = null

    fun requestNow() = schedule(0L, debounce = true)

    fun onLocalChange(change: LocalChange) = when (change) {
        LocalChange.EDIT -> schedule(editDebounceMs, debounce = true)
        LocalChange.PLAYBACK_SETTLED -> schedule(settledDelayMs, debounce = true)
        LocalChange.PROGRESS -> schedule(progressIntervalMs, debounce = false)
    }

    /**
     * debounce = true: substitui a rodada pendente pela nova (mais cedo ou adiada).
     * debounce = false: só agenda se não houver nada pendente — nunca adia.
     */
    private fun schedule(delayMs: Long, debounce: Boolean) {
        synchronized(lock) {
            // `pending` é só a espera: quando a rodada começa, sai daqui e não é mais cancelada
            // (um pedido novo agenda outra, que o Mutex do syncer põe na fila).
            if (pending != null && !debounce) return
            pending?.cancel()
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                delay(delayMs)
                synchronized(lock) { if (pending === job) pending = null }
                try {
                    runSync()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    onError(e)
                }
            }
            pending = job
            job.start()
        }
    }
}
