package com.tutushubham.pokidex.feature_goal_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.domain.usecase.GoalInsightsUseCase
import com.tutushubham.pokidex.core.engine.IntentProgress

class GoalDetailViewModelFactory(
    private val intentId: String,
    private val progressList: List<IntentProgress>,
    private val sessionRepository: SessionRepository,
    private val intentRepository: IntentRepository,
    private val behaviorRepository: BehaviorRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val goalInsightsUseCase = GoalInsightsUseCase(
            sessionRepository,
            intentRepository,
            behaviorRepository
        )
        return GoalDetailViewModel(
            intentId = intentId,
            progressList = progressList,
            sessionRepository = sessionRepository,
            intentRepository = intentRepository,
            behaviorRepository = behaviorRepository,
            goalInsightsUseCase = goalInsightsUseCase
        ) as T
    }
}
