package com.tutushubham.pokidex.feature_recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.engine.RecommendationAction
import com.tutushubham.pokidex.core.engine.RecommendationEngine
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecommendationViewModel(
    private val intentRepository: IntentRepository,
    private val behaviorProfileUseCase: BehaviorProfileUseCase,
    @Suppress("UNUSED_PARAMETER")
    private val todayPlannerUseCase: TodayPlannerUseCase,
    private val progressList: List<IntentProgress>
) : ViewModel() {

    private val _state = MutableStateFlow(RecommendationContract.RecommendationState())
    val state: StateFlow<RecommendationContract.RecommendationState> = _state.asStateFlow()

    private val _effect = Channel<RecommendationContract.RecommendationEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: RecommendationContract.RecommendationEvent) {
        when (event) {
            is RecommendationContract.RecommendationEvent.ScreenOpened -> load(event.intentId)
            is RecommendationContract.RecommendationEvent.ActionSelected -> handleAction(event.action)
            RecommendationContract.RecommendationEvent.Dismissed -> dismiss()
        }
    }

    private fun load(intentId: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val intent = intentRepository.getIntentById(intentId)
                ?: throw IllegalArgumentException("Intent not found")
            val profiles = behaviorProfileUseCase.getProfiles(LocalDate.now())
            val profile = profiles[intentId] ?: throw IllegalStateException("No profile for intent")
            val progress = progressList.firstOrNull { it.intentId == intentId }
            val recommendations = RecommendationEngine.generate(profile, progress)

            _state.update {
                RecommendationUiMapper.mapState(
                    intentId = intentId,
                    goalTitle = intent.title,
                    profile = profile,
                    progress = progress,
                    recommendations = recommendations
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(isLoading = false, error = t.message) }
        }
    }

    private fun handleAction(action: RecommendationAction) = viewModelScope.launch {
        when (action) {
            is RecommendationAction.AdjustDeadline -> {
                val intent = intentRepository.getIntentById(action.intentId)
                if (intent != null) {
                    intentRepository.updateIntent(intent.copy(endDate = action.suggestedDate))
                    _effect.send(RecommendationContract.RecommendationEffect.ShowMessage("Deadline adjusted"))
                }
            }
            is RecommendationAction.ReduceScope -> {
                val intent = intentRepository.getIntentById(action.intentId)
                if (intent != null) {
                    intentRepository.updateIntent(intent.copy(targetCount = action.suggestedTarget))
                    _effect.send(RecommendationContract.RecommendationEffect.ShowMessage("Scope reduced"))
                }
            }
            RecommendationAction.TakeBreak -> {
                _effect.send(RecommendationContract.RecommendationEffect.ShowMessage("Break scheduled"))
            }
            RecommendationAction.AddSession -> {
                _effect.send(RecommendationContract.RecommendationEffect.ShowMessage("Extra session added"))
            }
            RecommendationAction.MaintainPace -> {
                _effect.send(RecommendationContract.RecommendationEffect.ShowMessage("Keep up the great work!"))
            }
        }
        behaviorProfileUseCase.invalidateCache()
        _effect.send(RecommendationContract.RecommendationEffect.NavigateBack)
    }

    private fun dismiss() = viewModelScope.launch {
        _effect.send(RecommendationContract.RecommendationEffect.NavigateBack)
    }
}
