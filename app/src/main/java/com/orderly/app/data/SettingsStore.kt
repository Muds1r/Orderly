package com.orderly.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "orderly_settings")

class SettingsStore(private val context: Context) {

    private val accountKey = stringPreferencesKey("account_name")
    private val appPasswordKey = stringPreferencesKey("app_password")
    private val lastSyncKey = longPreferencesKey("last_sync")
    private val syncMarkerKey = intPreferencesKey("sync_logic_version")

    val accountName: Flow<String?> = context.dataStore.data.map { it[accountKey] }
    val appPassword: Flow<String?> = context.dataStore.data.map { it[appPasswordKey] }
    val lastSync: Flow<Long?> = context.dataStore.data.map { it[lastSyncKey] }
    val syncMarker: Flow<Int?> = context.dataStore.data.map { it[syncMarkerKey] }

    suspend fun setCredentials(email: String, appPassword: String) {
        context.dataStore.edit {
            it[accountKey] = email
            it[appPasswordKey] = appPassword
        }
    }

    suspend fun setLastSync(time: Long) {
        context.dataStore.edit { it[lastSyncKey] = time }
    }

    suspend fun setSyncMarker(version: Int) {
        context.dataStore.edit { it[syncMarkerKey] = version }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
