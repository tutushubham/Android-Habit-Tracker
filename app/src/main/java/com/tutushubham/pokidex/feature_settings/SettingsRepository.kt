package com.tutushubham.pokidex.feature_settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    private val Context.dataStore by preferencesDataStore("system_settings")

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ADAPTIVE_PLANNING = booleanPreferencesKey("adaptive_planning")
        val PLANNING_STYLE = stringPreferencesKey("planning_style")
        val FATIGUE_SENSITIVITY = stringPreferencesKey("fatigue_sensitivity")
        val LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val USE_AI_PEAK_TIME = booleanPreferencesKey("use_ai_peak_time")
        val PEAK_FOCUS_START = intPreferencesKey("peak_focus_start")
        val PEAK_FOCUS_END = intPreferencesKey("peak_focus_end")
        val NOTIFY_DAILY_SUMMARY = booleanPreferencesKey("notify_daily_summary")
        val NOTIFY_PEAK_REMINDER = booleanPreferencesKey("notify_peak_reminder")
        val NOTIFY_BEHIND_ALERT = booleanPreferencesKey("notify_behind_alert")
    }

    val settings: Flow<SystemSettings> = context.dataStore.data.map { prefs ->
        SystemSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            adaptivePlanningEnabled = prefs[Keys.ADAPTIVE_PLANNING] ?: true,
            planningStyle = prefs[Keys.PLANNING_STYLE]?.let { runCatching { PlanningStyle.valueOf(it) }.getOrNull() }
                ?: PlanningStyle.BALANCED,
            fatigueSensitivity = prefs[Keys.FATIGUE_SENSITIVITY]?.let { runCatching { FatigueSensitivity.valueOf(it) }.getOrNull() }
                ?: FatigueSensitivity.MEDIUM,
            learningEnabled = prefs[Keys.LEARNING_ENABLED] ?: true,
            useAiPeakTime = prefs[Keys.USE_AI_PEAK_TIME] ?: true,
            peakFocusStartHour = prefs[Keys.PEAK_FOCUS_START] ?: 9,
            peakFocusEndHour = prefs[Keys.PEAK_FOCUS_END] ?: 11,
            notifyDailySummary = prefs[Keys.NOTIFY_DAILY_SUMMARY] ?: true,
            notifyPeakReminder = prefs[Keys.NOTIFY_PEAK_REMINDER] ?: true,
            notifyBehindAlert = prefs[Keys.NOTIFY_BEHIND_ALERT] ?: true
        )
    }

    suspend fun update(settings: SystemSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.ADAPTIVE_PLANNING] = settings.adaptivePlanningEnabled
            prefs[Keys.PLANNING_STYLE] = settings.planningStyle.name
            prefs[Keys.FATIGUE_SENSITIVITY] = settings.fatigueSensitivity.name
            prefs[Keys.LEARNING_ENABLED] = settings.learningEnabled
            prefs[Keys.USE_AI_PEAK_TIME] = settings.useAiPeakTime
            prefs[Keys.PEAK_FOCUS_START] = settings.peakFocusStartHour
            prefs[Keys.PEAK_FOCUS_END] = settings.peakFocusEndHour
            prefs[Keys.NOTIFY_DAILY_SUMMARY] = settings.notifyDailySummary
            prefs[Keys.NOTIFY_PEAK_REMINDER] = settings.notifyPeakReminder
            prefs[Keys.NOTIFY_BEHIND_ALERT] = settings.notifyBehindAlert
        }
    }
}
