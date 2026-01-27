package com.tutushubham.pokidex.core.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_preferences")

private object TimerPreferencesKeys {
    val START_TIME_MILLIS = longPreferencesKey("start_time_millis")
    val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
}

class TimerPreferences(private val context: Context) {

    val startTimeMillis: Flow<Long?> = context.timerDataStore.data.map { preferences ->
        preferences[TimerPreferencesKeys.START_TIME_MILLIS]
    }

    val activeSessionId: Flow<String?> = context.timerDataStore.data.map { preferences ->
        preferences[TimerPreferencesKeys.ACTIVE_SESSION_ID]
    }

    suspend fun saveTimerState(sessionId: String, startTimeMillis: Long) {
        context.timerDataStore.edit { preferences ->
            preferences[TimerPreferencesKeys.ACTIVE_SESSION_ID] = sessionId
            preferences[TimerPreferencesKeys.START_TIME_MILLIS] = startTimeMillis
        }
    }

    suspend fun clearTimerState() {
        context.timerDataStore.edit { preferences ->
            preferences.remove(TimerPreferencesKeys.ACTIVE_SESSION_ID)
            preferences.remove(TimerPreferencesKeys.START_TIME_MILLIS)
        }
    }

    suspend fun getStartTimeMillis(): Long? {
        return context.timerDataStore.data.map { it[TimerPreferencesKeys.START_TIME_MILLIS] }.first()
    }

    suspend fun getActiveSessionId(): String? {
        return context.timerDataStore.data.map { it[TimerPreferencesKeys.ACTIVE_SESSION_ID] }.first()
    }
}
