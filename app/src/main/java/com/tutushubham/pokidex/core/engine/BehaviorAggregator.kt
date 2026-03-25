package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Orchestration-only aggregator: delegates to [FatigueAnalyzer], [MomentumAnalyzer],
 * and [EstimateLearner] to build per-intent behavior profiles.
 *
 * [aggregateWithPersisted] additionally consults [EstimateStrategyResolver] to select
 * the best estimate source (fresh, persisted, domain, or hybrid).
 *
 * Performance guard: processes at most [MAX_SESSIONS_PER_INTENT] sessions per intent.
 */
object BehaviorAggregator {

    private const val MAX_SESSIONS_PER_INTENT = 50

    fun aggregate(
        sessionsByIntent: Map<String, List<Session>>,
        intents: List<GoalIntent>,
        date: LocalDate
    ): Map<String, IntentBehaviorProfile> {
        return intents.associate { intent ->
            val sessions = sessionsByIntent[intent.id]
                ?.take(MAX_SESSIONS_PER_INTENT)
                ?: emptyList()

            val fatigue = FatigueAnalyzer.analyze(sessions)
            val momentum = MomentumAnalyzer.analyze(sessions, date)
            val learnedEstimate = EstimateLearner.computeRecentEstimate(
                intent.estimatedMinutesPerUnit,
                sessions
            )

            intent.id to IntentBehaviorProfile(fatigue, momentum, learnedEstimate)
        }
    }

    fun aggregateWithPersisted(
        sessionsByIntent: Map<String, List<Session>>,
        intents: List<GoalIntent>,
        date: LocalDate,
        persistedStats: Map<String, UserIntentStats>,
        domainProfiles: Map<Domain, DomainBehaviorProfile>
    ): Map<String, IntentBehaviorProfile> {
        val freshProfiles = aggregate(sessionsByIntent, intents, date)

        return freshProfiles.mapValues { (intentId, profile) ->
            val intent = intents.firstOrNull { it.id == intentId } ?: return@mapValues profile
            val persisted = persistedStats[intentId]
            val domainProfile = domainProfiles[intent.domain]

            val daysSinceUpdate = persisted?.lastUpdated?.let {
                ChronoUnit.DAYS.between(it, date).toInt()
            } ?: Int.MAX_VALUE

            val resolvedEstimate = EstimateStrategyResolver.resolve(
                staticMinutesPerUnit = intent.estimatedMinutesPerUnit,
                freshEstimate = profile.learnedEstimate,
                persistedStats = persisted,
                domainProfile = domainProfile,
                daysSinceLastUpdate = daysSinceUpdate
            )

            profile.copy(learnedEstimate = resolvedEstimate)
        }
    }
}
