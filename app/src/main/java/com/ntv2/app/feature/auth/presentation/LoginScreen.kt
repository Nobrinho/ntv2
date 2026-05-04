package com.ntv2.app.feature.auth.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ntv2.app.feature.auth.domain.model.AuthStep
import com.ntv2.app.feature.auth.domain.model.LoginMode
import com.ntv2.app.feature.auth.presentation.state.LoginAction
import com.ntv2.app.feature.auth.presentation.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAuthorized) {
        if (state.isAuthorized) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Login Telegram",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.QrCode)) }) {
                Text("QR Code")
            }
            Button(onClick = { viewModel.onAction(LoginAction.SwitchMode(LoginMode.Phone)) }) {
                Text("Telefone")
            }
        }

        if (state.loginMode == LoginMode.QrCode) {
            Text("Modo QR", color = Color.White)
            val qrBitmap = remember(state.qrCodePayload) {
                state.qrCodePayload?.let { generateQrBitmap(it, 420) }
            }
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code de login",
                    modifier = Modifier.size(240.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .blur(10.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                }
            }
            Text(state.qrCodePayload ?: "Gerando QR...", color = Color.White)
            Button(onClick = { viewModel.onAction(LoginAction.RequestQr) }) {
                Text("Atualizar QR")
            }
        } else {
            Text("Modo Telefone", color = Color.White)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.phoneNumber,
                onValueChange = { viewModel.onAction(LoginAction.UpdatePhone(it)) },
                label = { Text("Telefone", color = Color.White) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true
            )
            Button(onClick = { viewModel.onAction(LoginAction.SubmitPhone) }) {
                Text("Enviar Telefone")
            }

            if (state.authStep is AuthStep.WaitingCode || state.code.isNotBlank()) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.code,
                    onValueChange = { viewModel.onAction(LoginAction.UpdateCode(it)) },
                    label = { Text("Código", color = Color.White) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    singleLine = true
                )
                Button(onClick = { viewModel.onAction(LoginAction.SubmitCode) }) {
                    Text("Validar Código")
                }
            }

            if (state.authStep is AuthStep.WaitingPassword || state.password.isNotBlank()) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.password,
                    onValueChange = { viewModel.onAction(LoginAction.UpdatePassword(it)) },
                    label = { Text("Senha 2FA", color = Color.White) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(onClick = { viewModel.onAction(LoginAction.SubmitPassword) }) {
                    Text("Validar Senha")
                }
            }
        }

        if (state.isLoading) {
            Text("Carregando...", color = Color.White)
        }

        if (state.errorMessage != null) {
            Text("Erro: ${state.errorMessage}", color = Color.White)
            Button(onClick = { viewModel.onAction(LoginAction.ClearError) }) {
                Text("Limpar Erro")
            }
        }

        Text("Estado: ${state.authStep}", color = Color.White)
    }
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
