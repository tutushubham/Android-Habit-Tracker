package com.tutushubham.pokidex.core.domain.usecase

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.BehaviorAggregator
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import com.tutushubham.pokidex.core.domain.model.SystemSettings
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Single orchestrator for all behavior computation.
 *
 * Fetches data once, delegates to existing pure analyzers ([BehaviorAggregator],
 * [FatigueAnalyzer], [MomentumAnalyzer], [EstimateLearner]), and derives all
 * aggregate signals (consistency, peak hours, trends, weekly activity) in one pass.
 *
 * Consumers ([TodayPlannerUseCase], [InsightsUseCase], [GoalInsightsUseCase])
 * call [getProfiles] instead of independently computing behavior signals.
 *
 * Includes per-day caching: recomputed only when date or settings change,
 * or explicitly invalidated after session mutations.
 */
class BehaviorProfileUseCase(
    private val sessionRepository: SessionRepository,
    private val intentRepository: IntentRepository,
    private val behaviorRepository: BehaviorRepository
) {
    private var cachedProfiles: Map<String, UserBehaviorProfile>? = null
    private var cacheDate: LocalDate? = null
    private var cacheSettingsHash: Int? = null

    suspend fun getProfiles(
        date: LocalDate,
        settings: SystemSettings = SystemSettings()
    ): Map<String, UserBehaviorProfile> {
        if (cachedProfiles != null && cacheDate == date && cacheSettingsHash == settings.hashCode()) {
            return cachedProfiles!!
        }
        val profiles = computeProfiles(date)
        cachedProfiles = profiles
        cacheDate = date
        cacheSettingsHash = settings.hashCode()
        return profiles
    }

    fun invalidateCache() {
        cachedProfiles = null
    }

    private suspend fun computeProfiles(date: LocalDate): Map<String, UserBehaviorProfile> {
        val cutoff30d = date.minusDays(RECENT_DAYS_WINDOW)
        val cutoff7d = date.minusDays(7)
        val cutoff14d = date.minusDays(14)

        val sessions30d = sessionRepository.getRecentSessions(cutoff30d)
        val farPast = date.minusDays(365)
        val intents = intentRepository.getIntentsForDateRange(farPast, date)

        if (intents.isEmpty()) return emptyMap()

        val sessionsByIntent = sessions30d.groupBy { it.intentId }

        val persistedStats = behaviorRepository.getAllIntentStats().associateBy { it.intentId }
        val domainProfiles = behaviorRepository.getAllDomainProfiles().associateBy { it.domain }

        val behaviorProfiles = BehaviorAggregator.aggregateWithPersisted(
            sessionsByIntent, intents, date, persistedStats, domainProfiles
        )

        val globalPeakHours = derivePeakFocusHours(sessions30d)

        return intents.associate { intent ->
            val intentSessions = sessionsByIntent[intent.id] ?: emptyList()
            val sessions7d = intentSessions.filter { it.date >= cutoff7d }
            val sessionsPrev7d = intentSessions.filter { it.date in cutoff14d..<cutoff7d }
            val behaviorProfile = behaviorProfiles[intent.id]

            val profile = UserBehaviorProfile(
                intentId = intent.id,
                fatigue = behaviorProfile?.fatigue
                    ?: com.tutushubham.pokidex.core.engine.FatigueSignal(
                        com.tutushubham.pokidex.core.engine.FatigueLevel.LOW, 0, 0.0
                    ),
                momentum = behaviorProfile?.momentum
                    ?: com.tutushubham.pokidex.core.engine.MomentumSignal(0, 0.0, false),
                learnedEstimate = behaviorProfile?.learnedEstimate,
                consistencyScore = deriveConsistency(intentSessions, date),
                skipRate = deriveSkipRate(intentSessions),
                completionRate = deriveCompletionRate(intentSessions),
                peakFocusHours = globalPeakHours,
                velocityTrend = deriveVelocityTrend(sessions7d, sessionsPrev7d),
                durationTrend = deriveDurationTrend(sessions7d, sessionsPrev7d),
                weeklyMinutesByDay = deriveWeeklyMinutes(intentSessions, date)
            )

            intent.id to profile
        }
    }

    companion object {
        private const val RECENT_DAYS_WINDOW = 30L

        internal fun derivePeakFocusHours(sessions: List<Session>): List<Int> {
            val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
            if (completed.isEmpty()) return listOf(9, 10)

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

            return listOf(bestStart, (bestStart + 1).coerceAtMost(23))
        }

        internal fun deriveConsistency(sessions: List<Session>, date: LocalDate): Double {
            val completed = sessions.filter { it.status == SessionStatus.COMPLETED }
            if (completed.isEmpty()) return 0.0
            val activeDays = completed.map { it.date }.toSet().size
            val minDate = completed.minOf { it.date }
            val span = (date.toEpochDay() - minDate.toEpochDay() + 1).toInt().coerceAtLeast(1)
            return (activeDays.toDouble() / span).coerceIn(0.0, 1.0)
        }

        internal fun deriveSkipRate(sessions: List<Session>): Double {
            if (sessions.isEmpty()) return 0.0
            val skipped = sessions.count { it.status == SessionStatus.SKIPPED }
            return skipped.toDouble() / sessions.size
        }

        internal fun deriveCompletionRate(sessions: List<Session>): Double {
            if (sessions.isEmpty()) return 0.0
            val completed = sessions.count { it.status == SessionStatus.COMPLETED }
            return completed.toDouble() / sessions.size
        }

        internal fun deriveVelocityTrend(
            sessions7d: List<Session>,
            sessionsPrev7d: List<Session>
        ): TrendDirection {
            val recent = sessions7d.count { it.status == SessionStatus.COMPLETED }
            val prev = sessionsPrev7d.count { it.status == SessionStatus.COMPLETED }
            return when {
                recent > prev + 1 -> TrendDirection.UP
                recent < prev - 1 -> TrendDirection.DOWN
                else -> TrendDirection.FLAT
            }
        }

        internal fun deriveDurationTrend(
            sessions7d: List<Session>,
            sessionsPrev7d: List<Session>
        ): TrendDirection {
            val recentAvg = avgMinutes(sessions7d)
            val prevAvg = avgMinutes(sessionsPrev7d)
            return when {
                recentAvg == 0 || prevAvg == 0 -> TrendDirection.FLAT
                recentAvg < prevAvg - 3 -> TrendDirection.DOWN
                recentAvg > prevAvg + 3 -> TrendDirection.UP
                else -> TrendDirection.FLAT
            }
        }

        internal fun deriveWeeklyMinutes(sessions: List<Session>, date: LocalDate): Map<DayOfWeek, Int> {
            val weekStart = date.minusDays(6)
            val result = mutableMapOf<DayOfWeek, Int>()
            DayOfWeek.entries.forEach { result[it] = 0 }

            sessions
                .filter { it.date in weekStart..date && it.status == SessionStatus.COMPLETED }
                .forEach { s ->
                    val dow = s.date.dayOfWeek
                    result[dow] = (result[dow] ?: 0) + (s.actualMinutes ?: s.plannedMinutes)
                }
            return result
        }

        private fun avgMinutes(sessions: List<Session>): Int {
            val completed = sessions.filter {
                it.status == SessionStatus.COMPLETED && it.actualMinutes != null && it.actualMinutes > 0
            }
            return if (completed.isEmpty()) 0
            else completed.sumOf { it.actualMinutes!! } / completed.size
        }

        private fun blockToHour(block: DayBlock): Int = when (block) {
            DayBlock.MORNING -> 8
            DayBlock.DAY -> 13
            DayBlock.EVENING -> 18
            DayBlock.NIGHT -> 22
        }
    }
}
