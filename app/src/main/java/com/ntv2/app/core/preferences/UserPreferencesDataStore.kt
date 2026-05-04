package com.ntv2.app.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ntv2_prefs")

class UserPreferencesDataStore(
    private val context: Context
) {
    private val minDurationKey = intPreferencesKey("min_duration_minutes")

    val minDurationMinutes: Flow<Int> = context.dataStore.data.map { prefs: Preferences ->
        prefs[minDurationKey] ?: 15
    }

    suspend fun setMinDurationMinutes(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[minDurationKey] = value
        }
    }
}
