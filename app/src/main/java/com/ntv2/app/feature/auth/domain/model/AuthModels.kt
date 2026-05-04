package com.ntv2.app.feature.auth.domain.model

data class AuthSession(
    val isLoggedIn: Boolean,
    val userId: Long?,
    val displayName: String?
)

enum class LoginMode {
    QrCode,
    Phone
}

sealed interface AuthStep {
    data object Idle : AuthStep
    data object Initializing : AuthStep
    data object WaitingQrScan : AuthStep
    data object WaitingPhoneNumber : AuthStep
    data object WaitingCode : AuthStep
    data object WaitingPassword : AuthStep
    data object Authorized : AuthStep
    data class Failed(val message: String) : AuthStep
}

data class AuthState(
    val step: AuthStep = AuthStep.Idle,
    val qrCodePayload: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: AuthSession = AuthSession(isLoggedIn = false, userId = null, displayName = null)
)
