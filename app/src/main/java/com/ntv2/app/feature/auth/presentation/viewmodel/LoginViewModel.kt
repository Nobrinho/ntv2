package com.ntv2.app.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.auth.domain.AuthRepository
import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode
import com.ntv2.app.feature.auth.presentation.state.LoginAction
import com.ntv2.app.feature.auth.presentation.state.LoginUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    // Modo inicial por tipo de dispositivo: TV começa no QR Code, celular no telefone.
    initialMode: LoginMode = LoginMode.QrCode
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(loginMode = initialMode))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var qrRequested = false
    private var qrRequestInFlight = false
    private var cooldownJob: Job? = null
    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                // Saiu do fluxo de código/telefone: encerra o modo "editar telefone".
                val leftCredentialEntry = authState.step is AuthStep.WaitingPassword ||
                    authState.step is AuthStep.Authorized ||
                    authState.step is AuthStep.WaitingQrScan
                _uiState.update {
                    it.copy(
                        authStep = authState.step,
                        qrCodePayload = authState.qrCodePayload,
                        isLoading = authState.isLoading,
                        errorMessage = authState.errorMessage,
                        isAuthorized = authState.step is AuthStep.Authorized,
                        editingPhone = if (leftCredentialEntry) false else it.editingPhone
                    )
                }
                // Reseta o gatilho quando sai do estado de aguardar credenciais — assim, se o
                // cliente for recriado (ex.: após logout: Closed → Initializing → WaitPhoneNumber),
                // o QR é solicitado de novo em vez de ficar "gerando" para sempre.
                if (authState.step !is AuthStep.WaitingPhoneNumber &&
                    authState.step !is AuthStep.WaitingQrScan
                ) {
                    qrRequested = false
                }
                maybeRequestQr()
            }
        }

        onAction(LoginAction.Initialize)
    }

    private fun startResendCooldown(seconds: Int = RESEND_COOLDOWN_SECONDS) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _uiState.update { it.copy(resendCooldownSeconds = 0) }
        }
    }

    // Em modo QR, quando o TDLib está aguardando credenciais, solicita o QR uma vez. Aceita também
    // sair de WaitCode/WaitPassword (ex.: usuário começou pelo telefone e alternou para QR): o
    // gateway recria o cliente se preciso para voltar a WaitPhoneNumber antes de pedir o QR.
    private fun maybeRequestQr() {
        val s = _uiState.value
        val awaitingCredentials = s.authStep is AuthStep.WaitingPhoneNumber ||
            s.authStep is AuthStep.WaitingCode ||
            s.authStep is AuthStep.WaitingPassword
        if (s.loginMode == LoginMode.QrCode &&
            awaitingCredentials &&
            s.qrCodePayload == null &&
            !qrRequested
        ) {
            qrRequested = true
            requestQr()
        }
    }

    fun onAction(action: LoginAction) {
        when (action) {
            LoginAction.Initialize -> initialize()
            is LoginAction.SwitchMode -> switchMode(action.mode)
            LoginAction.RequestQr -> requestQr()
            is LoginAction.UpdatePhone -> _uiState.update {
                it.copy(phoneNumber = coerceBrazilPhone(action.value))
            }
            is LoginAction.UpdateCode -> _uiState.update { it.copy(code = action.value) }
            is LoginAction.UpdatePassword -> _uiState.update { it.copy(password = action.value) }
            LoginAction.SubmitPhone -> submitPhone()
            LoginAction.SubmitCode -> submitCode()
            LoginAction.SubmitPassword -> submitPassword()
            LoginAction.EditPhone -> _uiState.update {
                it.copy(editingPhone = true, code = "", errorMessage = null)
            }
            LoginAction.ResendCode -> resendCode()
            LoginAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun initialize() {
        viewModelScope.launch {
            // O flag persistido (DataStore) NÃO recria a sessão do TDLib. É preciso sempre
            // inicializar o TDLib: ele restaura a sessão salva e emite Authorized, ou volta a
            // pedir login. A navegação passa a depender do estado real (Authorized), evitando
            // entrar na biblioteca com o cliente ainda não pronto (canais vazios).
            authRepository.initialize()
        }
    }

    private fun switchMode(mode: LoginMode) {
        _uiState.update {
            it.copy(
                loginMode = mode,
                editingPhone = false,
                errorMessage = null
            )
        }

        if (mode == LoginMode.QrCode) {
            // Permite pedir um QR novo (ex.: veio do modo telefone) e solicita se já estiver pronto.
            qrRequested = false
            maybeRequestQr()
        }
    }

    private fun requestQr() {
        // Idempotente: durante a recriação do cliente (transições Closed→WaitPhoneNumber) o collect
        // pode chamar de novo; o guard evita disparar dois RequestQrCodeAuthentication concorrentes.
        if (qrRequestInFlight) return
        qrRequestInFlight = true
        viewModelScope.launch {
            try {
                authRepository.requestQrLogin()
            } finally {
                qrRequestInFlight = false
            }
        }
    }

    private fun submitPhone() {
        val national = nationalOf(_uiState.value.phoneNumber)
        if (!isValidBrazilMobile(national)) {
            _uiState.update {
                it.copy(errorMessage = "Número inválido. Informe DDD + celular (11 dígitos), ex.: (11) 99999-9999.")
            }
            return
        }

        // Reenvio explícito volta ao fluxo normal de código.
        _uiState.update { it.copy(editingPhone = false) }
        viewModelScope.launch {
            authRepository.submitPhoneNumber("+55$national")
        }
    }

    private fun resendCode() {
        // Bloqueia reenvios em sequência (flood): só libera quando o cooldown zera.
        if (_uiState.value.resendCooldownSeconds > 0) return
        val national = nationalOf(_uiState.value.phoneNumber)
        if (!isValidBrazilMobile(national)) return
        startResendCooldown()
        viewModelScope.launch {
            authRepository.submitPhoneNumber("+55$national")
        }
    }

    private fun submitCode() {
        val code = _uiState.value.code.trim()
        if (code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o código") }
            return
        }

        viewModelScope.launch {
            authRepository.submitCode(code)
        }
    }

    private fun submitPassword() {
        val password = _uiState.value.password
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe a senha 2FA") }
            return
        }

        viewModelScope.launch {
            authRepository.submitPassword(password)
        }
    }
}

/** DDDs válidos no Brasil (evita aceitar códigos de área inexistentes). */
private val BRAZIL_DDDS: Set<Int> = setOf(
    11, 12, 13, 14, 15, 16, 17, 18, 19,
    21, 22, 24, 27, 28,
    31, 32, 33, 34, 35, 37, 38,
    41, 42, 43, 44, 45, 46, 47, 48, 49,
    51, 53, 54, 55,
    61, 62, 63, 64, 65, 66, 67, 68, 69,
    71, 73, 74, 75, 77, 79,
    81, 82, 83, 84, 85, 86, 87, 88, 89,
    91, 92, 93, 94, 95, 96, 97, 98, 99
)

/**
 * O campo mostra só a parte nacional (DDD + número, máx. 11 dígitos). O DDI +55 é aplicado no envio.
 * Se o usuário colar um número com +55, o DDI é removido para não duplicar.
 */
private fun coerceBrazilPhone(input: String): String {
    var d = input.filter { it.isDigit() }
    if (d.length > 11 && d.startsWith("55")) d = d.substring(2)
    return d.take(11)
}

/** Parte nacional (DDD + número) já normalizada, só dígitos. */
private fun nationalOf(display: String): String = coerceBrazilPhone(display)

/** Valida celular BR: DDD válido + 9 dígitos começando com 9 (total 11 dígitos nacionais). */
private fun isValidBrazilMobile(national: String): Boolean {
    if (national.length != 11) return false
    val ddd = national.substring(0, 2).toIntOrNull() ?: return false
    if (ddd !in BRAZIL_DDDS) return false
    return national[2] == '9'
}

class LoginViewModelFactory(
    private val authRepository: AuthRepository,
    private val initialMode: LoginMode = LoginMode.QrCode
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(authRepository, initialMode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
