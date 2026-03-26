package com.tutushubham.pokidex.feature_insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase

class InsightsViewModelFactory(
    private val sessionRepository: SessionRepository,
    private val intentRepository: IntentRepository,
    private val behaviorProfileUseCase: BehaviorProfileUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InsightsViewModel(
            insightsUseCase = InsightsUseCase(sessionRepository, intentRepository),
            behaviorProfileUseCase = behaviorProfileUseCase
        ) as T
    }
}
