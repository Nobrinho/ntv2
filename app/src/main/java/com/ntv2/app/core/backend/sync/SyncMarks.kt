package com.ntv2.app.core.backend.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Onde a sincronização parou, por tabela: até que `updatedAt` local já subiu (push) e o último
 * `updated_at` remoto já baixado (pull, guardado como veio do servidor para não perder os
 * microssegundos e baixar a mesma linha de novo).
 */
interface SyncMarks {
    /** Dono das marcas: se o user_id mudar (sessão anônima nova), tudo recomeça do zero. */
    suspend fun userId(): String?
    suspend fun reset(userId: String)
    suspend fun pushedUpTo(table: String): Long
    suspend fun setPushedUpTo(table: String, epochMs: Long)
    suspend fun pulledUpTo(table: String): String?
    suspend fun setPulledUpTo(table: String, iso: String)
}

private val Context.syncDataStore by preferencesDataStore(name = "personalization_sync")

class DataStoreSyncMarks(private val context: Context) : SyncMarks {
    private val userKey = stringPreferencesKey("user_id")
    private fun pushKey(table: String) = longPreferencesKey("push_$table")
    private fun pullKey(table: String) = stringPreferencesKey("pull_$table")

    override suspend fun userId(): String? = context.syncDataStore.data.first()[userKey]

    override suspend fun reset(userId: String) {
        context.syncDataStore.edit {
            it.clear()
            it[userKey] = userId
        }
    }

    override suspend fun pushedUpTo(table: String): Long = context.syncDataStore.data.first()[pushKey(table)] ?: 0L

    override suspend fun setPushedUpTo(table: String, epochMs: Long) {
        context.syncDataStore.edit { it[pushKey(table)] = epochMs }
    }

    override suspend fun pulledUpTo(table: String): String? = context.syncDataStore.data.first()[pullKey(table)]

    override suspend fun setPulledUpTo(table: String, iso: String) {
        context.syncDataStore.edit { it[pullKey(table)] = iso }
    }
}
