package com.ntv2.app.core.telegram.tdlib

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListLoadingTest {

    private fun outcomes(vararg values: LoadChatsOutcome): Pair<suspend () -> LoadChatsOutcome, () -> Int> {
        var i = 0
        return Pair({ values[i++] }, { i })
    }

    @Test
    fun `carrega ate o 404`() = runTest {
        val (load, calls) = outcomes(LoadChatsOutcome.LOADED, LoadChatsOutcome.LOADED, LoadChatsOutcome.END)
        assertTrue(ChatListLoading.loadUntilEnd(maxCalls = 10, retryDelayMs = 0L, loadMore = load))
        assertEquals(3, calls())
    }

    @Test
    fun `erro passageiro tenta de novo e continua`() = runTest {
        // Antes o 1º erro encerrava o laço e a lista ficava incompleta.
        val (load, calls) = outcomes(
            LoadChatsOutcome.LOADED, LoadChatsOutcome.ERROR, LoadChatsOutcome.LOADED, LoadChatsOutcome.END
        )
        assertTrue(ChatListLoading.loadUntilEnd(maxCalls = 10, retryDelayMs = 0L, loadMore = load))
        assertEquals(4, calls())
    }

    @Test
    fun `erros seguidos esgotam as tentativas`() = runTest {
        val (load, calls) = outcomes(
            LoadChatsOutcome.ERROR, LoadChatsOutcome.ERROR, LoadChatsOutcome.ERROR, LoadChatsOutcome.END
        )
        assertFalse(ChatListLoading.loadUntilEnd(maxCalls = 10, retryDelayMs = 0L, loadMore = load))
        assertEquals(ChatListLoading.MAX_RETRIES + 1, calls())
    }

    @Test
    fun `respeita o maximo de carregamentos`() = runTest {
        val (load, calls) = outcomes(*Array(10) { LoadChatsOutcome.LOADED })
        assertFalse(ChatListLoading.loadUntilEnd(maxCalls = 3, retryDelayMs = 0L, loadMore = load))
        assertEquals(3, calls())
    }

    @Test
    fun `junta principal e arquivados sem repetir e respeita o limite`() {
        val merged = ChatListLoading.mergeChatIds(longArrayOf(1, 2, 3), longArrayOf(3, 4, 5), limit = 4)
        assertEquals(listOf(1L, 2L, 3L, 4L), merged)
        assertEquals(listOf(7L), ChatListLoading.mergeChatIds(longArrayOf(), longArrayOf(7), limit = 10))
    }

    @Test
    fun `lista vazia nas duas listas indica sessao ainda sincronizando`() {
        assertTrue(ChatListLoading.looksUnsynced(longArrayOf(), longArrayOf()))
        assertFalse(ChatListLoading.looksUnsynced(longArrayOf(777000), longArrayOf()))
        assertFalse(ChatListLoading.looksUnsynced(longArrayOf(), longArrayOf(1)))
    }
}
