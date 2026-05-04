package com.ntv2.app.feature.auth.data.datasource

import com.ntv2.app.feature.auth.domain.model.AuthSession
import com.ntv2.app.feature.auth.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

interface TelegramAuthDataSource {
    val authState: Flow<AuthState>

    suspend fun initialize()
    suspend fun requestQrLogin()
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun submitPassword(password: String)
    suspend fun logout()
    suspend fun close()
    suspend fun currentSession(): AuthSession
}
