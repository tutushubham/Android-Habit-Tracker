package com.tutushubham.pokidex.feature_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val behaviorRepository: BehaviorRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            useCase = SettingsUseCase(settingsRepository, behaviorRepository)
        ) as T
    }
}
