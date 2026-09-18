package com.ntv2.app.core.telegram.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TdAuthorizationState {
    data object Unknown : TdAuthorizationState
    data object WaitTdlibParameters : TdAuthorizationState
    data object WaitPhoneNumber : TdAuthorizationState
    data class WaitQrCode(val qrPayload: String) : TdAuthorizationState
    data object WaitCode : TdAuthorizationState
    data object WaitPassword : TdAuthorizationState
    data class Ready(val userId: Long, val displayName: String?) : TdAuthorizationState
    data object LoggingOut : TdAuthorizationState
    data class Closed(val reason: String? = null) : TdAuthorizationState
    /** Sessão encerrada externamente (revogada/deslogada em outro dispositivo), sem ação do usuário. */
    data object SessionExpired : TdAuthorizationState
    data class Error(val message: String) : TdAuthorizationState
}

interface TdlibAuthGateway {
    val authorizationState: Flow<TdAuthorizationState>

    /** Sinal one-shot: a sessão foi revogada externamente. Robusto contra conflação do StateFlow. */
    val sessionRevoked: Flow<Unit>

    suspend fun initialize()
    suspend fun requestQrCodeAuthentication()
    suspend fun setAuthenticationPhoneNumber(phoneNumber: String)
    suspend fun checkAuthenticationCode(code: String)
    suspend fun checkAuthenticationPassword(password: String)
    suspend fun logout()
    suspend fun close()

    /** Sonda leve (GetMe): se a sessão foi revogada, dispara [sessionRevoked]. No-op se deslogado. */
    suspend fun verifySessionActive()
}

class FakeTdlibAuthGateway : TdlibAuthGateway {
    private val state = MutableStateFlow<TdAuthorizationState>(TdAuthorizationState.Unknown)
    override val authorizationState: Flow<TdAuthorizationState> = state.asStateFlow()

    private val revoked = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
    override val sessionRevoked: Flow<Unit> = revoked.asSharedFlow()

    override suspend fun verifySessionActive() {
        // Fake: sessão nunca é revogada externamente.
    }

    override suspend fun initialize() {
        delay(100)
        state.value = TdAuthorizationState.WaitPhoneNumber
    }

    override suspend fun requestQrCodeAuthentication() {
        delay(120)
        state.value = TdAuthorizationState.WaitQrCode(
            qrPayload = "tg://login?token=fake-qr-token-${System.currentTimeMillis()}"
        )
    }

    override suspend fun setAuthenticationPhoneNumber(phoneNumber: String) {
        if (phoneNumber.length < 8) {
            state.value = TdAuthorizationState.Error("Telefone inválido")
            return
        }
        delay(120)
        state.value = TdAuthorizationState.WaitCode
    }

    override suspend fun checkAuthenticationCode(code: String) {
        if (code.length < 4) {
            state.value = TdAuthorizationState.Error("Código inválido")
            return
        }
        delay(120)
        if (code == "0000") {
            state.value = TdAuthorizationState.WaitPassword
        } else {
            state.value = TdAuthorizationState.Ready(
                userId = 123456789L,
                displayName = "Usuário Telegram"
            )
        }
    }

    override suspend fun checkAuthenticationPassword(password: String) {
        if (password.length < 4) {
            state.value = TdAuthorizationState.Error("Senha 2FA inválida")
            return
        }
        delay(120)
        state.value = TdAuthorizationState.Ready(
            userId = 123456789L,
            displayName = "Usuário Telegram"
        )
    }

    override suspend fun logout() {
        state.value = TdAuthorizationState.LoggingOut
        delay(80)
        state.value = TdAuthorizationState.Closed("Logout concluído")
    }

    override suspend fun close() {
        state.value = TdAuthorizationState.Closed("Cliente encerrado")
    }
}
