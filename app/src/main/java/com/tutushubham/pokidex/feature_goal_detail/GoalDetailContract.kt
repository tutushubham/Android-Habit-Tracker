package com.tutushubham.pokidex.feature_goal_detail

import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

object GoalDetailContract {

    sealed interface GoalDetailEvent {
        data object ScreenOpened : GoalDetailEvent
        data object Refresh : GoalDetailEvent
        data object ToggleEditMode : GoalDetailEvent
        data class UpdateTitle(val title: String) : GoalDetailEvent
        data class UpdateDomain(val domain: Domain) : GoalDetailEvent
        data class UpdateTarget(val target: Int) : GoalDetailEvent
        data class UpdateDeadline(val date: LocalDate) : GoalDetailEvent
        data object SaveChanges : GoalDetailEvent
    }

    data class GoalDetailState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val insights: GoalInsightsUiModel? = null,
        val isEditing: Boolean = false,
        val editTitle: String = "",
        val editDomain: Domain = Domain.STUDIES,
        val editTarget: Int = 0,
        val editDeadline: LocalDate = LocalDate.now()
    )

    sealed interface GoalDetailEffect {
        data class ShowMessage(val message: String) : GoalDetailEffect
        data object NavigateBack : GoalDetailEffect
    }
}
