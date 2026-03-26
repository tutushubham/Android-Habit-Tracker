package com.tutushubham.pokidex.feature_goal_detail

import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.engine.InsightExplanation
import com.tutushubham.pokidex.core.engine.PredictionInsight
import com.tutushubham.pokidex.core.engine.TrendDirection
import java.time.LocalDate

enum class RecommendationType { SCHEDULE, RECOVERY, STRETCH, WARNING }

data class VelocityInsight(
    val actualPace: Double,
    val requiredPace: Double,
    val trend: TrendDirection,
    val percentAhead: Int,
    val weeklyActivity: List<Float>
)

data class FatigueInsight(
    val levelLabel: String,
    val skipRate: Double,
    val skipStreak: Int,
    val segmentsFilled: Int
)

data class DurationInsight(
    val averageMinutes: Int,
    val staticMinutes: Int?,
    val trend: TrendDirection,
    val trendLabel: String
)

data class Recommendation(
    val title: String,
    val message: String,
    val icon: String,
    val type: RecommendationType,
    val explanation: InsightExplanation? = null
)

data class RecentSessionUi(
    val title: String,
    val date: LocalDate,
    val durationMinutes: Int,
    val badge: String
)

data class GoalInsightsUiModel(
    val goalTitle: String,
    val domain: Domain,
    val milestoneName: String,
    val progressFraction: Float,
    val progressLabel: String,
    val daysRemaining: Int,

    val velocity: VelocityInsight,
    val fatigue: FatigueInsight,
    val prediction: PredictionInsight,
    val duration: DurationInsight,
    val recommendations: List<Recommendation>,

    val streakDays: Int,
    val streakLabel: String,
    val recentSessions: List<RecentSessionUi>
)
