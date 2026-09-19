package com.ntv2.app.core.ui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import coil.EventListener
import coil.decode.DataSource
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Mede o carregamento das capas em "lotes" (tag NtvCovers): um lote começa quando a 1ª imagem
 * começa a carregar e termina quando não há mais nenhuma em andamento. Registra quantas vieram da
 * rede, do disco e da memória — base para comparar antes/depois de ajustes no Fire TV.
 */
class CoverTimingListener : EventListener {
    private var inFlight = 0
    private var batchStartedAt = 0L
    private var network = 0
    private var disk = 0
    private var memory = 0
    private var errors = 0

    @Synchronized
    override fun onStart(request: ImageRequest) {
        if (inFlight == 0) {
            batchStartedAt = SystemClock.elapsedRealtime()
            network = 0; disk = 0; memory = 0; errors = 0
        }
        inFlight++
    }

    @Synchronized
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        when (result.dataSource) {
            DataSource.NETWORK -> network++
            DataSource.DISK -> disk++
            DataSource.MEMORY_CACHE, DataSource.MEMORY -> memory++
        }
        finishOne()
    }

    @Synchronized
    override fun onError(request: ImageRequest, result: ErrorResult) {
        errors++
        finishOne()
    }

    @Synchronized
    override fun onCancel(request: ImageRequest) = finishOne()

    private fun finishOne() {
        if (inFlight == 0) return
        inFlight--
        if (inFlight == 0) {
            val total = network + disk + memory + errors
            // Lotes só de memória são instantâneos (rolar a grade): não poluem o log.
            if (total > 0 && memory < total) {
                Log.i(
                    TAG,
                    "lote de $total capas em ${SystemClock.elapsedRealtime() - batchStartedAt}ms " +
                        "(rede=$network disco=$disk memória=$memory erros=$errors)"
                )
            }
        }
    }

    private companion object {
        const val TAG = "NtvCovers"
    }
}

/**
 * Pré-carrega capas remotas (URLs) no cache de disco antes de os cards aparecerem, para as linhas
 * de baixo já estarem prontas ao rolar. Decodifica em tamanho mínimo e sem cache de memória: o
 * objetivo é só ter os bytes no disco (o card decodifica no tamanho dele depois).
 */
fun prefetchCovers(context: Context, urls: List<String>) {
    val loader = context.imageLoader
    urls.asSequence()
        .filter { it.startsWith("http") }
        .distinct()
        .forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(48)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
}
