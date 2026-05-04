package com.ntv2.app.feature.auth.domain

import com.ntv2.app.feature.auth.domain.model.AuthSession
import com.ntv2.app.feature.auth.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun initialize()
    suspend fun requestQrLogin()
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun restoreSession(): AuthSession
    suspend fun logout()
}
