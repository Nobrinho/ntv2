package com.ntv2.app.feature.auth.data.datasource

import com.ntv2.app.core.telegram.auth.TdAuthorizationState
import com.ntv2.app.core.telegram.auth.TdlibAuthGateway
import com.ntv2.app.feature.auth.data.session.AuthSessionStore
import com.ntv2.app.feature.auth.domain.model.AuthSession
import com.ntv2.app.feature.auth.domain.model.AuthState
import com.ntv2.app.feature.auth.domain.model.AuthStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TdlibTelegramAuthDataSource(
    private val tdlibGateway: TdlibAuthGateway,
    private val sessionStore: AuthSessionStore
) : TelegramAuthDataSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(AuthState(step = AuthStep.Idle))
    override val authState: Flow<AuthState> = state.asStateFlow()

    init {
        scope.launch {
            tdlibGateway.authorizationState.collect { tdState ->
                mapTdState(tdState)
            }
        }
    }

    override suspend fun initialize() {
        // Este data source é singleton (vive enquanto o processo vive). initialize() é chamado
        // toda vez que a LoginScreen é composta (nova LoginViewModel a cada recriação da Activity).
        // NÃO podemos sobrescrever um estado já resolvido (ex.: Authorized) com Initializing: se o
        // cliente TDLib já existe, ele não reemite o estado atual e a tela travava em "Initializing"
        // pedindo QR pra sempre. Só mostramos Initializing quando ainda não há estado definido.
        val current = state.value.step
        val alreadyResolved = current != AuthStep.Idle &&
            current != AuthStep.Initializing &&
            current !is AuthStep.Failed
        if (!alreadyResolved) {
            state.update { it.copy(isLoading = true, step = AuthStep.Initializing, errorMessage = null) }
        }
        runCatching {
            tdlibGateway.initialize()
        }.onFailure { error ->
            state.update {
                it.copy(
                    step = AuthStep.Failed(error.message ?: "Falha ao inicializar TDLib"),
                    errorMessage = error.message ?: "Falha ao inicializar TDLib"
                )
            }
        }
        state.update { it.copy(isLoading = false) }
    }

    override suspend fun requestQrLogin() {
        state.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching { tdlibGateway.requestQrCodeAuthentication() }
            .onFailure { error ->
                state.update {
                    it.copy(errorMessage = error.message ?: "Falha ao solicitar QR Code")
                }
            }
        state.update { it.copy(isLoading = false) }
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        state.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching { tdlibGateway.setAuthenticationPhoneNumber(phoneNumber) }
            .onFailure { error ->
                state.update {
                    it.copy(errorMessage = error.message ?: "Falha ao enviar telefone")
                }
            }
        state.update { it.copy(isLoading = false) }
    }

    override suspend fun submitCode(code: String) {
        state.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching { tdlibGateway.checkAuthenticationCode(code) }
            .onFailure { error ->
                state.update {
                    it.copy(errorMessage = error.message ?: "Falha ao validar código")
                }
            }
        state.update { it.copy(isLoading = false) }
    }

    override suspend fun submitPassword(password: String) {
        state.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching { tdlibGateway.checkAuthenticationPassword(password) }
            .onFailure { error ->
                state.update {
                    it.copy(errorMessage = error.message ?: "Falha ao validar senha")
                }
            }
        state.update { it.copy(isLoading = false) }
    }

    override suspend fun logout() {
        // Zera o estado autorizado IMEDIATAMENTE: sem isso a tela de Login (recriada logo após o
        // logout) enxergava um Authorized obsoleto e re-navegava para a biblioteca/seleção.
        state.update { it.copy(isLoading = true, step = AuthStep.Initializing, errorMessage = null) }
        runCatching { tdlibGateway.logout() }
            .onFailure { error ->
                state.update {
                    it.copy(errorMessage = error.message ?: "Falha ao efetuar logout")
                }
            }
        sessionStore.clear()
        // Não forçamos WaitingPhoneNumber aqui: o gateway recria o cliente e os updates reais
        // (WaitTdlibParameters → WaitPhoneNumber) conduzem o estado. Assim o QR só é pedido
        // quando o cliente novo estiver de fato pronto (evita pedido prematuro no re-login).
        state.update {
            it.copy(
                isLoading = false,
                qrCodePayload = null,
                session = AuthSession(isLoggedIn = false, userId = null, displayName = null)
            )
        }
    }

    override suspend fun close() {
        runCatching { tdlibGateway.close() }
    }

    override suspend fun currentSession(): AuthSession = sessionStore.session.first()

    private fun mapTdState(tdState: TdAuthorizationState) {
        when (tdState) {
            TdAuthorizationState.Unknown,
            TdAuthorizationState.WaitTdlibParameters -> {
                state.update { it.copy(step = AuthStep.Initializing) }
            }

            TdAuthorizationState.WaitPhoneNumber -> {
                state.update {
                    it.copy(
                        step = AuthStep.WaitingPhoneNumber,
                        qrCodePayload = null,
                        errorMessage = null
                    )
                }
            }

            is TdAuthorizationState.WaitQrCode -> {
                state.update {
                    it.copy(
                        step = AuthStep.WaitingQrScan,
                        qrCodePayload = tdState.qrPayload,
                        errorMessage = null
                    )
                }
            }

            TdAuthorizationState.WaitCode -> {
                state.update { it.copy(step = AuthStep.WaitingCode, errorMessage = null) }
            }

            TdAuthorizationState.WaitPassword -> {
                state.update { it.copy(step = AuthStep.WaitingPassword, errorMessage = null) }
            }

            is TdAuthorizationState.Ready -> {
                val authSession = AuthSession(
                    isLoggedIn = true,
                    userId = tdState.userId,
                    displayName = tdState.displayName
                )
                scope.launch {
                    sessionStore.save(authSession)
                }
                state.update {
                    it.copy(
                        step = AuthStep.Authorized,
                        errorMessage = null,
                        session = authSession
                    )
                }
            }

            TdAuthorizationState.LoggingOut -> {
                state.update { it.copy(isLoading = true) }
            }

            is TdAuthorizationState.Closed -> {
                // Cliente fechado (ex.: pós-logout). O gateway recria o cliente; ficamos em
                // Initializing até o WaitPhoneNumber real chegar, para o QR não ser pedido cedo.
                state.update {
                    it.copy(
                        isLoading = false,
                        step = AuthStep.Initializing,
                        qrCodePayload = null,
                        errorMessage = tdState.reason
                    )
                }
            }

            is TdAuthorizationState.Error -> {
                state.update {
                    it.copy(
                        isLoading = false,
                        step = AuthStep.Failed(tdState.message),
                        errorMessage = tdState.message
                    )
                }
            }
        }
    }
}
