package com.tutushubham.pokidex.ui.components

import com.tutushubham.pokidex.core.engine.FatigueLevel
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import com.tutushubham.pokidex.ui.theme.SemanticColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Enforces the "No Hidden Intelligence" rule.
 *
 * Every field in [UserBehaviorProfile] MUST have a corresponding [MappedProfileField].
 * If a new field is added to the profile, this mapper must be updated or the app
 * will have untested intelligence paths.
 *
 * Screens consume [MappedProfileField] list and render each.
 */
data class MappedProfileField(
    val label: String,
    val value: String,
    val color: Color,
    val category: FieldCategory
)

enum class FieldCategory {
    FATIGUE, MOMENTUM, LEARNING, VELOCITY, ACTIVITY
}

object ProfileUiMapper {

    fun mapAll(profile: UserBehaviorProfile, colors: SemanticColorScheme): List<MappedProfileField> = listOf(
        MappedProfileField(
            label = "Fatigue Level",
            value = profile.fatigue.level.name.lowercase().replaceFirstChar { it.uppercase() },
            color = when (profile.fatigue.level) {
                FatigueLevel.HIGH -> colors.deficit
                FatigueLevel.MEDIUM -> colors.warning
                FatigueLevel.LOW -> colors.momentum
            },
            category = FieldCategory.FATIGUE
        ),
        MappedProfileField(
            label = "Skip Streak",
            value = "${profile.fatigue.skipStreak} sessions",
            color = if (profile.fatigue.skipStreak > 2) colors.deficit else colors.neutral,
            category = FieldCategory.FATIGUE
        ),
        MappedProfileField(
            label = "Skip Rate",
            value = "${(profile.skipRate * 100).toInt()}%",
            color = if (profile.skipRate > 0.3) colors.deficit else colors.neutral,
            category = FieldCategory.FATIGUE
        ),
        MappedProfileField(
            label = "Streak",
            value = "${profile.momentum.streakDays} days",
            color = if (profile.momentum.streakDays >= 3) colors.momentum else colors.neutral,
            category = FieldCategory.MOMENTUM
        ),
        MappedProfileField(
            label = "Completion Rate",
            value = "${(profile.completionRate * 100).toInt()}%",
            color = if (profile.completionRate >= 0.7) colors.momentum else colors.warning,
            category = FieldCategory.MOMENTUM
        ),
        MappedProfileField(
            label = "Consistency",
            value = "${(profile.consistencyScore * 100).toInt()}%",
            color = if (profile.consistencyScore >= 0.7) colors.momentum else colors.warning,
            category = FieldCategory.MOMENTUM
        ),
        MappedProfileField(
            label = "Learned Estimate",
            value = profile.learnedEstimate?.let {
                "${it.effectiveMinutesPerUnit}m/unit (${(it.confidence * 100).toInt()}%)"
            } ?: "Learning...",
            color = when {
                profile.learnedEstimate == null -> colors.neutral
                profile.learnedEstimate.confidence >= 0.8 -> colors.momentum
                else -> colors.warning
            },
            category = FieldCategory.LEARNING
        ),
        MappedProfileField(
            label = "Velocity Trend",
            value = when (profile.velocityTrend) {
                TrendDirection.UP -> "Improving ↑"
                TrendDirection.DOWN -> "Declining ↓"
                TrendDirection.FLAT -> "Steady →"
            },
            color = when (profile.velocityTrend) {
                TrendDirection.UP -> colors.momentum
                TrendDirection.DOWN -> colors.deficit
                TrendDirection.FLAT -> colors.neutral
            },
            category = FieldCategory.VELOCITY
        ),
        MappedProfileField(
            label = "Duration Trend",
            value = when (profile.durationTrend) {
                TrendDirection.UP -> "Increasing ↑"
                TrendDirection.DOWN -> "Decreasing ↓"
                TrendDirection.FLAT -> "Stable →"
            },
            color = colors.neutral,
            category = FieldCategory.VELOCITY
        ),
        MappedProfileField(
            label = "Peak Focus",
            value = "${formatHour(profile.peakFocusHours.firstOrNull() ?: 9)}–" +
                formatHour((profile.peakFocusHours.lastOrNull() ?: 10) + 1),
            color = colors.momentum,
            category = FieldCategory.ACTIVITY
        ),
        MappedProfileField(
            label = "Weekly Activity",
            value = "${profile.weeklyMinutesByDay.values.sum()}m this week",
            color = colors.neutral,
            category = FieldCategory.ACTIVITY
        )
    )

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
