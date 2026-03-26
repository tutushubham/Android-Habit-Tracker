package com.tutushubham.pokidex.feature_goal_detail

import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.engine.FatigueLevel
import com.tutushubham.pokidex.core.engine.FatigueSignal
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.engine.LearnedEstimate
import com.tutushubham.pokidex.core.engine.MomentumSignal
import com.tutushubham.pokidex.core.engine.PredictionInsight
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object GoalInsightsMapper {

    fun map(
        intent: GoalIntent,
        progress: IntentProgress?,
        sessions30d: List<Session>,
        fatigue: FatigueSignal,
        momentum: MomentumSignal,
        learnedEstimate: LearnedEstimate?,
        today: LocalDate,
        profile: UserBehaviorProfile? = null
    ): GoalInsightsUiModel {
        val intentSessions = sessions30d.filter { it.intentId == intent.id }
        val sessions7d = intentSessions.filter { it.date >= today.minusDays(7) }
        val sessionsPrev7d = intentSessions.filter {
            it.date >= today.minusDays(14) && it.date < today.minusDays(7)
        }

        val velocity = deriveVelocity(sessions7d, sessionsPrev7d, progress, today)
        val fatigueInsight = deriveFatigue(fatigue)
        val prediction = derivePrediction(progress, velocity, momentum, intent, today)
        val duration = deriveDuration(intentSessions, sessions7d, sessionsPrev7d, learnedEstimate, intent)
        val recommendations = deriveRecommendations(velocity, fatigueInsight, prediction, progress, duration)
        val recentSessions = deriveRecentSessions(intentSessions, intent)

        val frac = progress?.progressFraction ?: 0f
        val daysRem = progress?.daysRemaining ?: ChronoUnit.DAYS.between(today, intent.endDate).toInt().coerceAtLeast(0)

        return GoalInsightsUiModel(
            goalTitle = intent.title,
            domain = intent.domain,
            milestoneName = deriveMilestone(frac),
            progressFraction = frac,
            progressLabel = "${(frac * 100).toInt()}%",
            daysRemaining = daysRem,
            velocity = velocity,
            fatigue = fatigueInsight,
            prediction = prediction,
            duration = duration,
            recommendations = recommendations,
            streakDays = momentum.streakDays,
            streakLabel = if (momentum.streakDays > 0) "${momentum.streakDays} days of consistent work" else "No current streak",
            recentSessions = recentSessions
        )
    }

    // ─── Velocity ─────────────────────────────────────────────

    internal fun deriveVelocity(
        sessions7d: List<Session>,
        sessionsPrev7d: List<Session>,
        progress: IntentProgress?,
        today: LocalDate
    ): VelocityInsight {
        val recentCompleted = sessions7d.count { it.status == SessionStatus.COMPLETED }
        val prevCompleted = sessionsPrev7d.count { it.status == SessionStatus.COMPLETED }

        val actualPace = progress?.currentPace ?: (recentCompleted.toDouble() / 7)
        val requiredPace = progress?.requiredUnitsPerDay ?: 0.0

        val trend = when {
            recentCompleted > prevCompleted + 1 -> TrendDirection.UP
            recentCompleted < prevCompleted - 1 -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }

        val percentAhead = if (requiredPace > 0) {
            ((actualPace - requiredPace) / requiredPace * 100).toInt()
        } else 0

        val weeklyActivity = deriveWeeklyBars(sessions7d, today)

        return VelocityInsight(actualPace, requiredPace, trend, percentAhead, weeklyActivity)
    }

    private fun deriveWeeklyBars(sessions: List<Session>, today: LocalDate): List<Float> {
        val weekStart = today.minusDays(6)
        val dayMinutes = mutableMapOf<DayOfWeek, Int>()
        DayOfWeek.entries.forEach { dayMinutes[it] = 0 }

        sessions
            .filter { it.date in weekStart..today && it.status == SessionStatus.COMPLETED }
            .forEach { s ->
                val dow = s.date.dayOfWeek
                dayMinutes[dow] = (dayMinutes[dow] ?: 0) + (s.actualMinutes ?: s.plannedMinutes)
            }

        val max = dayMinutes.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        return DayOfWeek.entries.map { (dayMinutes[it] ?: 0).toFloat() / max }
    }

    // ─── Fatigue ─────────────────────────────────────────────

    internal fun deriveFatigue(signal: FatigueSignal): FatigueInsight {
        val (label, segments) = when (signal.level) {
            FatigueLevel.LOW -> "Low" to 1
            FatigueLevel.MEDIUM -> "Medium" to 3
            FatigueLevel.HIGH -> "High" to 5
        }
        return FatigueInsight(
            levelLabel = label,
            skipRate = signal.recentSkipRate,
            skipStreak = signal.skipStreak,
            segmentsFilled = segments
        )
    }

    // ─── Prediction ─────────────────────────────────────────────

    internal fun derivePrediction(
        progress: IntentProgress?,
        velocity: VelocityInsight,
        momentum: MomentumSignal,
        intent: GoalIntent,
        today: LocalDate
    ): PredictionInsight {
        if (progress == null || velocity.actualPace <= 0) {
            return PredictionInsight(
                predictedDate = intent.endDate,
                confidence = 0.0,
                confidenceLabel = "Insufficient data"
            )
        }

        val remaining = progress.remainingUnits
        val daysNeeded = (remaining / velocity.actualPace).toInt().coerceAtLeast(1)
        val predictedDate = today.plusDays(daysNeeded.toLong())

        val consistency = momentum.recentCompletionRate
        val paceVariance = if (velocity.requiredPace > 0)
            (velocity.actualPace / velocity.requiredPace).coerceIn(0.0, 2.0) / 2.0
        else 0.5

        val confidence = ((consistency * 0.6 + paceVariance * 0.4) * 100).coerceIn(0.0, 100.0)

        val confidenceLabel = when {
            confidence >= 80 -> "High confidence"
            confidence >= 50 -> "Moderate confidence"
            else -> "Low confidence"
        }

        return PredictionInsight(predictedDate, confidence / 100.0, confidenceLabel)
    }

    // ─── Duration ─────────────────────────────────────────────

    internal fun deriveDuration(
        allSessions: List<Session>,
        sessions7d: List<Session>,
        sessionsPrev7d: List<Session>,
        learnedEstimate: LearnedEstimate?,
        intent: GoalIntent
    ): DurationInsight {
        val completed = allSessions.filter {
            it.status == SessionStatus.COMPLETED && it.actualMinutes != null && it.actualMinutes > 0
        }

        val avgMinutes = if (completed.isNotEmpty())
            completed.sumOf { it.actualMinutes!! } / completed.size
        else
            learnedEstimate?.effectiveMinutesPerUnit ?: intent.estimatedMinutesPerUnit ?: 0

        val recentAvg = avgOf(sessions7d)
        val prevAvg = avgOf(sessionsPrev7d)

        val trend = when {
            recentAvg == 0 || prevAvg == 0 -> TrendDirection.FLAT
            recentAvg < prevAvg - 3 -> TrendDirection.DOWN
            recentAvg > prevAvg + 3 -> TrendDirection.UP
            else -> TrendDirection.FLAT
        }

        val trendLabel = when (trend) {
            TrendDirection.UP -> "Trending slower (+${recentAvg - prevAvg}m)"
            TrendDirection.DOWN -> "Getting faster (${recentAvg - prevAvg}m)"
            TrendDirection.FLAT -> "Consistent pace"
        }

        return DurationInsight(
            averageMinutes = avgMinutes,
            staticMinutes = intent.estimatedMinutesPerUnit,
            trend = trend,
            trendLabel = trendLabel
        )
    }

    private fun avgOf(sessions: List<Session>): Int {
        val completed = sessions.filter {
            it.status == SessionStatus.COMPLETED && it.actualMinutes != null && it.actualMinutes > 0
        }
        return if (completed.isEmpty()) 0 else completed.sumOf { it.actualMinutes!! } / completed.size
    }

    // ─── Recommendations ─────────────────────────────────────────────

    internal fun deriveRecommendations(
        velocity: VelocityInsight,
        fatigue: FatigueInsight,
        prediction: PredictionInsight,
        progress: IntentProgress?,
        duration: DurationInsight
    ): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()

        if (fatigue.levelLabel == "High") {
            recs.add(
                Recommendation(
                    title = "Recovery Needed",
                    message = "You've skipped ${fatigue.skipStreak} sessions in a row. Consider shorter sessions (${(duration.averageMinutes * 0.6).toInt()}m) to rebuild momentum.",
                    icon = "🛡️",
                    type = RecommendationType.RECOVERY
                )
            )
        }

        if (velocity.percentAhead < -15 && progress?.isBehind == true) {
            recs.add(
                Recommendation(
                    title = "Schedule Extra Sessions",
                    message = "You're ${-velocity.percentAhead}% behind pace. Adding one extra session per day could close the gap by ${prediction.predictedDate ?: "the deadline"}.",
                    icon = "📅",
                    type = RecommendationType.SCHEDULE
                )
            )
        }

        if (velocity.percentAhead > 20 && fatigue.levelLabel == "Low") {
            recs.add(
                Recommendation(
                    title = "Stretch Opportunity",
                    message = "You're ${velocity.percentAhead}% ahead. Consider increasing session depth or adding advanced topics.",
                    icon = "🚀",
                    type = RecommendationType.STRETCH
                )
            )
        }

        if (velocity.trend == TrendDirection.DOWN && fatigue.levelLabel != "High") {
            recs.add(
                Recommendation(
                    title = "Velocity Dropping",
                    message = "Your completion rate is declining. Rebalance your plan to maintain deadline targets.",
                    icon = "⚠️",
                    type = RecommendationType.WARNING
                )
            )
        }

        if (duration.trend == TrendDirection.UP && duration.averageMinutes > (duration.staticMinutes ?: Int.MAX_VALUE)) {
            recs.add(
                Recommendation(
                    title = "Sessions Taking Longer",
                    message = "Average duration (${duration.averageMinutes}m) exceeds your estimate (${duration.staticMinutes}m). Consider splitting into smaller units.",
                    icon = "⏱️",
                    type = RecommendationType.WARNING
                )
            )
        }

        if (recs.isEmpty()) {
            recs.add(
                Recommendation(
                    title = "On Track",
                    message = "Your pace and consistency are solid. Keep up the current rhythm.",
                    icon = "✅",
                    type = RecommendationType.STRETCH
                )
            )
        }

        return recs
    }

    // ─── Recent Sessions ─────────────────────────────────────────────

    private fun deriveRecentSessions(
        sessions: List<Session>,
        intent: GoalIntent
    ): List<RecentSessionUi> {
        return sessions
            .filter { it.status == SessionStatus.COMPLETED }
            .sortedByDescending { it.date }
            .take(5)
            .map { session ->
                RecentSessionUi(
                    title = intent.title,
                    date = session.date,
                    durationMinutes = session.actualMinutes ?: session.plannedMinutes,
                    badge = "Completed"
                )
            }
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun deriveMilestone(fraction: Float): String = when {
        fraction >= 0.9f -> "Final Push"
        fraction >= 0.75f -> "Advanced Stage"
        fraction >= 0.5f -> "Halfway Milestone"
        fraction >= 0.25f -> "Building Momentum"
        else -> "Getting Started"
    }
}
