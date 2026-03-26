package com.tutushubham.pokidex.core.engine

import java.time.DayOfWeek

/**
 * Single source of truth for all per-intent behavior signals.
 *
 * Computed once by [BehaviorProfileUseCase] and consumed by all mappers,
 * engines, and UI layers. No screen should independently re-derive these fields.
 */
data class UserBehaviorProfile(
    val intentId: String,

    val fatigue: FatigueSignal,
    val momentum: MomentumSignal,
    val learnedEstimate: LearnedEstimate?,

    val consistencyScore: Double,
    val skipRate: Double,
    val completionRate: Double,
    val peakFocusHours: List<Int>,

    val velocityTrend: TrendDirection,
    val durationTrend: TrendDirection,
    val weeklyMinutesByDay: Map<DayOfWeek, Int>
) {
    fun toBehaviorProfile(): IntentBehaviorProfile =
        IntentBehaviorProfile(fatigue, momentum, learnedEstimate)
}
