package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.SystemSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Pure simulation engine for "what-if" analysis.
 *
 * Reuses [TodayEngine] by running [TodayEngine.generate] with modified inputs
 * and comparing the original vs simulated plans. No repository access, deterministic.
 */
object SimulationEngine {

    private val engine = TodayEngine()

    fun simulate(
        intents: List<GoalIntent>,
        anchors: List<Anchor>,
        currentProfiles: Map<String, UserBehaviorProfile>,
        adjustments: SimulationInput,
        settings: SystemSettings = SystemSettings(),
        date: LocalDate,
        getCompletedUnits: (String) -> Int = { 0 },
        getDaysWorked: (String) -> Int = { 0 }
    ): SimulationResult {
        val behaviorMap = currentProfiles.mapValues { it.value.toBehaviorProfile() }

        val originalPlan = engine.generate(
            date = date,
            intents = intents,
            anchors = anchors,
            getCompletedUnits = getCompletedUnits,
            getDaysWorked = getDaysWorked,
            behaviorMap = behaviorMap,
            settings = settings
        )

        val adjustedIntents = intents.map { intent ->
            var modified = intent
            adjustments.deadlineAdjustments[intent.id]?.let { newDate ->
                modified = modified.copy(endDate = newDate)
            }
            adjustments.sessionDurationOverrides[intent.id]?.let { newMinutes ->
                modified = modified.copy(estimatedMinutesPerUnit = newMinutes)
            }
            modified
        }

        val adjustedAnchors = if (adjustments.dailyCapacityMinutes != null) {
            val scale = adjustments.dailyCapacityMinutes.toDouble() /
                anchors.sumOf { it.defaultMinutes }.coerceAtLeast(1)
            anchors.map { it.copy(defaultMinutes = (it.defaultMinutes * scale).toInt().coerceAtLeast(1)) }
        } else anchors

        val simPlan = engine.generate(
            date = date,
            intents = adjustedIntents,
            anchors = adjustedAnchors,
            getCompletedUnits = getCompletedUnits,
            getDaysWorked = getDaysWorked,
            behaviorMap = behaviorMap,
            settings = settings
        )

        val predictedDates = adjustedIntents.associate { intent ->
            val target = intent.targetCount ?: 0
            val completed = getCompletedUnits(intent.id)
            val remaining = (target - completed).coerceAtLeast(0)
            val profile = currentProfiles[intent.id]
            val minutesPerUnit = profile?.learnedEstimate?.effectiveMinutesPerUnit
                ?: intent.estimatedMinutesPerUnit ?: 30

            val dailyMinutes = adjustedAnchors
                .filter { it.domain == intent.domain }
                .sumOf { it.defaultMinutes }
            val unitsPerDay = if (minutesPerUnit > 0) dailyMinutes.toDouble() / minutesPerUnit else 1.0
            val daysNeeded = if (unitsPerDay > 0) ceil(remaining / unitsPerDay).toLong() else 365L

            intent.id to date.plusDays(daysNeeded)
        }

        val originalOverloads = originalPlan.overloadDetails.map { it.intentId }.toSet()
        val simOverloads = simPlan.overloadDetails.associateBy { it.intentId }
        val allIds = (originalOverloads + simOverloads.keys).toSet()

        val overloadChanges = allIds.map { id ->
            OverloadChange(
                intentId = id,
                wasBefore = id in originalOverloads,
                isAfter = id in simOverloads,
                detail = simOverloads[id]
            )
        }

        val requiredEffort = adjustedIntents.associate { intent ->
            val target = intent.targetCount ?: 0
            val completed = getCompletedUnits(intent.id)
            val remaining = (target - completed).coerceAtLeast(0)
            val days = ChronoUnit.DAYS.between(date, intent.endDate).toInt().coerceAtLeast(1)
            val minutesPerUnit = currentProfiles[intent.id]?.learnedEstimate?.effectiveMinutesPerUnit
                ?: intent.estimatedMinutesPerUnit ?: 30
            intent.id to (remaining.toDouble() / days) * minutesPerUnit
        }

        val feasibility = computeFeasibility(adjustedIntents, adjustedAnchors, date, getCompletedUnits, currentProfiles)

        return SimulationResult(
            predictedCompletionDates = predictedDates,
            overloadChanges = overloadChanges,
            requiredDailyEffort = requiredEffort,
            feasibilityScore = feasibility
        )
    }

    private fun computeFeasibility(
        intents: List<GoalIntent>,
        anchors: List<Anchor>,
        date: LocalDate,
        getCompletedUnits: (String) -> Int,
        profiles: Map<String, UserBehaviorProfile>
    ): Double {
        if (intents.isEmpty()) return 1.0

        val scores = intents.mapNotNull { intent ->
            val target = intent.targetCount ?: return@mapNotNull null
            val completed = getCompletedUnits(intent.id)
            val remaining = (target - completed).coerceAtLeast(0)
            if (remaining == 0) return@mapNotNull 1.0

            val days = ChronoUnit.DAYS.between(date, intent.endDate).toInt().coerceAtLeast(1)
            val minutesPerUnit = profiles[intent.id]?.learnedEstimate?.effectiveMinutesPerUnit
                ?: intent.estimatedMinutesPerUnit ?: 30
            val dailyMinutesNeeded = (remaining.toDouble() / days) * minutesPerUnit

            val dailyCapacity = anchors
                .filter { it.domain == intent.domain }
                .sumOf { it.defaultMinutes }

            if (dailyCapacity == 0) 0.0
            else (dailyCapacity.toDouble() / dailyMinutesNeeded).coerceAtMost(1.0)
        }

        return if (scores.isEmpty()) 1.0 else scores.average()
    }
}
