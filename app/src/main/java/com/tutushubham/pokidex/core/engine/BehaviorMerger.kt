package com.tutushubham.pokidex.core.engine

import kotlin.math.roundToInt

/**
 * Pure merger: blends a persisted learned estimate with a fresh learned estimate.
 *
 * Persisted data decays linearly over 30 days (weight = 1 - days/30).
 * No strategy decisions -- only called when [EstimateStrategyResolver] selects the hybrid path.
 */
object BehaviorMerger {

    private const val DECAY_WINDOW_DAYS = 30.0

    fun merge(
        freshEstimate: LearnedEstimate,
        persistedLearned: Double?,
        persistedConfidence: Double,
        daysSinceLastUpdate: Int,
        staticMinutesPerUnit: Int
    ): LearnedEstimate {
        if (persistedLearned == null) return freshEstimate

        val persistedWeight = (1.0 - daysSinceLastUpdate / DECAY_WINDOW_DAYS).coerceAtLeast(0.0)
        if (persistedWeight <= 0.0) return freshEstimate

        val freshLearned = freshEstimate.learnedMinutesPerUnit ?: return freshEstimate
        val freshConf = freshEstimate.confidence

        val blendedLearned = persistedLearned * persistedWeight * (1.0 - freshConf) +
            freshLearned * freshConf
        val clamped = blendedLearned.coerceIn(staticMinutesPerUnit * 0.5, staticMinutesPerUnit * 2.5)

        val blendedConfidence = (persistedConfidence * persistedWeight + freshConf) / (persistedWeight + 1.0)

        val effective = (staticMinutesPerUnit * (1.0 - blendedConfidence) + clamped * blendedConfidence)
            .coerceAtLeast(1.0)
            .roundToInt()

        return LearnedEstimate(
            effectiveMinutesPerUnit = effective,
            learnedMinutesPerUnit = clamped,
            confidence = blendedConfidence,
            staticEstimate = staticMinutesPerUnit
        )
    }
}
