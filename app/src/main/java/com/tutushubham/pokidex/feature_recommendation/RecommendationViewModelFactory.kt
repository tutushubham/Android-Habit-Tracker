package com.tutushubham.pokidex.feature_recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import com.tutushubham.pokidex.core.domain.usecase.TodayPlannerUseCase
import com.tutushubham.pokidex.core.engine.IntentProgress

class RecommendationViewModelFactory(
    private val intentRepository: IntentRepository,
    private val behaviorProfileUseCase: BehaviorProfileUseCase,
    private val todayPlannerUseCase: TodayPlannerUseCase,
    private val progressList: List<IntentProgress>
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecommendationViewModel(
            intentRepository, behaviorProfileUseCase, todayPlannerUseCase, progressList
        ) as T
    }
}
