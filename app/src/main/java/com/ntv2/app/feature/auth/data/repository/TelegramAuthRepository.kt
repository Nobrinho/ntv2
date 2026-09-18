package com.ntv2.app.feature.auth.data.repository

import com.ntv2.app.feature.auth.data.datasource.TelegramAuthDataSource
import com.ntv2.app.feature.auth.domain.AuthRepository
import com.ntv2.app.feature.auth.domain.model.AuthSession
import com.ntv2.app.feature.auth.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

class TelegramAuthRepository(
    private val dataSource: TelegramAuthDataSource
) : AuthRepository {

    override val authState: Flow<AuthState> = dataSource.authState

    override suspend fun initialize() {
        dataSource.initialize()
    }

    override suspend fun requestQrLogin() {
        dataSource.requestQrLogin()
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        dataSource.submitPhoneNumber(phoneNumber)
    }

    override suspend fun submitCode(code: String) {
        dataSource.submitCode(code)
    }

    override suspend fun submitPassword(password: String) {
        dataSource.submitPassword(password)
    }

    override suspend fun restoreSession(): AuthSession {
        return dataSource.currentSession()
    }

    override suspend fun logout() {
        dataSource.logout()
    }

    override suspend fun verifySessionActive() {
        dataSource.verifySessionActive()
    }
}
