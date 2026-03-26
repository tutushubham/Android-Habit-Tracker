package com.tutushubham.pokidex.feature_recommendation

import com.tutushubham.pokidex.core.engine.RecommendationAction
import com.tutushubham.pokidex.core.engine.ScoredRecommendation

object RecommendationContract {

    sealed interface RecommendationEvent {
        data class ScreenOpened(val intentId: String) : RecommendationEvent
        data class ActionSelected(val action: RecommendationAction) : RecommendationEvent
        data object Dismissed : RecommendationEvent
    }

    data class RecommendationState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val intentId: String = "",
        val goalTitle: String = "",
        val actualPace: Double = 0.0,
        val requiredPace: Double = 0.0,
        val recommendations: List<ScoredRecommendation> = emptyList()
    )

    sealed interface RecommendationEffect {
        data object NavigateBack : RecommendationEffect
        data class ShowMessage(val message: String) : RecommendationEffect
    }
}
