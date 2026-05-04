package com.ntv2.app.feature.settings.domain

import com.ntv2.app.core.preferences.UserPreferencesDataStore
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val minDurationMinutes: Flow<Int>
    suspend fun updateMinDurationMinutes(value: Int)
}

class FakeSettingsRepository(
    private val dataStore: UserPreferencesDataStore
) : SettingsRepository {
    override val minDurationMinutes: Flow<Int> = dataStore.minDurationMinutes

    override suspend fun updateMinDurationMinutes(value: Int) {
        dataStore.setMinDurationMinutes(value)
    }
}
