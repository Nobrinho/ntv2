package com.ntv2.app.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ntv2.app.feature.auth.domain.AuthRepository
import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode
import com.ntv2.app.feature.auth.presentation.state.LoginAction
import com.ntv2.app.feature.auth.presentation.state.LoginUiState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                _uiState.update {
                    it.copy(
                        authStep = authState.step,
                        qrCodePayload = authState.qrCodePayload,
                        isLoading = authState.isLoading,
                        errorMessage = authState.errorMessage,
                        isAuthorized = authState.step is AuthStep.Authorized
                    )
                }
            }
        }

        onAction(LoginAction.Initialize)
    }

    fun onAction(action: LoginAction) {
        when (action) {
            LoginAction.Initialize -> initialize()
            is LoginAction.SwitchMode -> switchMode(action.mode)
            LoginAction.RequestQr -> requestQr()
            is LoginAction.UpdatePhone -> _uiState.update { it.copy(phoneNumber = action.value) }
            is LoginAction.UpdateCode -> _uiState.update { it.copy(code = action.value) }
            is LoginAction.UpdatePassword -> _uiState.update { it.copy(password = action.value) }
            LoginAction.SubmitPhone -> submitPhone()
            LoginAction.SubmitCode -> submitCode()
            LoginAction.SubmitPassword -> submitPassword()
            LoginAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun initialize() {
        viewModelScope.launch {
            val session = authRepository.restoreSession()
            if (session.isLoggedIn) {
                _uiState.update {
                    it.copy(
                        isAuthorized = true,
                        authStep = AuthStep.Authorized,
                        errorMessage = null
                    )
                }
                return@launch
            }

            authRepository.initialize()
            if (_uiState.value.loginMode == LoginMode.QrCode) {
                authRepository.requestQrLogin()
            }
        }
    }

    private fun switchMode(mode: LoginMode) {
        _uiState.update {
            it.copy(
                loginMode = mode,
                errorMessage = null
            )
        }

        if (mode == LoginMode.QrCode) {
            requestQr()
        }
    }

    private fun requestQr() {
        viewModelScope.launch {
            authRepository.requestQrLogin()
        }
    }

    private fun submitPhone() {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe o telefone") }
            return
        }

        viewModelScope.launch {
            authRepository.submitPhoneNumber(phone)
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

class LoginViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
