package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import kotlin.math.roundToInt

/**
 * Decides which estimate source to use based on data availability and confidence.
 *
 * Decision tree:
 * 1. No static -> domain fallback or null
 * 2. Has fresh with confidence >= 0.3 -> use fresh
 * 3. Has fresh with confidence < 0.3 -> blend domain (confidence-weighted)
 * 4. Has persisted (< 30 days old) -> hybrid via [BehaviorMerger]
 * 5. Cold start with domain profile -> domain fallback
 * 6. Otherwise -> static as-is
 */
object EstimateStrategyResolver {

    private const val HIGH_CONFIDENCE_THRESHOLD = 0.3
    private const val STALE_THRESHOLD_DAYS = 30

    fun resolve(
        staticMinutesPerUnit: Int?,
        freshEstimate: LearnedEstimate?,
        persistedStats: UserIntentStats?,
        domainProfile: DomainBehaviorProfile?,
        daysSinceLastUpdate: Int
    ): LearnedEstimate? {
        if (staticMinutesPerUnit == null || staticMinutesPerUnit <= 0) {
            return domainProfile?.let { domainFallback(it) }
        }

        if (freshEstimate != null && freshEstimate.confidence > 0.0) {
            if (freshEstimate.confidence >= HIGH_CONFIDENCE_THRESHOLD) {
                return freshEstimate
            }
            if (domainProfile != null) {
                return blendWithDomain(staticMinutesPerUnit, freshEstimate.confidence, domainProfile)
            }
            return freshEstimate
        }

        if (persistedStats != null && daysSinceLastUpdate < STALE_THRESHOLD_DAYS && freshEstimate != null) {
            return BehaviorMerger.merge(
                freshEstimate = freshEstimate,
                persistedLearned = persistedStats.learnedMinutesPerUnit,
                persistedConfidence = persistedStats.confidence,
                daysSinceLastUpdate = daysSinceLastUpdate,
                staticMinutesPerUnit = staticMinutesPerUnit
            )
        }

        if (domainProfile != null) {
            return domainFallback(domainProfile)
        }

        return LearnedEstimate(
            effectiveMinutesPerUnit = staticMinutesPerUnit,
            learnedMinutesPerUnit = null,
            confidence = 0.0,
            staticEstimate = staticMinutesPerUnit
        )
    }

    /**
     * Confidence-weighted domain blending:
     * effective = static * confidence + domainDuration * (1 - confidence)
     */
    private fun blendWithDomain(
        staticMinutesPerUnit: Int,
        confidence: Double,
        domainProfile: DomainBehaviorProfile
    ): LearnedEstimate {
        val weight = 1.0 - confidence
        val effective = (staticMinutesPerUnit * confidence + domainProfile.preferredSessionDuration * weight)
            .coerceAtLeast(1.0)
            .roundToInt()
        return LearnedEstimate(
            effectiveMinutesPerUnit = effective,
            learnedMinutesPerUnit = null,
            confidence = confidence,
            staticEstimate = staticMinutesPerUnit
        )
    }

    private fun domainFallback(profile: DomainBehaviorProfile): LearnedEstimate {
        return LearnedEstimate(
            effectiveMinutesPerUnit = profile.preferredSessionDuration.coerceAtLeast(1),
            learnedMinutesPerUnit = null,
            confidence = 0.0,
            staticEstimate = profile.preferredSessionDuration
        )
    }
}
