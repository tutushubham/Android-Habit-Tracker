package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.model.FatigueSensitivity
import com.tutushubham.pokidex.core.domain.model.PlanningStyle
import com.tutushubham.pokidex.core.domain.model.SystemSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RecommendationType { SCHEDULE, RECOVERY, STRETCH, WARNING }

sealed class RecommendationAction {
    data class AdjustDeadline(val intentId: String, val suggestedDate: LocalDate) : RecommendationAction()
    data class ReduceScope(val intentId: String, val suggestedTarget: Int) : RecommendationAction()
    data object TakeBreak : RecommendationAction()
    data object AddSession : RecommendationAction()
    data object MaintainPace : RecommendationAction()
}

data class ScoredRecommendation(
    val type: RecommendationType,
    val priority: Int,
    val title: String,
    val message: String,
    val icon: String,
    val action: RecommendationAction,
    val explanation: InsightExplanation,
    val score: Double
)

/**
 * Scoring-based recommendation engine.
 *
 * Replaces the rule-based system with a weighted formula:
 *   score = fatigueWeight * fatigueLevel
 *         + deficitWeight * deficit
 *         + consistencyWeight * (1 - consistencyScore)
 *         + velocityWeight * velocityPenalty
 *
 * Weights are adjusted by [SystemSettings.fatigueSensitivity] and [planningStyle].
 */
object RecommendationEngine {

    fun generate(
        profile: UserBehaviorProfile,
        progress: IntentProgress?,
        settings: SystemSettings = SystemSettings()
    ): List<ScoredRecommendation> {
        val results = mutableListOf<ScoredRecommendation>()

        val fatigueWeight = when (settings.fatigueSensitivity) {
            FatigueSensitivity.HIGH -> 3.0
            FatigueSensitivity.MEDIUM -> 2.0
            FatigueSensitivity.LOW -> 1.0
        }
        val deficitWeight = when (settings.planningStyle) {
            PlanningStyle.STRICT -> 3.0
            PlanningStyle.BALANCED -> 2.0
            PlanningStyle.FLEXIBLE -> 1.0
        }
        val consistencyWeight = 1.5
        val velocityWeight = 1.0

        val fatigueLevel = when (profile.fatigue.level) {
            FatigueLevel.HIGH -> 1.0
            FatigueLevel.MEDIUM -> 0.5
            FatigueLevel.LOW -> 0.0
        }
        val deficit = progress?.deficit ?: 0.0
        val velocityPenalty = when (profile.velocityTrend) {
            TrendDirection.DOWN -> 1.0
            TrendDirection.FLAT -> 0.3
            TrendDirection.UP -> 0.0
        }

        val totalScore = fatigueWeight * fatigueLevel +
            deficitWeight * deficit.coerceAtMost(5.0) +
            consistencyWeight * (1.0 - profile.consistencyScore) +
            velocityWeight * velocityPenalty

        if (profile.fatigue.level == FatigueLevel.HIGH) {
            val score = fatigueWeight * 1.0 + consistencyWeight * profile.skipRate
            results += ScoredRecommendation(
                type = RecommendationType.RECOVERY,
                priority = computePriority(score),
                title = "Take a Recovery Day",
                message = "Your skip rate is ${(profile.skipRate * 100).toInt()}% " +
                    "with a ${profile.fatigue.skipStreak}-session skip streak. " +
                    "A lighter day may help you reset.",
                icon = "pause_circle",
                action = RecommendationAction.TakeBreak,
                explanation = InsightExplanation(
                    title = "Recovery Recommended",
                    description = "High fatigue detected from sustained skipping pattern.",
                    factors = listOf(
                        "Fatigue: HIGH",
                        "Skip rate: ${(profile.skipRate * 100).toInt()}%",
                        "Skip streak: ${profile.fatigue.skipStreak}"
                    )
                ),
                score = score
            )
        }

        if (deficit > 0 && progress != null) {
            val score = deficitWeight * deficit + velocityWeight * velocityPenalty
            results += ScoredRecommendation(
                type = RecommendationType.SCHEDULE,
                priority = computePriority(score),
                title = "Add Extra Session",
                message = "You're behind by %.1f units/day. ".format(deficit) +
                    "An extra session today would help close the gap.",
                icon = "add_circle",
                action = RecommendationAction.AddSession,
                explanation = InsightExplanation(
                    title = "Behind Schedule",
                    description = "Current pace is insufficient to meet the deadline.",
                    factors = listOf(
                        "Deficit: %.1f units/day".format(deficit),
                        "Required pace: %.1f/day".format(progress.requiredUnitsPerDay),
                        "Current pace: %.1f/day".format(progress.currentPace)
                    )
                ),
                score = score
            )
        }

        if (progress != null && progress.isBehind && progress.daysRemaining > 7) {
            val suggestedDate = LocalDate.now().plusDays(
                (progress.daysRemaining * 1.3).toLong().coerceAtMost(365)
            )
            val score = deficitWeight * deficit * 0.8
            results += ScoredRecommendation(
                type = RecommendationType.WARNING,
                priority = computePriority(score),
                title = "Consider Deadline Extension",
                message = "At current pace, you may not finish on time. " +
                    "Extending by ${(progress.daysRemaining * 0.3).toInt()} days could reduce pressure.",
                icon = "schedule",
                action = RecommendationAction.AdjustDeadline(
                    intentId = profile.intentId,
                    suggestedDate = suggestedDate
                ),
                explanation = InsightExplanation(
                    title = "Deadline Risk",
                    description = "Pace indicates potential miss without adjustment.",
                    factors = listOf(
                        "Days remaining: ${progress.daysRemaining}",
                        "Completion: ${(progress.progressFraction * 100).toInt()}%",
                        "Velocity trend: ${profile.velocityTrend.name}"
                    )
                ),
                score = score
            )
        }

        if (profile.momentum.isConsistent && profile.velocityTrend == TrendDirection.UP) {
            val score = 1.0
            results += ScoredRecommendation(
                type = RecommendationType.STRETCH,
                priority = computePriority(score),
                title = "Maintain Your Momentum",
                message = "You're on a ${profile.momentum.streakDays}-day streak with " +
                    "improving velocity. Keep it up!",
                icon = "trending_up",
                action = RecommendationAction.MaintainPace,
                explanation = InsightExplanation(
                    title = "Strong Momentum",
                    description = "Consistent performance with upward velocity trend.",
                    factors = listOf(
                        "Streak: ${profile.momentum.streakDays} days",
                        "Completion rate: ${(profile.completionRate * 100).toInt()}%",
                        "Velocity: ${profile.velocityTrend.name}"
                    )
                ),
                score = score
            )
        }

        if (profile.consistencyScore < 0.3 && profile.fatigue.level != FatigueLevel.HIGH) {
            val score = consistencyWeight * (1.0 - profile.consistencyScore)
            results += ScoredRecommendation(
                type = RecommendationType.SCHEDULE,
                priority = computePriority(score),
                title = "Build Consistency",
                message = "Your consistency is at ${(profile.consistencyScore * 100).toInt()}%. " +
                    "Try completing at least one session daily to build the habit.",
                icon = "repeat",
                action = RecommendationAction.AddSession,
                explanation = InsightExplanation(
                    title = "Low Consistency",
                    description = "Irregular practice pattern detected.",
                    factors = listOf(
                        "Consistency: ${(profile.consistencyScore * 100).toInt()}%",
                        "Completion rate: ${(profile.completionRate * 100).toInt()}%"
                    )
                ),
                score = score
            )
        }

        return results.sortedByDescending { it.score }
    }

    private fun computePriority(score: Double): Int = when {
        score >= 5.0 -> 1
        score >= 3.0 -> 2
        score >= 1.0 -> 3
        else -> 4
    }
}
