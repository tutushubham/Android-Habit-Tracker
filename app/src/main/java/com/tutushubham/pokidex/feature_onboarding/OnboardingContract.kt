package com.tutushubham.pokidex.feature_onboarding

import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import java.time.LocalDate

data class OnboardingGoal(
    val id: String,
    val title: String,
    val domain: Domain,
    val deadline: LocalDate,
    val targetCount: Int? = null,
    /** Minutes per unit when targetCount is set (e.g. 25 for DSA, 180 for guitar song). Null = time-based. */
    val estimatedMinutesPerUnit: Int? = null
) {
    val isValid: Boolean
        get() = title.isNotBlank() && deadline.isAfter(LocalDate.now())
}

object OnboardingContract {

    sealed interface Event {

        data object ScreenOpened : Event

        data object GeneratePreview : Event

        data object AddGoalRequested : Event
        data class GoalAdded(val goal: OnboardingGoal) : Event
        data class GoalRemoved(val id: String) : Event
        data class GoalUpdated(val goal: OnboardingGoal) : Event

        data class BlockToggled(
            val block: DayBlock,
            val enabled: Boolean
        ) : Event

        data class BlockMinutesChanged(
            val block: DayBlock,
            val minutes: Int
        ) : Event

        data class BlockDomainAssigned(
            val block: DayBlock,
            val domain: Domain
        ) : Event

        data class FocusAdded(
            val domain: Domain,
            val name: String
        ) : Event

        data class FocusRemoved(
            val domain: Domain,
            val name: String
        ) : Event

        data class FocusRenamed(
            val domain: Domain,
            val oldName: String,
            val newName: String
        ) : Event

        data class StrategySelected(
            val domain: Domain,
            val strategy: FocusStrategy
        ) : Event

        data object FinishClicked : Event
    }

    data class State(

        val goals: List<OnboardingGoal> = emptyList(),

        val dayBlocks: Map<DayBlock, Int> = emptyMap(),

        /** One domain per enabled block (e.g. Morning → STUDIES, Evening → FITNESS). */
        val blockToDomain: Map<DayBlock, Domain> = emptyMap(),

        val focuses: Map<Domain, List<String>> = emptyMap(),

        val strategies: Map<Domain, FocusStrategy> = emptyMap(),

        val previewLines: List<String> = emptyList(),

        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed interface Effect {
        data object ExitOnboarding : Effect
        data class ShowMessage(val msg: String) : Effect
    }
}
