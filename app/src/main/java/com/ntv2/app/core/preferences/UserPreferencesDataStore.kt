package com.ntv2.app.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ntv2_prefs")

class UserPreferencesDataStore(
    private val context: Context
) {
    private val minDurationKey = intPreferencesKey("min_duration_minutes")
    private val activeChannelKey = longPreferencesKey("active_channel_id")
    private val showCoversKey = booleanPreferencesKey("show_covers")
    private val animationsKey = booleanPreferencesKey("animations_enabled")
    private val castPhotosKey = booleanPreferencesKey("cast_photos")
    private val maxCardsKey = intPreferencesKey("max_cards")

    val minDurationMinutes: Flow<Int> = context.dataStore.data.map { prefs: Preferences ->
        prefs[minDurationKey] ?: 15
    }

    suspend fun setMinDurationMinutes(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[minDurationKey] = value
        }
    }

    /** Máximo de cards mantidos na grade (teto de memória, grade não-lazy). */
    val maxCards: Flow<Int> = context.dataStore.data.map { prefs: Preferences ->
        prefs[maxCardsKey] ?: 150
    }

    suspend fun setMaxCards(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[maxCardsKey] = value
        }
    }

    /** Canal atualmente exibido na biblioteca (0 = nenhum/escolher o primeiro habilitado). */
    val activeChannelId: Flow<Long> = context.dataStore.data.map { prefs: Preferences ->
        prefs[activeChannelKey] ?: 0L
    }

    suspend fun setActiveChannelId(value: Long) {
        context.dataStore.edit { prefs ->
            prefs[activeChannelKey] = value
        }
    }

    /** Exibir capas (pôsteres/thumbs) na interface. */
    val showCovers: Flow<Boolean> = context.dataStore.data.map { it[showCoversKey] ?: true }

    suspend fun setShowCovers(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[showCoversKey] = value }
    }

    /** Animações e transições da interface (ex.: intro/splash). */
    val animationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[animationsKey] ?: true }

    suspend fun setAnimationsEnabled(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[animationsKey] = value }
    }

    /** Exibir fotos do elenco na tela de detalhes (senão, só os nomes). */
    val castPhotos: Flow<Boolean> = context.dataStore.data.map { it[castPhotosKey] ?: true }

    suspend fun setCastPhotos(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[castPhotosKey] = value }
    }
}
