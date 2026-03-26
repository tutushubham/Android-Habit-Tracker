package com.tutushubham.pokidex.feature_insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class InsightsViewModel(
    private val insightsUseCase: InsightsUseCase,
    private val behaviorProfileUseCase: BehaviorProfileUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsContract.InsightsState())
    val state: StateFlow<InsightsContract.InsightsState> = _state.asStateFlow()

    private val _effect = Channel<InsightsContract.InsightsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        onEvent(InsightsContract.InsightsEvent.ScreenOpened)
    }

    fun onEvent(event: InsightsContract.InsightsEvent) {
        when (event) {
            InsightsContract.InsightsEvent.ScreenOpened,
            InsightsContract.InsightsEvent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val today = LocalDate.now()
                val profiles = behaviorProfileUseCase.getProfiles(today)
                val model = insightsUseCase.loadInsights(today, profiles)
                _state.value = InsightsContract.InsightsState(
                    isLoading = false,
                    insights = model,
                    profiles = profiles
                )
            } catch (e: Exception) {
                val message = e.message ?: "Failed to load insights"
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = message
                )
                _effect.send(InsightsContract.InsightsEffect.ShowMessage(message))
            }
        }
    }
}
