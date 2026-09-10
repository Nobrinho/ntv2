package com.ntv2.app.core.telegram.tdlib

import com.ntv2.app.core.telegram.auth.TdAuthorizationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TdlibAuthReducerTest {

    private val reducer = TdlibAuthReducer()

    @Test
    fun `primeiro WaitTdlibParameters envia parametros uma vez`() {
        val result = reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        assertEquals(TdAuthorizationState.WaitTdlibParameters, result.state)
        assertEquals(listOf(TdlibAuthReducer.Effect.SendParameters), result.effects)
    }

    @Test
    fun `segundo WaitTdlibParameters no mesmo cliente NAO reenvia parametros`() {
        reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        val second = reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        // Este era o bug: reenviar SetTdlibParameters após Ready resetava o TDLib.
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun `cliente novo permite reenviar parametros`() {
        reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        reducer.onClientCreated()
        val afterNewClient = reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        assertEquals(listOf(TdlibAuthReducer.Effect.SendParameters), afterNewClient.effects)
    }

    @Test
    fun `falha no envio permite nova tentativa`() {
        reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        reducer.onSendParametersFailed()
        val retry = reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
        assertEquals(listOf(TdlibAuthReducer.Effect.SendParameters), retry.effects)
    }

    @Test
    fun `ready nao emite estado imediato e pede o usuario atual`() {
        val result = reducer.reduce(TdlibAuthReducer.Update.Ready)
        assertEquals(null, result.state)
        assertEquals(listOf(TdlibAuthReducer.Effect.FetchCurrentUser), result.effects)
    }

    @Test
    fun `closed emite Closed e pede limpar o cliente`() {
        val result = reducer.reduce(TdlibAuthReducer.Update.Closed(reason = null))
        assertTrue(result.state is TdAuthorizationState.Closed)
        assertEquals(listOf(TdlibAuthReducer.Effect.ClearClient), result.effects)
    }

    @Test
    fun `closing emite Closed sem limpar o cliente`() {
        val result = reducer.reduce(TdlibAuthReducer.Update.Closing)
        assertTrue(result.state is TdAuthorizationState.Closed)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `mapeia estados de entrada de credenciais`() {
        assertEquals(
            TdAuthorizationState.WaitPhoneNumber,
            reducer.reduce(TdlibAuthReducer.Update.WaitPhoneNumber).state
        )
        assertEquals(
            TdAuthorizationState.WaitCode,
            reducer.reduce(TdlibAuthReducer.Update.WaitCode).state
        )
        assertEquals(
            TdAuthorizationState.WaitPassword,
            reducer.reduce(TdlibAuthReducer.Update.WaitPassword).state
        )
    }

    @Test
    fun `qr code propaga o link`() {
        val result = reducer.reduce(TdlibAuthReducer.Update.WaitOtherDeviceConfirmation("tg://login?token=abc"))
        assertEquals(TdAuthorizationState.WaitQrCode("tg://login?token=abc"), result.state)
    }

    @Test
    fun `failure mapeia para Error com a mensagem`() {
        val result = reducer.reduce(TdlibAuthReducer.Update.Failure("boom"))
        assertEquals(TdAuthorizationState.Error("boom"), result.state)
    }

    @Test
    fun `guard e atomico sob concorrencia - apenas um envio`() {
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val barrier = CyclicBarrier(threads)
            val sendCount = AtomicInteger(0)
            val tasks = (1..threads).map {
                pool.submit {
                    barrier.await(5, TimeUnit.SECONDS)
                    val r = reducer.reduce(TdlibAuthReducer.Update.WaitTdlibParameters)
                    if (r.effects.contains(TdlibAuthReducer.Effect.SendParameters)) {
                        sendCount.incrementAndGet()
                    }
                }
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals("apenas uma thread deve enviar os parametros", 1, sendCount.get())
        } finally {
            pool.shutdownNow()
        }
    }
}
