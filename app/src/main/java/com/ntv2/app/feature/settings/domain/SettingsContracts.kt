package com.ntv2.app.feature.settings.domain

import com.ntv2.app.core.preferences.UserPreferencesDataStore
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val minDurationMinutes: Flow<Int>
    suspend fun updateMinDurationMinutes(value: Int)

    val maxCards: Flow<Int>
    suspend fun updateMaxCards(value: Int)

    /** Canal ativo exibido na biblioteca (0 = escolher o primeiro habilitado). */
    val activeChannelId: Flow<Long>
    suspend fun updateActiveChannelId(value: Long)

    val showCovers: Flow<Boolean>
    suspend fun updateShowCovers(value: Boolean)

    val animationsEnabled: Flow<Boolean>
    suspend fun updateAnimationsEnabled(value: Boolean)

    val castPhotos: Flow<Boolean>
    suspend fun updateCastPhotos(value: Boolean)
}

class FakeSettingsRepository(
    private val dataStore: UserPreferencesDataStore
) : SettingsRepository {
    override val minDurationMinutes: Flow<Int> = dataStore.minDurationMinutes

    override suspend fun updateMinDurationMinutes(value: Int) {
        dataStore.setMinDurationMinutes(value)
    }

    override val maxCards: Flow<Int> = dataStore.maxCards

    override suspend fun updateMaxCards(value: Int) {
        dataStore.setMaxCards(value)
    }

    override val activeChannelId: Flow<Long> = dataStore.activeChannelId

    override suspend fun updateActiveChannelId(value: Long) {
        dataStore.setActiveChannelId(value)
    }

    override val showCovers: Flow<Boolean> = dataStore.showCovers

    override suspend fun updateShowCovers(value: Boolean) {
        dataStore.setShowCovers(value)
    }

    override val animationsEnabled: Flow<Boolean> = dataStore.animationsEnabled

    override suspend fun updateAnimationsEnabled(value: Boolean) {
        dataStore.setAnimationsEnabled(value)
    }

    override val castPhotos: Flow<Boolean> = dataStore.castPhotos

    override suspend fun updateCastPhotos(value: Boolean) {
        dataStore.setCastPhotos(value)
    }
}
