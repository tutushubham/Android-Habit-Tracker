package com.tutushubham.pokidex.feature_insights

import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.DayOfWeek

data class DayActivity(
    val dayOfWeek: DayOfWeek,
    val totalMinutes: Int,
    val fractionOfMax: Float
)

enum class CompletionTrend { UP, STEADY, DOWN }

data class GoalCompletion(
    val intentId: String,
    val title: String,
    val domain: Domain,
    val completionRate: Float,
    val trend: CompletionTrend,
    val trendLabel: String
)

enum class HabitType(val title: String, val description: String, val icon: String) {
    EARLY_BIRD(
        "Early Bird",
        "Most of your high-impact tasks are completed before noon. Lean into this by blocking out focused morning time.",
        "☀️"
    ),
    NIGHT_OWL(
        "Night Owl",
        "You're most productive in the evening. Schedule deep work sessions after dinner for maximum output.",
        "🌙"
    ),
    SPRINT_FINISHER(
        "Sprint Finisher",
        "You batch tasks on certain days, resulting in higher efficiency than your daily average.",
        "⚡"
    ),
    STEADY_PACER(
        "Steady Pacer",
        "You distribute effort evenly across the week. Consistency is your superpower.",
        "🎯"
    ),
    REFLECTIVE_PLANNER(
        "Reflective Planner",
        "Evening review sessions boost your next-day success rate. Keep that routine!",
        "🧠"
    )
}

data class InsightsUiModel(
    val peakFocusTime: Pair<Int, Int>,
    val streakDays: Int,
    val personalBestStreak: Int,
    val weeklyActivity: List<DayActivity>,
    val completionRates: List<GoalCompletion>,
    val habitArchetype: HabitType,
    val secondaryArchetype: HabitType?,
    val summaryInsight: String,
    val totalMinutesThisWeek: Int,
    val totalSessionsThisWeek: Int
)
