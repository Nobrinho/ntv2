package com.ntv2.app.feature.auth.presentation.state

import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode

data class LoginUiState(
    val loginMode: LoginMode = LoginMode.QrCode,
    val authStep: AuthStep = AuthStep.Idle,
    val qrCodePayload: String? = null,
    val phoneNumber: String = "+55 ",
    val code: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthorized: Boolean = false
)

sealed interface LoginAction {
    data object Initialize : LoginAction
    data class SwitchMode(val mode: LoginMode) : LoginAction
    data object RequestQr : LoginAction
    data class UpdatePhone(val value: String) : LoginAction
    data class UpdateCode(val value: String) : LoginAction
    data class UpdatePassword(val value: String) : LoginAction
    data object SubmitPhone : LoginAction
    data object SubmitCode : LoginAction
    data object SubmitPassword : LoginAction
    data object ClearError : LoginAction
}
