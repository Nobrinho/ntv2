package com.ntv2.app.feature.media.data.parts

import com.ntv2.app.core.multipart.MultiPartRegistry
import com.ntv2.app.core.multipart.PartRef
import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.core.telegram.media.TelegramVideoPage
import com.ntv2.app.core.telegram.media.TelegramVideoPartRef
import com.ntv2.app.core.telegram.media.TelegramVideoParts
import com.ntv2.app.feature.media.domain.MultiPartPrepareResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultMultiPartPreparerTest {

    private class FakeGateway(private val parts: () -> TelegramVideoParts?) : TdlibMediaGateway {
        override suspend fun listVideoMessages(chatId: Long, fromMessageId: Long, limit: Int) =
            TelegramVideoPage(emptyList(), 0L)

        override suspend fun searchVideoMessages(chatId: Long, query: String, fromMessageId: Long, limit: Int) =
            TelegramVideoPage(emptyList(), 0L)

        override suspend fun resolveVideoParts(chatId: Long, messageId: Long): TelegramVideoParts? = parts()
    }

    private fun parts(total: Int, missing: List<Int> = emptyList(), size: (Int) -> Long = { 1_000L * it }) =
        TelegramVideoParts(
            baseName = "Duna.mkv",
            total = total,
            // fora de ordem: o preparador precisa ordenar por índice
            parts = (1..total).filter { it !in missing }.reversed()
                .map { TelegramVideoPartRef(index = it, messageId = 100L + it, fileId = 10 + it, sizeBytes = size(it)) },
            missing = missing
        )

    private fun prepare(registry: MultiPartRegistry, found: () -> TelegramVideoParts?) = runBlocking {
        DefaultMultiPartPreparer(FakeGateway(found), registry).prepare(channelId = 1L, messageId = 101L)
    }

    @Test
    fun `partes completas sao registradas em ordem de indice`() {
        val registry = MultiPartRegistry()
        assertEquals(MultiPartPrepareResult.Ready, prepare(registry) { parts(3) })
        assertEquals(
            listOf(PartRef(11, 1_000), PartRef(12, 2_000), PartRef(13, 3_000)),
            registry.partsOf(11)
        )
    }

    @Test
    fun `faltando parte nao registra e informa quais`() {
        val registry = MultiPartRegistry()
        val result = prepare(registry) { parts(4, missing = listOf(2, 4)) }
        assertEquals(MultiPartPrepareResult.Incomplete(listOf(2, 4), 4), result)
        assertNull(registry.partsOf(11))
    }

    @Test
    fun `tamanho desconhecido de uma parte impede tocar`() {
        val registry = MultiPartRegistry()
        val result = prepare(registry) { parts(3) { if (it == 2) 0L else 1_000L } }
        assertEquals(MultiPartPrepareResult.Failed, result)
        assertNull(registry.partsOf(11))
    }

    @Test
    fun `mensagem que nao e parte de filme dividido falha`() {
        assertEquals(MultiPartPrepareResult.Failed, prepare(MultiPartRegistry()) { null })
    }

    @Test
    fun `erro do gateway vira falha e nao derruba o app`() {
        val result = prepare(MultiPartRegistry()) { error("TDLib fora do ar") }
        assertEquals(MultiPartPrepareResult.Failed, result)
    }
}
