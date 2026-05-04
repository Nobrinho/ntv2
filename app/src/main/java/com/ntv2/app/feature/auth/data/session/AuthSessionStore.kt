package com.ntv2.app.feature.auth.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ntv2.app.feature.auth.domain.model.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

class AuthSessionStore(
    private val context: Context
) {
    private val loggedInKey = booleanPreferencesKey("logged_in")
    private val userIdKey = longPreferencesKey("user_id")
    private val displayNameKey = stringPreferencesKey("display_name")

    val session: Flow<AuthSession> = context.authDataStore.data.map { prefs: Preferences ->
        AuthSession(
            isLoggedIn = prefs[loggedInKey] ?: false,
            userId = prefs[userIdKey],
            displayName = prefs[displayNameKey]
        )
    }

    suspend fun save(session: AuthSession) {
        context.authDataStore.edit { prefs ->
            prefs[loggedInKey] = session.isLoggedIn
            if (session.userId != null) {
                prefs[userIdKey] = session.userId
            } else {
                prefs.remove(userIdKey)
            }
            if (session.displayName != null) {
                prefs[displayNameKey] = session.displayName
            } else {
                prefs.remove(displayNameKey)
            }
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
