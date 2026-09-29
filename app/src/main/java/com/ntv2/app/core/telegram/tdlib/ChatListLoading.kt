package com.ntv2.app.core.telegram.tdlib

import kotlinx.coroutines.delay

/** Resultado de uma chamada LoadChats. */
internal enum class LoadChatsOutcome {
    /** Carregou mais chats: pode haver mais. */
    LOADED,
    /** Erro 404: a lista acabou. */
    END,
    /** Outro erro (rede, limite do servidor): vale tentar de novo. */
    ERROR
}

/**
 * Carregamento da lista de chats do TDLib (lógica pura, testável na JVM).
 *
 * Antes: o laço parava no PRIMEIRO erro de qualquer tipo — uma falha passageira de rede na 1ª carga
 * deixava a lista incompleta em silêncio (canais faltando até recarregar). Agora só o 404 ("acabou")
 * encerra; outros erros têm novas tentativas com espera.
 */
internal object ChatListLoading {
    const val MAX_RETRIES = 2
    const val RETRY_DELAY_MS = 1_000L

    /** Lista vazia logo após o login: novas tentativas (espera crescente: 2 s, 4 s, 6 s). */
    const val EMPTY_LIST_ATTEMPTS = 4
    const val EMPTY_LIST_RETRY_MS = 2_000L
    /** Quanto esperar a conexão com o Telegram ficar pronta antes de cada tentativa. */
    const val CONNECTION_WAIT_MS = 15_000L

    /**
     * Principal e arquivados vazios: a sessão ainda não sincronizou (conta logada sempre tem ao menos
     * um chat — no mínimo o do próprio Telegram).
     */
    fun looksUnsynced(main: LongArray, archive: LongArray): Boolean = main.isEmpty() && archive.isEmpty()

    /**
     * Chama [loadMore] até a lista acabar, até [maxCalls] carregamentos ou até esgotar as novas
     * tentativas de erro. Retorna true se chegou ao fim da lista.
     */
    suspend fun loadUntilEnd(
        maxCalls: Int,
        retryDelayMs: Long = RETRY_DELAY_MS,
        loadMore: suspend () -> LoadChatsOutcome
    ): Boolean {
        var calls = 0
        var retries = 0
        while (calls < maxCalls) {
            when (loadMore()) {
                LoadChatsOutcome.END -> return true
                LoadChatsOutcome.LOADED -> {
                    calls++
                    retries = 0
                }
                LoadChatsOutcome.ERROR -> {
                    if (retries >= MAX_RETRIES) return false
                    retries++
                    delay(retryDelayMs)
                }
            }
        }
        return false
    }

    /**
     * Junta os ids da lista principal com os dos arquivados (canais arquivados no Telegram ficavam
     * de fora), sem repetir, na ordem: principal e depois arquivados; no máximo [limit].
     */
    fun mergeChatIds(main: LongArray, archive: LongArray, limit: Int): List<Long> =
        (main.asSequence() + archive.asSequence()).distinct().take(limit).toList()
}
