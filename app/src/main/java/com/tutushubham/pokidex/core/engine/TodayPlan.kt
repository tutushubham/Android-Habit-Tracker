package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.Domain

/** One overloaded intent: required units today exceed daily capacity. severity = needed / capacity. */
data class OverloadDetail(
    val intentId: String,
    val needed: Int,
    val capacity: Int
) {
    val severity: Double get() = if (capacity > 0) needed.toDouble() / capacity else needed.toDouble()
}

/** Per-goal progress snapshot: tracks pace, deficit, and whether the user is behind or critically overloaded. */
data class IntentProgress(
    val intentId: String,
    val title: String,
    val domain: Domain,

    val targetCount: Int,
    val completedUnits: Int,
    val remainingUnits: Int,

    val daysRemaining: Int,

    val requiredUnitsPerDay: Double,
    val currentPace: Double,

    val isBehind: Boolean,
    val isOverloaded: Boolean = false,
    val overloadSeverity: Double? = null
) {
    val progressFraction: Float
        get() = if (targetCount > 0) (completedUnits.toFloat() / targetCount).coerceIn(0f, 1f) else 0f

    val deficit: Double
        get() = (requiredUnitsPerDay - currentPace).coerceAtLeast(0.0)

    val isCritical: Boolean
        get() = isBehind && isOverloaded
}

data class TodayPlan(
    val sessions: List<Session>,
    val hasAnchors: Boolean = false,
    val hasIntents: Boolean = false,
    /** Intent IDs whose required units today exceed total daily block capacity (so we under-allocate). */
    val overloadedIntentIds: List<String> = emptyList(),
    /** Per-intent overload details for severity (needed / capacity). */
    val overloadDetails: List<OverloadDetail> = emptyList(),
    /** Per-goal progress with execution-based pace, sorted by criticality. */
    val progressList: List<IntentProgress> = emptyList()
)
