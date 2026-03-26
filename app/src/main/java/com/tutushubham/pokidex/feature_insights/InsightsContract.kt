package com.tutushubham.pokidex.feature_insights

import com.tutushubham.pokidex.core.engine.UserBehaviorProfile

object InsightsContract {

    sealed interface InsightsEvent {
        data object ScreenOpened : InsightsEvent
        data object Refresh : InsightsEvent
    }

    data class InsightsState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val insights: InsightsUiModel? = null,
        val profiles: Map<String, UserBehaviorProfile> = emptyMap()
    )

    sealed interface InsightsEffect {
        data class ShowMessage(val message: String) : InsightsEffect
    }
}
