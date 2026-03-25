package com.tutushubham.pokidex.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.tutushubham.pokidex.core.domain.repository.AppStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppStateRepositoryImpl(
    private val context: Context
) : AppStateRepository {

    private val Context.dataStore by preferencesDataStore("app_state")

    private val ONBOARDING_KEY =
        booleanPreferencesKey("onboarding_completed")

    override suspend fun isOnboardingCompleted(): Boolean {
        return context.dataStore.data
            .map { it[ONBOARDING_KEY] ?: false }
            .first()
    }

    override suspend fun setOnboardingCompleted() {
        context.dataStore.edit {
            it[ONBOARDING_KEY] = true
        }
    }
}
