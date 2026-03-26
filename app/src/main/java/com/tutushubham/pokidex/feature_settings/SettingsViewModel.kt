package com.tutushubham.pokidex.feature_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val useCase: SettingsUseCase
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val settings: SystemSettings = SystemSettings(),
        val showResetConfirmation: Boolean = false,
        val resetComplete: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            useCase.getSettings().collect { settings ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    settings = settings
                )
            }
        }
    }

    fun updateSettings(transform: (SystemSettings) -> SystemSettings) {
        val updated = transform(_state.value.settings)
        _state.value = _state.value.copy(settings = updated)
        viewModelScope.launch { useCase.updateSettings(updated) }
    }

    fun showResetConfirmation() {
        _state.value = _state.value.copy(showResetConfirmation = true)
    }

    fun dismissResetConfirmation() {
        _state.value = _state.value.copy(showResetConfirmation = false)
    }

    fun confirmResetBehavior() {
        viewModelScope.launch {
            useCase.resetLearnedBehavior()
            _state.value = _state.value.copy(
                showResetConfirmation = false,
                resetComplete = true
            )
        }
    }

    fun clearResetComplete() {
        _state.value = _state.value.copy(resetComplete = false)
    }
}
