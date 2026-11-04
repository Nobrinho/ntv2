package com.ntv2.app.feature.auth.presentation

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ntv2.app.R
import com.ntv2.app.core.ui.BrandButton
import com.ntv2.app.core.ui.BrandButtonLabel
import com.ntv2.app.core.ui.BrandButtonStyle
import com.ntv2.app.core.ui.BrandColors
import com.ntv2.app.core.ui.NbrType
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode
import com.ntv2.app.feature.auth.presentation.state.LoginAction
import com.ntv2.app.feature.auth.presentation.viewmodel.LoginViewModel

// Largura máxima da coluna do login fora do celular (tablet / TV nos passos de telefone, código e senha).
private const val PAGE_MAX_WIDTH_DP = 480

private enum class LoginStep { Qr, Phone, Code, Password, Success }

/**
 * Telas de entrada do protótipo: 05 Login QR, 06 Login telefone, 07 Login código,
 * 08 Verificação em duas etapas (celular) e TV 05 Login QR. Ver docs/PROTOTIPO-ESPEC.md.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    // Sessão encerrada fora do app (revogada/deslogada em outro dispositivo): exibe um aviso.
    sessionEndedNotice: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isAuthorized) {
        if (state.isAuthorized) onLoginSuccess()
    }

    val step = when {
        state.isAuthorized -> LoginStep.Success
        state.authStep is AuthStep.WaitingPassword -> LoginStep.Password
        // "Voltar" no passo de código: mostra o telefone para corrigir, mesmo com TDLib em WaitCode.
        state.editingPhone -> LoginStep.Phone
        // Modo QR tem prioridade: ao alternar para QR (mesmo vindo do passo de código), mostra o QR
        // (spinner enquanto ele é gerado) em vez de continuar na confirmação de código.
        state.loginMode == LoginMode.QrCode -> LoginStep.Qr
        state.authStep is AuthStep.WaitingCode -> LoginStep.Code
        else -> LoginStep.Phone
    }

    val adaptive = rememberAdaptiveLayoutInfo()
    // Voltar em camadas: código → telefone; telefone → QR (TV, onde o QR é o início).
    BackHandler(enabled = step == LoginStep.Code) { viewModel.onAction(LoginAction.EditPhone) }
    BackHandler(enabled = step == LoginStep.Phone && adaptive.isTv) {
        viewModel.onAction(LoginAction.SwitchMode(LoginMode.QrCode))
    }
    val banner = sessionEndedNotice && step != LoginStep.Success

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BrandColors.Background)) {
        val compact = adaptive.usePhoneLayout || maxWidth < 600.dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Teclado aberto (código / senha): o conteúdo sobe e rola, para o campo e o botão
                // ficarem acima do teclado.
                .imePadding()
        ) {
            val viewport = maxHeight
            when (step) {
                LoginStep.Qr -> if (compact) {
                    PhoneQrStep(
                        viewport = viewport,
                        banner = banner,
                        payload = state.qrCodePayload,
                        error = state.errorMessage,
                        onUsePhone = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.Phone)) }
                    )
                } else {
                    TvQrStep(
                        banner = banner,
                        payload = state.qrCodePayload,
                        error = state.errorMessage,
                        onUsePhone = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.Phone)) }
                    )
                }
                LoginStep.Phone -> PhoneStep(
                    viewport = viewport,
                    banner = banner,
                    value = state.phoneNumber,
                    loading = state.isLoading,
                    error = state.errorMessage,
                    // Nunca foca automaticamente: o teclado/cursor só abre ao tocar no campo.
                    autoFocus = false,
                    onChange = { viewModel.onAction(LoginAction.UpdatePhone(it)) },
                    onSubmit = { viewModel.onAction(LoginAction.SubmitPhone) },
                    onUseQr = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.QrCode)) }
                )
                LoginStep.Code -> CodeStep(
                    viewport = viewport,
                    banner = banner,
                    phone = state.phoneNumber,
                    value = state.code,
                    loading = state.isLoading,
                    error = state.errorMessage,
                    resendCooldownSeconds = state.resendCooldownSeconds,
                    onChange = { viewModel.onAction(LoginAction.UpdateCode(it)) },
                    onSubmit = { viewModel.onAction(LoginAction.SubmitCode) },
                    onResend = { viewModel.onAction(LoginAction.ResendCode) },
                    onBack = { viewModel.onAction(LoginAction.EditPhone) }
                )
                LoginStep.Password -> PasswordStep(
                    viewport = viewport,
                    banner = banner,
                    value = state.password,
                    loading = state.isLoading,
                    error = state.errorMessage,
                    onChange = { viewModel.onAction(LoginAction.UpdatePassword(it)) },
                    onSubmit = { viewModel.onAction(LoginAction.SubmitPassword) }
                )
                LoginStep.Success -> LoginPage(viewport = viewport, banner = false, onBack = null) {
                    LoginSuccess(modifier = Modifier.padding(top = 48.dp))
                }
            }
        }
    }
}

/**
 * Página de entrada do celular: padding 0/24/24/24, gap 20, topo de entrada; [actions] (botões)
 * ficam presos embaixo (coluna gap 12) e tudo rola quando não cabe (teclado aberto).
 */
@Composable
private fun LoginPage(
    viewport: androidx.compose.ui.unit.Dp,
    banner: Boolean,
    onBack: (() -> Unit)?,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PAGE_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .heightIn(min = viewport)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LoginTopBar(onBack)
                if (banner) LoginSessionEndedBanner()
                content()
            }
            if (actions != null) {
                Column(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = actions
                )
            }
        }
    }
}

private val QR_STEPS = listOf(
    "Abra o Telegram",
    "Ajustes → Dispositivos",
    "Vincular dispositivo (Scan QR)",
    "Aponte a câmera para este código"
)

/** Pede o foco do botão ao abrir o QR e ao cancelar o "Fechar o aplicativo?" (sinal global). */
@Composable
private fun rememberRestoredFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    val restoreSignal = com.ntv2.app.core.ui.LocalFocusRestoreSignal.current
    LaunchedEffect(restoreSignal) { runCatching { focus.requestFocus() } }
    return focus
}

/** 05 Login QR (celular). */
@Composable
private fun PhoneQrStep(
    viewport: androidx.compose.ui.unit.Dp,
    banner: Boolean,
    payload: String?,
    error: String?,
    onUsePhone: () -> Unit
) {
    val phoneFocus = rememberRestoredFocus()
    val qr = remember(payload) { payload?.let { generateQrBitmap(it, 400)?.asImageBitmap() } }
    LoginPage(viewport = viewport, banner = banner, onBack = null) {
        LoginTitleBlock("Entre na sua conta", "Escaneie com o Telegram no seu celular:")
        LoginMethodLabel("Método 1 — QR Code")
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LoginQrCard(qr, size = 220.dp, radius = 20.dp, qrSize = 170.dp)
            LoginQrStatus(ready = qr != null)
        }
        error?.let { LoginError(it, Modifier.align(Alignment.CenterHorizontally)) }
        LoginStepsBox(QR_STEPS)
        LoginOrDivider()
        Text(
            "Método 2 — Entrar com o número de telefone",
            style = NbrType.Secondary,
            color = BrandColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        LoginButton(
            modifier = Modifier.fillMaxWidth().focusRequester(phoneFocus),
            primary = false,
            onClick = onUsePhone
        ) { BrandButtonLabel("Entrar com telefone") }
    }
}

/** TV 05 Login QR: padding 48/60, gap 60; texto à esquerda, QR 240 à direita. */
@Composable
private fun TvQrStep(banner: Boolean, payload: String?, error: String?, onUsePhone: () -> Unit) {
    val phoneFocus = rememberRestoredFocus()
    val qr = remember(payload) { payload?.let { generateQrBitmap(it, 400)?.asImageBitmap() } }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_logo),
                contentDescription = "NBR Play",
                modifier = Modifier.size(width = 60.dp, height = 48.dp)
            )
            if (banner) LoginSessionEndedBanner()
            LoginTitleBlock("Entre na sua conta", subtitle = null, tv = true)
            LoginMethodLabel("Método 1 · QR Code (recomendado)")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QR_STEPS.forEachIndexed { i, s -> LoginStepLine("${i + 1}", s, tv = true) }
            }
            error?.let { LoginError(it) }
            Text(
                "Método 2 · Prefere receber um código? Entre com o seu número do Telegram.",
                style = NbrType.Caption.copy(fontSize = 11.sp, lineHeight = (11 * 1.21f).sp),
                color = BrandColors.TextMuted
            )
            LoginButton(
                modifier = Modifier.width(220.dp).focusRequester(phoneFocus),
                primary = false,
                onClick = onUsePhone
            ) { BrandButtonLabel("Entrar com telefone") }
        }
        Column(
            modifier = Modifier.width(280.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LoginQrCard(qr, size = 240.dp, radius = 16.dp, qrSize = 190.dp)
            LoginQrStatus(ready = qr != null, tv = true)
        }
    }
}

/** 06 Login telefone. */
@Composable
private fun PhoneStep(
    viewport: androidx.compose.ui.unit.Dp,
    banner: Boolean,
    value: String,
    loading: Boolean,
    error: String?,
    autoFocus: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onUseQr: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }

    LoginPage(
        viewport = viewport,
        banner = banner,
        onBack = null,
        actions = {
            LoginButton(modifier = Modifier.fillMaxWidth(), onClick = onSubmit) {
                BrandButtonLabel(if (loading) "Enviando…" else "Continuar")
            }
            LoginOrDivider()
            Text(
                "Método 2 — Entrar com QR Code",
                style = NbrType.Secondary,
                color = BrandColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            LoginButton(modifier = Modifier.fillMaxWidth(), primary = false, onClick = onUseQr) {
                BrandButtonLabel("Entrar com QR Code")
            }
        }
    ) {
        LoginTitleBlock("Entre com o seu número", "Número do Brasil (+55) — informe DDD e celular")
        LoginMethodLabel("Método 1 — Número de telefone")
        LoginTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focus).dpadVerticalExit(),
            value = value,
            onValueChange = onChange,
            isError = error != null,
            prefix = "+55",
            placeholder = "(11) 99999-9999",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
        )
        error?.let { LoginError(it) }
    }
}

/** 07 Login código. O voltar (código → telefone) fica no chevron do topo. */
@Composable
private fun CodeStep(
    viewport: androidx.compose.ui.unit.Dp,
    banner: Boolean,
    phone: String,
    value: String,
    loading: Boolean,
    error: String?,
    resendCooldownSeconds: Int,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LoginPage(
        viewport = viewport,
        banner = banner,
        onBack = onBack,
        actions = {
            LoginButton(modifier = Modifier.fillMaxWidth(), onClick = onSubmit) {
                BrandButtonLabel(if (loading) "Validando…" else "Confirmar")
            }
        }
    ) {
        LoginTitleBlock("Confirme o código", "Enviamos um código para ${maskPhone(phone)}")
        LoginCodeField(
            modifier = Modifier.fillMaxWidth().focusRequester(focus).dpadVerticalExit(),
            value = value,
            onValueChange = onChange,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
        )
        error?.let { LoginError(it, Modifier.align(Alignment.CenterHorizontally)) }
        val canResend = resendCooldownSeconds <= 0
        LoginTextLink(
            text = if (canResend) "Reenviar código" else "Reenviar em ${resendCooldownSeconds}s",
            enabled = canResend,
            onClick = onResend,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/** 08 Verificação em duas etapas. */
@Composable
private fun PasswordStep(
    viewport: androidx.compose.ui.unit.Dp,
    banner: Boolean,
    value: String,
    loading: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LoginPage(
        viewport = viewport,
        banner = banner,
        onBack = null,
        actions = {
            LoginButton(modifier = Modifier.fillMaxWidth(), onClick = onSubmit) {
                BrandButtonLabel(if (loading) "Entrando…" else "Entrar")
            }
        }
    ) {
        LoginTitleBlock("Verificação em duas etapas", "Digite a senha da sua conta Telegram")
        LoginTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focus).dpadVerticalExit(),
            value = value,
            onValueChange = onChange,
            isError = error != null,
            textStyle = LoginPasswordStyle,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
        )
        error?.let { LoginError(it) }
    }
}

private fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 4) return phone
    return "••• ••••-" + digits.takeLast(4)
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

// Botão do login: componente de marca (pílula; foco por escala + contorno + glow).
@Composable
private fun LoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    BrandButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = if (primary) BrandButtonStyle.Primary else BrandButtonStyle.Secondary,
        content = content
    )
}

// Campo de texto no D-pad: ← / → movem o cursor; ↑ / ↓ sempre saem do campo.
@Composable
private fun Modifier.dpadVerticalExit(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
}
