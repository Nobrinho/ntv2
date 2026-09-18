package com.ntv2.app.feature.auth.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ntv2.app.R
import com.ntv2.app.core.ui.rememberAdaptiveLayoutInfo
import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode
import com.ntv2.app.feature.auth.presentation.state.LoginAction
import com.ntv2.app.feature.auth.presentation.viewmodel.LoginViewModel

private val BRAND = Color(0xFF2BEE34)
private const val CARD_WIDTH_DP = 560

private enum class LoginStep { Qr, Phone, Code, Password, Success }

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    // Sessão encerrada fora do app (revogada/deslogada em outro dispositivo): exibe um aviso.
    sessionEndedNotice: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()

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
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        val compact = adaptive.usePhoneLayout || maxWidth < 600.dp
        val logoGlow = if (compact) 124.dp else 180.dp
        val logoSize = if (compact) 86.dp else 124.dp
        // Degradê verde suave no topo (na cor da marca).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x242BEE34), Color(0x0A2BEE34), Color(0x00000000))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 20.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
        ) {
            // Logo maior com glow verde por trás.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(logoGlow)
                        .background(
                            Brush.radialGradient(listOf(Color(0x552BEE34), Color(0x00000000)))
                        )
                )
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = "Nbr PLAY",
                    colorFilter = ColorFilter.tint(BRAND),
                    modifier = Modifier.size(logoSize)
                )
            }

            if (sessionEndedNotice && step != LoginStep.Success) {
                SessionEndedBanner(compact = compact)
            }

            Card(compact = compact) {
                when (step) {
                LoginStep.Success -> SuccessStep()
                LoginStep.Password -> PasswordStep(
                    value = state.password,
                    loading = state.isLoading,
                    error = state.errorMessage,
                    onChange = { viewModel.onAction(LoginAction.UpdatePassword(it)) },
                    onSubmit = { viewModel.onAction(LoginAction.SubmitPassword) }
                )
                LoginStep.Code -> CodeStep(
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
                LoginStep.Phone -> PhoneStep(
                    value = state.phoneNumber,
                    loading = state.isLoading,
                    error = state.errorMessage,
                    // Nunca foca automaticamente: o teclado/cursor só abre ao tocar no campo.
                    autoFocus = false,
                    onChange = { viewModel.onAction(LoginAction.UpdatePhone(it)) },
                    onSubmit = { viewModel.onAction(LoginAction.SubmitPhone) },
                    onUseQr = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.QrCode)) }
                )
                LoginStep.Qr -> QrStep(
                    compact = compact,
                    payload = state.qrCodePayload,
                    error = state.errorMessage,
                    onUsePhone = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.Phone)) }
                )
                }
            }
        }
    }
}

@Composable
private fun SessionEndedBanner(compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = CARD_WIDTH_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33F2B01E))
            .border(1.dp, Color(0x66F2B01E), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = Color(0xFFF2B01E),
            modifier = Modifier.size(if (compact) 20.dp else 22.dp)
        )
        Text(
            text = "Sua sessão do Telegram foi encerrada em outro dispositivo. Entre novamente para continuar.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF3E3C0)
        )
    }
}

@Composable
private fun Card(compact: Boolean, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = CARD_WIDTH_DP.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = if (compact) 18.dp else 28.dp, vertical = if (compact) 18.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
private fun QrStep(compact: Boolean, payload: String?, error: String?, onUsePhone: () -> Unit) {
    val phoneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { phoneFocus.requestFocus() } }

    Text("Entre na sua conta", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(
        "Método 1 — QR Code",
        style = MaterialTheme.typography.titleSmall,
        color = BRAND,
        textAlign = TextAlign.Center
    )

    val qr = remember(payload) { payload?.let { generateQrBitmap(it, 400) } }
    val qrBoxSize = if (compact) 184.dp else 200.dp
    val qrImageSize = if (compact) 164.dp else 180.dp
    val instructions: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Escaneie com o Telegram no seu celular:",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            StepLine("1", "Abra o Telegram")
            StepLine("2", "Ajustes → Dispositivos")
            StepLine("3", "Vincular dispositivo (Scan QR)")
            StepLine("4", "Aponte a câmera para este código")
            Text(
                if (qr != null) "● Aguardando conexão…" else "Gerando QR…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (qr != null) BRAND else Color(0xFF8A8A8A)
            )
        }
    }
    val qrContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(qrBoxSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (qr != null) {
                Image(qr.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(qrImageSize))
            } else {
                CircularProgressIndicator(color = Color.Black)
            }
        }
    }

    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            qrContent()
            instructions(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            qrContent()
            instructions(Modifier.weight(1f))
        }
    }

    error?.let { ErrorText(it) }

    Divider("OU")

    Text(
        "Método 2 — Entrar com o número de telefone",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF8A8A8A),
        textAlign = TextAlign.Center
    )
    Button(
        modifier = Modifier.fillMaxWidth().focusRequester(phoneFocus),
        onClick = onUsePhone
    ) {
        Text("Entrar com telefone")
    }
}

@Composable
private fun StepLine(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0x332BEE34)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = BRAND, style = MaterialTheme.typography.labelMedium)
        }
        Text(text, color = Color(0xFFD0D0D0), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PhoneStep(
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

    Text("Entre na sua conta", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(
        "Método 1 — Número de telefone",
        style = MaterialTheme.typography.titleSmall,
        color = BRAND,
        textAlign = TextAlign.Center
    )
    Text("Número do Brasil (+55) — informe DDD e celular", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB0B0B0))
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        value = value,
        onValueChange = onChange,
        singleLine = true,
        prefix = { Text("+55", color = Color(0xFFB0B0B0)) },
        placeholder = { Text("(11) 99999-9999", color = Color(0xFF6A6A6A)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
    )
    error?.let { ErrorText(it) }
    Button(modifier = Modifier.fillMaxWidth(), onClick = onSubmit) {
        Text(if (loading) "Enviando…" else "Continuar")
    }

    Divider("OU")

    Text(
        "Método 2 — Entrar com QR Code",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF8A8A8A),
        textAlign = TextAlign.Center
    )
    Button(modifier = Modifier.fillMaxWidth(), onClick = onUseQr) { Text("Entrar com QR Code") }
}

@Composable
private fun CodeStep(
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

    Text("Confirme o código", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(
        "Enviamos um código para ${maskPhone(phone)}",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFB0B0B0),
        textAlign = TextAlign.Center
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("_ _ _ _ _", color = Color(0xFF6A6A6A)) },
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done)
    )
    error?.let { ErrorText(it) }
    Button(modifier = Modifier.fillMaxWidth(), onClick = onSubmit) {
        Text(if (loading) "Validando…" else "Confirmar")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val canResend = resendCooldownSeconds <= 0
        Button(onClick = onResend, enabled = canResend) {
            Text(if (canResend) "Reenviar código" else "Reenviar em ${resendCooldownSeconds}s")
        }
        Button(onClick = onBack) { Text("Voltar") }
    }
}

@Composable
private fun PasswordStep(
    value: String,
    loading: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Text("Verificação em duas etapas", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(
        "Digite a senha da sua conta Telegram",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFB0B0B0),
        textAlign = TextAlign.Center
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
    )
    error?.let { ErrorText(it) }
    Button(modifier = Modifier.fillMaxWidth(), onClick = onSubmit) {
        Text(if (loading) "Entrando…" else "Entrar")
    }
}

@Composable
private fun SuccessStep() {
    Box(
        modifier = Modifier.size(72.dp).clip(CircleShape).background(BRAND),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(44.dp))
    }
    Text("Login realizado", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Preparando seu conteúdo…", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB0B0B0))
}

@Composable
private fun Divider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0x33FFFFFF)))
        Text(label, color = Color(0xFF8A8A8A), style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0x33FFFFFF)))
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
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
