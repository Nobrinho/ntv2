package com.ntv2.app.feature.auth.presentation.state

import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode

data class LoginUiState(
    val loginMode: LoginMode = LoginMode.QrCode,
    val authStep: AuthStep = AuthStep.Idle,
    val qrCodePayload: String? = null,
    // Só a parte nacional (DDD + número). O DDI +55 é aplicado nos bastidores no envio.
    val phoneNumber: String = "",
    val code: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthorized: Boolean = false,
    /** Usuário pediu para voltar e corrigir o número (mesmo com o TDLib já em WaitCode). */
    val editingPhone: Boolean = false,
    /** Segundos restantes até poder reenviar o código (0 = liberado). Evita flood. */
    val resendCooldownSeconds: Int = 0
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
    /** Volta ao passo de telefone para corrigir o número (a partir do passo de código). */
    data object EditPhone : LoginAction
    /** Reenvia o código para o mesmo número (respeita o cooldown). */
    data object ResendCode : LoginAction
    data object ClearError : LoginAction
}
