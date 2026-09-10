package com.ntv2.app.core.telegram.tdlib

import com.ntv2.app.core.telegram.auth.TdAuthorizationState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Máquina de estados de autorização do TDLib, pura e testável (sem JNI/Client).
 * O [RealTdlibGateway] traduz os `TdApi.AuthorizationState*` para [Update], aplica o [Result]
 * (estado emitido + efeitos colaterais a executar).
 *
 * Encapsula o guard que corrigiu o travamento no login: SetTdlibParameters deve ser enviado
 * UMA vez por cliente — reenviar após Ready resetava o TDLib (Ready → WaitTdlibParameters).
 */
internal class TdlibAuthReducer {

    sealed interface Update {
        data object WaitTdlibParameters : Update
        data object WaitPhoneNumber : Update
        data object WaitCode : Update
        data object WaitPassword : Update
        data class WaitOtherDeviceConfirmation(val link: String) : Update
        data object Ready : Update
        data object LoggingOut : Update
        data class Closed(val reason: String?) : Update
        data object Closing : Update
        data class Failure(val message: String) : Update
    }

    sealed interface Effect {
        /** Enviar SetTdlibParameters (uma vez por cliente). */
        data object SendParameters : Effect
        /** Buscar o usuário atual (GetMe) e então emitir Ready com userId/nome. */
        data object FetchCurrentUser : Effect
        /** Zerar a referência do cliente (fechado); o próximo login cria um novo. */
        data object ClearClient : Effect
    }

    data class Result(
        val state: TdAuthorizationState?,
        val effects: List<Effect> = emptyList()
    )

    private val parametersSent = AtomicBoolean(false)

    /** Ao criar um cliente novo: o próximo WaitTdlibParameters deve reenviar os parâmetros. */
    fun onClientCreated() {
        parametersSent.set(false)
    }

    /** Se o envio de SetTdlibParameters falhar, permite nova tentativa. */
    fun onSendParametersFailed() {
        parametersSent.set(false)
    }

    fun reduce(update: Update): Result = when (update) {
        Update.WaitTdlibParameters -> Result(
            state = TdAuthorizationState.WaitTdlibParameters,
            // compareAndSet garante envio único mesmo sob concorrência (thread de callback do TDLib).
            effects = if (parametersSent.compareAndSet(false, true)) listOf(Effect.SendParameters) else emptyList()
        )

        Update.WaitPhoneNumber -> Result(TdAuthorizationState.WaitPhoneNumber)
        Update.WaitCode -> Result(TdAuthorizationState.WaitCode)
        Update.WaitPassword -> Result(TdAuthorizationState.WaitPassword)
        is Update.WaitOtherDeviceConfirmation -> Result(TdAuthorizationState.WaitQrCode(update.link))

        // Ready: userId/nome vêm de GetMe (assíncrono). O gateway emite o estado final após o efeito.
        Update.Ready -> Result(state = null, effects = listOf(Effect.FetchCurrentUser))

        Update.LoggingOut -> Result(TdAuthorizationState.LoggingOut)
        is Update.Closed -> Result(TdAuthorizationState.Closed(update.reason), listOf(Effect.ClearClient))
        Update.Closing -> Result(TdAuthorizationState.Closed("Closing session"))
        is Update.Failure -> Result(TdAuthorizationState.Error(update.message))
    }
}
