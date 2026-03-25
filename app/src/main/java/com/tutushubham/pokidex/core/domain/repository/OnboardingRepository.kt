package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import java.time.LocalDate

/**
 * Persists onboarding state in a single atomic transaction.
 * Caller is responsible for effects (e.g. exit onboarding, show error).
 */
interface OnboardingRepository {
    suspend fun persist(state: OnboardingPersistState): Result<Unit>
}

/**
 * Data required to persist onboarding. Kept in core so that
 * [OnboardingRepository] does not depend on feature contracts.
 */
data class OnboardingPersistState(
    val dayBlocks: Map<DayBlock, Int>,
    val blockToDomain: Map<DayBlock, Domain>,
    val focuses: Map<Domain, List<String>>,
    val strategies: Map<Domain, FocusStrategy>,
    val goals: List<OnboardingGoalInput>
)

/**
 * Goal data for persistence; maps to [GoalIntent].
 */
data class OnboardingGoalInput(
    val id: String,
    val title: String,
    val domain: Domain,
    val deadline: LocalDate,
    val targetCount: Int?,
    val estimatedMinutesPerUnit: Int? = null
)
