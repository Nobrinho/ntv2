package com.ntv2.app.core.player.io

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Libera trechos do disco (punch hole) numa thread própria de baixa prioridade, em blocos pequenos
 * com pausa entre eles. Antes a liberação rodava na thread que carrega o vídeo: enquanto o ext4
 * travava o arquivo para liberar os blocos, o player não lia e o TDLib não gravava.
 *
 * [cancel] invalida os trabalhos pendentes do arquivo e espera o bloco em andamento terminar —
 * obrigatório antes de apagar/recriar o arquivo, senão um trabalho antigo poderia liberar bytes
 * da cópia NOVA (o TDLib pode reutilizar o mesmo caminho).
 */
internal class DiskEvictor(
    private val puncher: HolePuncher,
    private val chunkBytes: Long = 8L * MB,
    private val pauseMs: Long = 40L
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ntv-disk-evictor").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }
    }
    private val generations = ConcurrentHashMap<Int, AtomicInteger>()
    private val lock = Any()

    private fun generation(fileId: Int): AtomicInteger = generations.computeIfAbsent(fileId) { AtomicInteger() }

    fun submit(fileId: Int, path: String, start: Long, end: Long, onProgress: (Long) -> Unit = {}) {
        if (end <= start) return
        val gen = generation(fileId).get()
        executor.execute {
            val startedAt = SystemClock.elapsedRealtime()
            var position = start
            var slowestMs = 0L
            while (position < end) {
                val length = min(chunkBytes, end - position)
                var cancelled = false
                val ok = synchronized(lock) {
                    if (generation(fileId).get() != gen) {
                        cancelled = true
                        false
                    } else {
                        val t0 = SystemClock.elapsedRealtime()
                        val punched = puncher.punch(path, position, length)
                        slowestMs = max(slowestMs, SystemClock.elapsedRealtime() - t0)
                        punched
                    }
                }
                if (!ok) {
                    if (cancelled) Log.i(TAG, "liberação cancelada fileId=$fileId em ${position / MB}MB")
                    break
                }
                position += length
                // Só publica como liberado aquilo que o sistema de arquivos confirmou. Assim uma
                // falha no punch hole nunca faz o player tratar bytes válidos como ausentes.
                onProgress(position)
                if (position < end) Thread.sleep(pauseMs)
            }
            if (position > start) {
                Log.i(
                    TAG,
                    "liberado fileId=$fileId ${start / MB}-${position / MB}MB em " +
                        "${SystemClock.elapsedRealtime() - startedAt}ms (maior bloco ${slowestMs}ms)"
                )
            }
        }
    }

    /** Invalida as liberações pendentes de [fileId] e espera o bloco em andamento terminar. */
    fun cancel(fileId: Int) {
        synchronized(lock) { generation(fileId).incrementAndGet() }
    }

    private companion object {
        const val TAG = "NtvDiskWindow"
        const val MB = 1024L * 1024L
    }
}
