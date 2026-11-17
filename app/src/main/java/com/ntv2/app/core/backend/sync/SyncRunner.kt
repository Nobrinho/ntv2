package com.ntv2.app.core.backend.sync

import com.ntv2.app.core.backend.BackendException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** Estado da sincronização mostrado na tela (Conectar aparelhos). */
data class SyncStatus(
    val running: Boolean = false,
    /** Última rodada que terminou sem erro (0 = nunca neste aparelho). */
    val lastSuccessAtMs: Long = 0L
)

/** Resultado de um "Sincronizar agora". */
sealed interface SyncOutcome {
    data class Done(val sent: Int, val received: Int) : SyncOutcome
    /** O servidor desligou o sync (`flags.sync`) ou o backend não está configurado. */
    data object Disabled : SyncOutcome
    data object PremiumRequired : SyncOutcome
    data object Offline : SyncOutcome
    data object RateLimited : SyncOutcome
    /** Toque repetido antes do intervalo mínimo. */
    data object TooSoon : SyncOutcome
    data object Failed : SyncOutcome
}

/**
 * Roda a sincronização e guarda o estado para a tela. O agendador automático passa por [auto]; o botão
 * "Sincronizar agora" por [syncNow], que refaz tudo (marcas zeradas) e devolve o que aconteceu.
 */
class SyncRunner(
    private val run: suspend (forceFull: Boolean) -> SyncResult,
    loadLastSuccess: () -> Long = { 0L },
    private val saveLastSuccess: (Long) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    /** Protege o limite do backend (60 escritas/min por tabela) de toques seguidos. */
    private val minIntervalMs: Long = 10_000L
) {
    private val _status = MutableStateFlow(SyncStatus(lastSuccessAtMs = loadLastSuccess()))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    @Volatile
    private var lastManualAt = 0L

    @Volatile
    private var fullPending = false

    /**
     * A conta mudou (conectou ou saiu): a próxima rodada automática sobe e baixa tudo. Sem isso, quem
     * digita o código mantém as marcas antigas e não baixa as linhas copiadas do outro aparelho, que
     * chegam com datas anteriores à marca.
     */
    fun requestFullNext() {
        fullPending = true
    }

    /** Rodada automática: erros sobem para o agendador (que só registra). */
    suspend fun auto() {
        val full = fullPending
        fullPending = false
        val result = try {
            run(full)
        } catch (e: Throwable) {
            if (full) fullPending = true
            throw e
        }
        if (result.skipped == null) markSuccess()
    }

    suspend fun syncNow(): SyncOutcome {
        val startedAt = now()
        if (lastManualAt != 0L && startedAt - lastManualAt < minIntervalMs) return SyncOutcome.TooSoon
        lastManualAt = startedAt
        _status.value = _status.value.copy(running = true)
        return try {
            val result = run(true)
            when (result.skipped) {
                null -> {
                    markSuccess()
                    SyncOutcome.Done(sent = result.pushed.values.sum(), received = result.pulled.values.sum())
                }
                PersonalizationSyncer.SKIP_NO_PREMIUM -> SyncOutcome.PremiumRequired
                PersonalizationSyncer.SKIP_NO_SESSION -> SyncOutcome.Offline
                else -> SyncOutcome.Disabled
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: BackendException) {
            if (e.isRateLimited) SyncOutcome.RateLimited else SyncOutcome.Failed
        } catch (e: IOException) {
            SyncOutcome.Offline
        } catch (e: Exception) {
            SyncOutcome.Failed
        } finally {
            _status.value = _status.value.copy(running = false)
        }
    }

    private fun markSuccess() {
        val at = now()
        _status.value = _status.value.copy(lastSuccessAtMs = at)
        runCatching { saveLastSuccess(at) }
    }

    companion object {
        /** Texto do resultado para a tela. */
        fun message(outcome: SyncOutcome): String = when (outcome) {
            is SyncOutcome.Done -> when {
                outcome.received == 0 -> "Tudo em dia."
                else -> "${plural(outcome.received, "item recebido", "itens recebidos")}. Tudo em dia."
            }
            SyncOutcome.Disabled -> "A sincronização está desligada no momento."
            SyncOutcome.PremiumRequired -> "Sincronizar entre aparelhos é um recurso do NBR Play Premium."
            SyncOutcome.Offline -> "Sem internet. Verifique a conexão e tente de novo."
            SyncOutcome.RateLimited -> "Muitas tentativas. Aguarde um minuto e tente de novo."
            SyncOutcome.TooSoon -> "Acabou de sincronizar. Aguarde alguns segundos."
            SyncOutcome.Failed -> "Não foi possível sincronizar. Tente de novo."
        }

        /** "Última sincronização: há 3 min" (null = nunca sincronizou). */
        fun lastSyncText(lastSuccessAtMs: Long, nowMs: Long): String? {
            if (lastSuccessAtMs <= 0L) return null
            val minutes = ((nowMs - lastSuccessAtMs).coerceAtLeast(0L)) / 60_000L
            val ago = when {
                minutes < 1 -> "agora mesmo"
                minutes < 60 -> "há $minutes min"
                minutes < 24 * 60 -> "há ${minutes / 60} h"
                else -> "há ${plural((minutes / (24 * 60)).toInt(), "dia", "dias")}"
            }
            return "Última sincronização: $ago"
        }

        private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    }
}
