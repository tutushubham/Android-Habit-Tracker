package com.tutushubham.pokidex.feature_settings

import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import kotlinx.coroutines.flow.Flow

class SettingsUseCase(
    private val settingsRepository: SettingsRepository,
    private val behaviorRepository: BehaviorRepository
) {
    fun getSettings(): Flow<SystemSettings> = settingsRepository.settings

    suspend fun updateSettings(settings: SystemSettings) {
        settingsRepository.update(settings)
    }

    suspend fun resetLearnedBehavior() {
        behaviorRepository.clearAll()
    }
}
