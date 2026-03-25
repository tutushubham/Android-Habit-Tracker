package com.tutushubham.pokidex.core.domain.usecase

import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.BehaviorAggregator
import com.tutushubham.pokidex.core.engine.DomainProfileCalculator
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.TodayEngine
import com.tutushubham.pokidex.core.engine.TodayPlan
import java.time.LocalDate

// TODO: split into smaller use cases when scaling (e.g. PlanUseCase, PersistUseCase)
open class TodayPlannerUseCase(
    private val intentRepository: IntentRepository,
    private val sessionRepository: SessionRepository,
    private val anchorRepository: AnchorRepository,
    private val focusResolver: FocusResolver,
    private val behaviorRepository: BehaviorRepository? = null,
    private val engine: TodayEngine = TodayEngine()
) {
    companion object {
        private const val RECENT_DAYS_WINDOW = 30L
    }

    open suspend fun planToday(date: LocalDate): TodayPlan {
        val anchors = anchorRepository.getAllAnchors()
        val intents = intentRepository.getIntentsForDateRange(date, date)
        val existingSessions = sessionRepository.getSessionsForDate(date)

        // NOTE: O(N) queries per intent. Safe for small N (<=10).
        // Future optimization: batch query (GROUP BY intentId) or caching layer.
        val completedUnitsMap = intents.associate {
            it.id to sessionRepository.getCompletedUnitsForIntent(it.id)
        }
        val daysWorkedMap = intents.associate {
            it.id to sessionRepository.getDistinctDaysWorkedForIntent(it.id)
        }

        val cutoffDate = date.minusDays(RECENT_DAYS_WINDOW)
        val recentSessions = sessionRepository.getRecentSessions(cutoffDate)
        val sessionsByIntent = recentSessions.groupBy { it.intentId }

        val behaviorMap = if (behaviorRepository != null) {
            val persistedStats = behaviorRepository.getAllIntentStats().associateBy { it.intentId }
            val domainProfiles = behaviorRepository.getAllDomainProfiles().associateBy { it.domain }
            BehaviorAggregator.aggregateWithPersisted(
                sessionsByIntent, intents, date, persistedStats, domainProfiles
            )
        } else {
            BehaviorAggregator.aggregate(sessionsByIntent, intents, date)
        }

        val lastPlannedDates = recentSessions
            .filter { it.status == SessionStatus.PLANNED || it.status == SessionStatus.COMPLETED }
            .groupBy { it.intentId }
            .mapValues { (_, sessions) -> sessions.maxOf { it.date } }

        val domains = anchors.map { it.domain }.distinct()
        val focusMap = buildMap {
            for (domain in domains) {
                focusResolver.resolve(domain, date)?.let { put(domain, it) }
            }
        }

        val plan = engine.generate(
            date = date,
            intents = intents,
            anchors = anchors,
            existingSessions = existingSessions,
            resolveFocus = { focusMap[it] },
            getCompletedUnits = { completedUnitsMap[it] ?: 0 },
            getDaysWorked = { daysWorkedMap[it] ?: 0 },
            behaviorMap = behaviorMap,
            lastPlannedDates = lastPlannedDates
        )

        if (behaviorRepository != null) {
            persistBehaviorData(behaviorMap, recentSessions, date)
        }

        return plan
    }

    private suspend fun persistBehaviorData(
        behaviorMap: Map<String, com.tutushubham.pokidex.core.engine.IntentBehaviorProfile>,
        recentSessions: List<com.tutushubham.pokidex.core.domain.entity.Session>,
        date: LocalDate
    ) {
        val repo = behaviorRepository ?: return

        behaviorMap.forEach { (intentId, profile) ->
            profile.learnedEstimate?.let { estimate ->
                repo.saveIntentStats(
                    UserIntentStats(
                        intentId = intentId,
                        learnedMinutesPerUnit = estimate.learnedMinutesPerUnit,
                        confidence = estimate.confidence,
                        lastUpdated = date
                    )
                )
            }
        }

        val updatedProfiles = DomainProfileCalculator.compute(
            recentSessions.groupBy { it.domain }, date
        )
        updatedProfiles.forEach { (_, profile) ->
            repo.saveDomainProfile(profile)
        }
    }
}
