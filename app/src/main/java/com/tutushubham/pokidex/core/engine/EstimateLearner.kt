package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import kotlin.math.min
import kotlin.math.roundToInt

data class LearnedEstimate(
    val effectiveMinutesPerUnit: Int,
    val learnedMinutesPerUnit: Double?,
    val confidence: Double,
    val staticEstimate: Int
)

/**
 * Stateless computation helper for adaptive estimate learning.
 *
 * Provides two estimation strategies:
 * - [computeEstimate]: lifetime aggregate (totalMinutes / units), used for backward compat.
 * - [computeRecentEstimate]: recency-weighted (harmonic 1/(1+i)), used by behavior system.
 *
 * Both blend static with learned via confidence weighting, clamp to [0.5x, 2.5x], and
 * ramp slowly for cold-start (< 5 units).
 */
object EstimateLearner {

    private const val CONFIDENCE_THRESHOLD = 20

    fun computeEstimate(
        staticMinutesPerUnit: Int,
        totalActualMinutes: Int,
        completedUnits: Int
    ): LearnedEstimate {
        if (completedUnits <= 0 || totalActualMinutes <= 0) {
            return LearnedEstimate(
                effectiveMinutesPerUnit = staticMinutesPerUnit.coerceAtLeast(1),
                learnedMinutesPerUnit = null,
                confidence = 0.0,
                staticEstimate = staticMinutesPerUnit
            )
        }

        val rawLearned = totalActualMinutes.toDouble() / completedUnits
        val learned = rawLearned.coerceIn(staticMinutesPerUnit * 0.5, staticMinutesPerUnit * 2.5)

        val confidence = when {
            completedUnits < 5 -> 0.1 * completedUnits / 5.0
            else -> min(1.0, completedUnits.toDouble() / CONFIDENCE_THRESHOLD)
        }

        val blended = staticMinutesPerUnit * (1.0 - confidence) + learned * confidence
        val effective = blended.coerceAtLeast(1.0).roundToInt()

        return LearnedEstimate(
            effectiveMinutesPerUnit = effective,
            learnedMinutesPerUnit = learned,
            confidence = confidence,
            staticEstimate = staticMinutesPerUnit
        )
    }

    /**
     * Recency-weighted estimate using harmonic weighting: weight = 1/(1+i).
     *
     * Returns null if static is invalid (null or <= 0).
     * Returns static fallback with confidence 0 if no completed sessions exist.
     */
    fun computeRecentEstimate(
        staticMinutesPerUnit: Int?,
        recentSessions: List<Session>
    ): LearnedEstimate? {
        if (staticMinutesPerUnit == null || staticMinutesPerUnit <= 0) return null

        val completed = recentSessions.filter {
            it.status == SessionStatus.COMPLETED && it.actualMinutes != null && it.actualMinutes > 0
        }

        if (completed.isEmpty()) {
            return LearnedEstimate(
                effectiveMinutesPerUnit = staticMinutesPerUnit,
                learnedMinutesPerUnit = null,
                confidence = 0.0,
                staticEstimate = staticMinutesPerUnit
            )
        }

        val sorted = completed.sortedByDescending { it.date }
        var weightedSum = 0.0
        var totalWeight = 0.0

        sorted.forEachIndexed { i, session ->
            val weight = 1.0 / (1 + i)
            weightedSum += session.actualMinutes!!.toDouble() * weight
            totalWeight += weight
        }

        val rawLearned = weightedSum / totalWeight
        val learned = rawLearned.coerceIn(staticMinutesPerUnit * 0.5, staticMinutesPerUnit * 2.5)

        val confidence = when {
            completed.size < 5 -> 0.1 * completed.size / 5.0
            else -> min(1.0, completed.size.toDouble() / CONFIDENCE_THRESHOLD)
        }

        val blended = staticMinutesPerUnit * (1.0 - confidence) + learned * confidence
        val effective = blended.coerceAtLeast(1.0).roundToInt()

        return LearnedEstimate(
            effectiveMinutesPerUnit = effective,
            learnedMinutesPerUnit = learned,
            confidence = confidence,
            staticEstimate = staticMinutesPerUnit
        )
    }
}
