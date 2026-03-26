package com.tutushubham.pokidex.feature_goal_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.domain.usecase.GoalInsightsUseCase
import com.tutushubham.pokidex.core.engine.IntentProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GoalDetailViewModel(
    private val intentId: String,
    private val progressList: List<IntentProgress>,
    @Suppress("UNUSED_PARAMETER") private val sessionRepository: SessionRepository,
    private val intentRepository: IntentRepository,
    @Suppress("UNUSED_PARAMETER") private val behaviorRepository: BehaviorRepository,
    private val goalInsightsUseCase: GoalInsightsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GoalDetailContract.GoalDetailState())
    val state: StateFlow<GoalDetailContract.GoalDetailState> = _state.asStateFlow()

    private val _effect = Channel<GoalDetailContract.GoalDetailEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        onEvent(GoalDetailContract.GoalDetailEvent.ScreenOpened)
    }

    fun onEvent(event: GoalDetailContract.GoalDetailEvent) {
        when (event) {
            GoalDetailContract.GoalDetailEvent.ScreenOpened -> load()
            GoalDetailContract.GoalDetailEvent.Refresh -> load()
            GoalDetailContract.GoalDetailEvent.ToggleEditMode -> toggleEditMode()
            is GoalDetailContract.GoalDetailEvent.UpdateTitle ->
                _state.update { it.copy(editTitle = event.title) }
            is GoalDetailContract.GoalDetailEvent.UpdateDomain ->
                _state.update { it.copy(editDomain = event.domain) }
            is GoalDetailContract.GoalDetailEvent.UpdateTarget ->
                _state.update { it.copy(editTarget = event.target.coerceAtLeast(0)) }
            is GoalDetailContract.GoalDetailEvent.UpdateDeadline ->
                _state.update { it.copy(editDeadline = event.date) }
            GoalDetailContract.GoalDetailEvent.SaveChanges -> saveChanges()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val model = goalInsightsUseCase.load(intentId, progressList)
                _state.update {
                    it.copy(isLoading = false, insights = model, error = null)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load goal insights"
                    )
                }
            }
        }
    }

    private fun toggleEditMode() {
        viewModelScope.launch {
            _state.update { s ->
                if (!s.isEditing) {
                    val insights = s.insights ?: return@update s
                    val intent = intentRepository.getIntentById(intentId) ?: return@update s
                    s.copy(
                        isEditing = true,
                        editTitle = insights.goalTitle,
                        editDomain = insights.domain,
                        editTarget = intent.targetCount ?: 0,
                        editDeadline = intent.endDate
                    )
                } else {
                    s.copy(isEditing = false)
                }
            }
        }
    }

    private fun saveChanges() {
        viewModelScope.launch {
            val s = _state.value
            val intent = intentRepository.getIntentById(intentId)
            if (intent == null) {
                _effect.send(GoalDetailContract.GoalDetailEffect.ShowMessage("Goal not found"))
                return@launch
            }
            if (s.editTitle.isBlank()) {
                _effect.send(GoalDetailContract.GoalDetailEffect.ShowMessage("Title cannot be empty"))
                return@launch
            }
            try {
                val updated = intent.copy(
                    title = s.editTitle.trim(),
                    domain = s.editDomain,
                    targetCount = s.editTarget.takeIf { it > 0 },
                    endDate = s.editDeadline
                )
                intentRepository.updateIntent(updated)
                _state.update { it.copy(isEditing = false) }
                _effect.send(GoalDetailContract.GoalDetailEffect.ShowMessage("Goal updated"))
                load()
            } catch (e: Exception) {
                _effect.send(
                    GoalDetailContract.GoalDetailEffect.ShowMessage(
                        e.message ?: "Failed to save changes"
                    )
                )
            }
        }
    }
}
