package com.ntv2.app.core.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Incrementado quando um overlay global (ex.: "Fechar o aplicativo?") fecha sem sair da tela.
 * Cada tela observa e devolve o foco ao item que ela mesma lembra (o overlay não sabe qual era).
 */
val LocalFocusRestoreSignal = compositionLocalOf { 0 }
