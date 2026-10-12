package com.lovelydays.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session")
private val Context.scheduleDataStore by preferencesDataStore(name = "schedule")

class SessionStore(context: Context) {
    private val dataStore = context.applicationContext.sessionDataStore

    val userId: Flow<Long?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY]?.takeIf { it > 0L } }

    suspend fun currentUserId(): Long? = userId.first()

    suspend fun setUserId(id: Long) {
        dataStore.edit { it[KEY] = id }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = longPreferencesKey("user_id")
    }
}

class ScheduleStore(context: Context) {
    private val dataStore = context.applicationContext.scheduleDataStore

    suspend fun names(): Set<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY] ?: emptySet() }
        .first()

    suspend fun save(names: Set<String>) {
        dataStore.edit { prefs ->
            if (names.isEmpty()) prefs.remove(KEY) else prefs[KEY] = names
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("work_names")
    }
}
