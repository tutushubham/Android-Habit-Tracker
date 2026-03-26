package com.tutushubham.pokidex.feature_recommendation

import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.engine.ScoredRecommendation
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile

object RecommendationUiMapper {

    fun mapState(
        intentId: String,
        goalTitle: String,
        profile: UserBehaviorProfile,
        progress: IntentProgress?,
        recommendations: List<ScoredRecommendation>
    ): RecommendationContract.RecommendationState {
        return RecommendationContract.RecommendationState(
            isLoading = false,
            intentId = intentId,
            goalTitle = goalTitle,
            actualPace = progress?.currentPace ?: 0.0,
            requiredPace = progress?.requiredUnitsPerDay ?: 0.0,
            recommendations = recommendations
        )
    }
}
