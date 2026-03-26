package com.tutushubham.pokidex.feature_insights

import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import java.time.DayOfWeek
import java.time.LocalDate

object InsightsMapper {

    fun map(
        sessions7d: List<Session>,
        sessions30d: List<Session>,
        intents: List<GoalIntent>,
        today: LocalDate,
        profiles: Map<String, UserBehaviorProfile> = emptyMap()
    ): InsightsUiModel {
        val peakFocus = derivePeakFocusTime(sessions30d)
        val streak = deriveStreak(sessions30d, today)
        val weekly = deriveWeeklyActivity(sessions7d, today)
        val completions = deriveCompletionRates(sessions7d, sessions30d, intents)
        val archetype = deriveArchetype(sessions30d)
        val secondary = deriveSecondaryArchetype(sessions30d, archetype)
        val summary = deriveSummary(peakFocus, streak, completions, archetype)

        val weekCompletedSessions = sessions7d.filter { it.status == SessionStatus.COMPLETED }

        return InsightsUiModel(
            peakFocusTime = peakFocus,
            streakDays = streak.first,
            personalBestStreak = streak.second,
            weeklyActivity = weekly,
            completionRates = completions,
            habitArchetype = archetype,
            secondaryArchetype = secondary,
            summaryInsight = summary,
            totalMinutesThisWeek = weekCompletedSessions.sumOf { it.actualMinutes ?: it.plannedMinutes },
            totalSessionsThisWeek = weekCompletedSessions.size
        )
    }

    /**
     * Group completed sessions by their start hour (from startedAt or block fallback),
     * find the 2-hour window with the highest completion rate.
     */
    internal fun derivePeakFocusTime(sessions: List<Session>): Pair<Int, Int> {
        val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return 9 to 11

        val hourCounts = mutableMapOf<Int, Int>()
        completed.forEach { session ->
            val hour = session.startedAt
                ?.atZone(java.time.ZoneId.systemDefault())
                ?.hour
                ?: blockToHour(session.block)
            hourCounts[hour] = (hourCounts[hour] ?: 0) + 1
        }

        var bestStart = 9
        var bestCount = 0
        for (h in 0..22) {
            val windowCount = (hourCounts[h] ?: 0) + (hourCounts[h + 1] ?: 0)
            if (windowCount > bestCount) {
                bestCount = windowCount
                bestStart = h
            }
        }

        return bestStart to (bestStart + 2).coerceAtMost(24)
    }

    /**
     * Current streak = consecutive days with at least one completed session, ending today.
     * Personal best = longest streak found in the 30d window.
     */
    internal fun deriveStreak(sessions: List<Session>, today: LocalDate): Pair<Int, Int> {
        val completedDays = sessions
            .filter { it.status == SessionStatus.COMPLETED }
            .map { it.date }
            .toSortedSet()

        if (completedDays.isEmpty()) return 0 to 0

        var currentStreak = 0
        var day = today
        while (day in completedDays) {
            currentStreak++
            day = day.minusDays(1)
        }

        val sortedDays = completedDays.toList()
        var bestStreak = 1
        var runLength = 1
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i].toEpochDay() - sortedDays[i - 1].toEpochDay() == 1L) {
                runLength++
                if (runLength > bestStreak) bestStreak = runLength
            } else {
                runLength = 1
            }
        }

        return currentStreak to bestStreak
    }

    /**
     * Group sessions by DayOfWeek for the last 7 days.
     * Normalize fractions against the max-minutes day.
     */
    internal fun deriveWeeklyActivity(sessions: List<Session>, today: LocalDate): List<DayActivity> {
        val weekStart = today.minusDays(6)
        val daySums = mutableMapOf<DayOfWeek, Int>()

        DayOfWeek.entries.forEach { daySums[it] = 0 }

        sessions
            .filter { it.date in weekStart..today && it.status == SessionStatus.COMPLETED }
            .forEach { session ->
                val dow = session.date.dayOfWeek
                daySums[dow] = (daySums[dow] ?: 0) + (session.actualMinutes ?: session.plannedMinutes)
            }

        val maxMinutes = daySums.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        return DayOfWeek.entries.map { dow ->
            val mins = daySums[dow] ?: 0
            DayActivity(
                dayOfWeek = dow,
                totalMinutes = mins,
                fractionOfMax = mins.toFloat() / maxMinutes
            )
        }
    }

    /**
     * Per-intent completion rate = completed / total sessions.
     * Trend compares last-7d rate vs previous-23d rate.
     */
    internal fun deriveCompletionRates(
        sessions7d: List<Session>,
        sessions30d: List<Session>,
        intents: List<GoalIntent>
    ): List<GoalCompletion> {
        val intentMap = intents.associateBy { it.id }
        val allIntentIds = sessions30d.map { it.intentId }.toSet()

        return allIntentIds.mapNotNull { intentId ->
            val intent = intentMap[intentId] ?: return@mapNotNull null

            val recent = sessions7d.filter { it.intentId == intentId }
            val recentRate = completionRate(recent)

            val older = sessions30d.filter { it.intentId == intentId && it !in sessions7d }
            val olderRate = completionRate(older)

            val overall = completionRate(sessions30d.filter { it.intentId == intentId })

            val diff = recentRate - olderRate
            val (trend, label) = when {
                diff > 0.05f -> CompletionTrend.UP to "+${(diff * 100).toInt()}%"
                diff < -0.05f -> CompletionTrend.DOWN to "${(diff * 100).toInt()}%"
                else -> CompletionTrend.STEADY to "Steady"
            }

            GoalCompletion(
                intentId = intentId,
                title = intent.title,
                domain = intent.domain,
                completionRate = overall,
                trend = trend,
                trendLabel = label
            )
        }.sortedByDescending { it.completionRate }
    }

    internal fun deriveArchetype(sessions: List<Session>): HabitType {
        val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return HabitType.STEADY_PACER

        val morningRatio = completed.count { hourOf(it) < 12 }.toFloat() / completed.size
        val eveningRatio = completed.count { hourOf(it) >= 18 }.toFloat() / completed.size

        val dayDistribution = completed.groupBy { it.date.dayOfWeek }
        val maxDayCount = dayDistribution.values.maxOfOrNull { it.size } ?: 0
        val avgDayCount = completed.size.toFloat() / dayDistribution.size.coerceAtLeast(1)
        val isBatchy = maxDayCount > avgDayCount * 1.6f

        val activeDays = completed.map { it.date }.toSet().size
        val spanDays = completed.let {
            val min = it.minOf { s -> s.date }
            val max = it.maxOf { s -> s.date }
            (max.toEpochDay() - min.toEpochDay() + 1).toInt().coerceAtLeast(1)
        }
        val consistency = activeDays.toFloat() / spanDays

        return when {
            morningRatio >= 0.6f -> HabitType.EARLY_BIRD
            eveningRatio >= 0.5f -> HabitType.NIGHT_OWL
            isBatchy -> HabitType.SPRINT_FINISHER
            consistency >= 0.7f -> HabitType.STEADY_PACER
            eveningRatio >= 0.3f -> HabitType.REFLECTIVE_PLANNER
            else -> HabitType.STEADY_PACER
        }
    }

    private fun deriveSecondaryArchetype(sessions: List<Session>, primary: HabitType): HabitType? {
        val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
        if (completed.size < 5) return null

        val candidates = HabitType.entries.filter { it != primary }
        val morningRatio = completed.count { hourOf(it) < 12 }.toFloat() / completed.size
        val eveningRatio = completed.count { hourOf(it) >= 18 }.toFloat() / completed.size

        return when {
            morningRatio >= 0.4f && HabitType.EARLY_BIRD in candidates -> HabitType.EARLY_BIRD
            eveningRatio >= 0.35f && HabitType.REFLECTIVE_PLANNER in candidates -> HabitType.REFLECTIVE_PLANNER
            else -> candidates.firstOrNull()
        }
    }

    internal fun deriveSummary(
        peakFocus: Pair<Int, Int>,
        streak: Pair<Int, Int>,
        completions: List<GoalCompletion>,
        archetype: HabitType
    ): String {
        val bestGoal = completions.maxByOrNull { it.completionRate }
        val worstGoal = completions.filter { it.completionRate < 0.5f }.minByOrNull { it.completionRate }

        return when {
            streak.first >= 7 ->
                "You're on a ${streak.first}-day streak! Your consistency during ${formatHour(peakFocus.first)}–${formatHour(peakFocus.second)} is driving strong results."

            worstGoal != null ->
                "\"${worstGoal.title}\" needs attention at ${(worstGoal.completionRate * 100).toInt()}% completion. Try scheduling it during your peak hours (${formatHour(peakFocus.first)}–${formatHour(peakFocus.second)})."

            bestGoal != null && bestGoal.completionRate >= 0.8f ->
                "Strong performance on \"${bestGoal.title}\" at ${(bestGoal.completionRate * 100).toInt()}%. Your ${archetype.title} pattern is working well."

            else ->
                "Focus peaks between ${formatHour(peakFocus.first)} and ${formatHour(peakFocus.second)}. Schedule high-complexity tasks in this window for best results."
        }
    }

    private fun completionRate(sessions: List<Session>): Float {
        if (sessions.isEmpty()) return 0f
        return sessions.count { it.status == SessionStatus.COMPLETED }.toFloat() / sessions.size
    }

    private fun hourOf(session: Session): Int =
        session.startedAt?.atZone(java.time.ZoneId.systemDefault())?.hour
            ?: blockToHour(session.block)

    private fun blockToHour(block: DayBlock): Int = when (block) {
        DayBlock.MORNING -> 8
        DayBlock.DAY -> 13
        DayBlock.EVENING -> 18
        DayBlock.NIGHT -> 22
    }

    private fun formatHour(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
