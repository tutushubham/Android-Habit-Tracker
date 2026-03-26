package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FatigueSensitivity
import com.tutushubham.pokidex.core.domain.model.PlanningStyle
import com.tutushubham.pokidex.core.domain.model.SystemSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.min

/**
 * Pure planning engine with zero repository dependencies.
 *
 * All data is passed in via [generate] parameters. This enables trivial unit testing
 * and keeps the engine free of Android framework dependencies.
 */
open class TodayEngine {

    private val maxUrgencyCap = 100.0

    open fun generate(
        date: LocalDate,
        intents: List<GoalIntent>,
        anchors: List<Anchor>,
        existingSessions: List<Session> = emptyList(),
        resolveFocus: (Domain) -> Focus? = { null },
        getCompletedUnits: (String) -> Int = { 0 },
        getDaysWorked: (String) -> Int = { 0 },
        behaviorMap: Map<String, IntentBehaviorProfile> = emptyMap(),
        lastPlannedDates: Map<String, LocalDate> = emptyMap(),
        settings: SystemSettings = SystemSettings(),
        profileMap: Map<String, UserBehaviorProfile> = emptyMap()
    ): TodayPlan {
        val progressList = computeProgressList(intents, date, getCompletedUnits, getDaysWorked)
        val progressMap = progressList.associateBy { it.intentId }

        val plannedSessions = mutableListOf<Session>()
        val allocatedUnitsToday = mutableMapOf<String, Int>()

        for (anchor in anchors) {
            val alreadyPlanned = existingSessions.any {
                it.block == anchor.block && it.domain == anchor.domain
            }
            if (alreadyPlanned) continue

            val focus = resolveFocus(anchor.domain)
            val candidateIntents = filterIntentsByFocus(
                intents.filter { it.domain == anchor.domain }, focus
            )

            val effectiveBehaviorMap = if (settings.adaptivePlanningEnabled) behaviorMap else emptyMap()

            val intent = selectIntentByUrgency(
                candidateIntents, date, getCompletedUnits, allocatedUnitsToday,
                progressMap, effectiveBehaviorMap, lastPlannedDates, settings,
                profileMap, anchor
            ) ?: continue

            val effectivePerUnit = if (settings.learningEnabled) resolveEffectivePerUnit(intent, behaviorMap)
                else intent.estimatedMinutesPerUnit
            val allocatedSoFar = allocatedUnitsToday[intent.id] ?: 0
            val minutes = computeCapacityAwareMinutes(
                blockMinutes = anchor.defaultMinutes,
                intent = intent,
                date = date,
                getCompletedUnits = getCompletedUnits,
                allocatedUnitsToday = allocatedSoFar,
                effectivePerUnit = effectivePerUnit
            )

            if (minutes > 0) {
                plannedSessions.add(
                    Session.planned(
                        intent = intent, date = date,
                        block = anchor.block, minutes = minutes
                    )
                )
                val perUnit = effectivePerUnit ?: intent.estimatedMinutesPerUnit
                if (perUnit != null && perUnit > 0) {
                    allocatedUnitsToday[intent.id] = allocatedSoFar + (minutes / perUnit)
                }
            }
        }

        val focusByDomain = anchors.map { it.domain }.distinct().associateWith { domain ->
            resolveFocus(domain)
        }
        val overloadDetails = computeOverloadDetails(
            intents = intents, anchors = anchors, date = date,
            getCompletedUnits = getCompletedUnits,
            focusForDomain = { focusByDomain[it] },
            behaviorMap = behaviorMap
        )

        val overloadMap = overloadDetails.associateBy { it.intentId }
        val enrichedProgress = progressList
            .map { p ->
                val overload = overloadMap[p.intentId]
                p.copy(
                    isOverloaded = overload != null,
                    overloadSeverity = overload?.severity
                )
            }
            .sortedWith(
                compareByDescending<IntentProgress> { it.isCritical }
                    .thenByDescending { it.isBehind }
                    .thenByDescending { it.requiredUnitsPerDay }
            )

        return TodayPlan(
            sessions = plannedSessions,
            hasAnchors = anchors.isNotEmpty(),
            hasIntents = intents.isNotEmpty(),
            overloadedIntentIds = overloadDetails.map { it.intentId },
            overloadDetails = overloadDetails,
            progressList = enrichedProgress
        )
    }

    private fun filterIntentsByFocus(
        intents: List<GoalIntent>, focus: Focus?
    ): List<GoalIntent> {
        if (focus == null) return intents
        return intents.filter { intent ->
            when {
                intent.focusId != null -> intent.focusId == focus.id
                else -> intent.title.contains(focus.name, ignoreCase = true)
            }
        }
    }

    private fun unitsNeededToday(
        intent: GoalIntent, date: LocalDate, getCompletedUnits: (String) -> Int
    ): Int? {
        val target = intent.targetCount ?: return null
        if (target <= 0) return null
        val completed = getCompletedUnits(intent.id)
        val remaining = (target - completed).coerceAtLeast(0)
        if (remaining <= 0) return 0
        val days = ChronoUnit.DAYS.between(date, intent.endDate).toInt()
        return when {
            days <= 0 -> remaining
            else -> ceil(remaining.toDouble() / days).toInt()
        }
    }

    /**
     * Urgency formula: baseUrgency x progressBoost x momentumBoost x fatigueDampening x starvationBoost.
     *
     * Fatigue cannot reduce urgency below 70% of baseUrgency.
     * Starvation boost (1.1x) applied when intent wasn't planned in >= 3 days.
     */
    private fun effectiveUrgency(
        intent: GoalIntent,
        date: LocalDate,
        getCompletedUnits: (String) -> Int,
        progressMap: Map<String, IntentProgress>,
        behaviorMap: Map<String, IntentBehaviorProfile>,
        lastPlannedDates: Map<String, LocalDate>,
        settings: SystemSettings = SystemSettings(),
        profileMap: Map<String, UserBehaviorProfile> = emptyMap(),
        anchor: Anchor? = null
    ): Double {
        val target = intent.targetCount
        if (target == null || target <= 0) return 1.0 / intent.priority.coerceAtLeast(1)
        val completed = getCompletedUnits(intent.id)
        val remaining = (target - completed).coerceAtLeast(0)
        if (remaining <= 0) return 0.0
        val days = ChronoUnit.DAYS.between(date, intent.endDate).toInt()
        val baseUrgency = when {
            days <= 0 -> maxUrgencyCap
            else -> (remaining.toDouble() / days).coerceAtMost(maxUrgencyCap)
        }

        val progress = progressMap[intent.id]
        val styleMultiplier = when (settings.planningStyle) {
            PlanningStyle.STRICT -> 1.3
            PlanningStyle.BALANCED -> 1.0
            PlanningStyle.FLEXIBLE -> 0.8
        }

        val progressBoost = when {
            progress?.isCritical == true -> 1.5 * styleMultiplier
            progress?.isBehind == true -> 1.2 * styleMultiplier
            else -> 1.0
        }

        val behavior = behaviorMap[intent.id]
        val profile = profileMap[intent.id]

        val momentumBoost = when {
            behavior?.momentum?.isConsistent == true -> 1.1
            else -> 1.0
        }

        val baseFatigueDampening = when (behavior?.fatigue?.level) {
            FatigueLevel.HIGH -> 0.85
            FatigueLevel.MEDIUM -> 0.90
            else -> 1.0
        }
        val fatigueDampening = when (settings.fatigueSensitivity) {
            FatigueSensitivity.HIGH -> baseFatigueDampening * 0.9
            FatigueSensitivity.MEDIUM -> baseFatigueDampening
            FatigueSensitivity.LOW -> 1.0 - (1.0 - baseFatigueDampening) * 0.5
        }

        val lastPlanned = lastPlannedDates[intent.id]
        val starvationBoost = when {
            lastPlanned == null -> 1.1
            ChronoUnit.DAYS.between(lastPlanned, date) >= 3 -> 1.1
            else -> 1.0
        }

        val consistencyBoost = if ((profile?.consistencyScore ?: 0.0) > 0.7) 1.05 else 1.0
        val peakAlignment = if (anchor != null && isPeakHour(anchor, profile?.peakFocusHours)) 1.1 else 1.0

        val preFatigueUrgency = baseUrgency * progressBoost * momentumBoost *
            starvationBoost * consistencyBoost * peakAlignment
        val finalUrgency = preFatigueUrgency * fatigueDampening

        return finalUrgency.coerceAtLeast(baseUrgency * 0.7).coerceAtMost(maxUrgencyCap)
    }

    private fun isPeakHour(anchor: Anchor, peakHours: List<Int>?): Boolean {
        if (peakHours.isNullOrEmpty()) return false
        val blockHour = when (anchor.block) {
            com.tutushubham.pokidex.core.domain.model.DayBlock.MORNING -> 8
            com.tutushubham.pokidex.core.domain.model.DayBlock.DAY -> 13
            com.tutushubham.pokidex.core.domain.model.DayBlock.EVENING -> 18
            com.tutushubham.pokidex.core.domain.model.DayBlock.NIGHT -> 22
        }
        return blockHour in peakHours || (blockHour + 1) in peakHours
    }

    private fun selectIntentByUrgency(
        intents: List<GoalIntent>,
        date: LocalDate,
        getCompletedUnits: (String) -> Int,
        allocatedUnitsToday: Map<String, Int>,
        progressMap: Map<String, IntentProgress>,
        behaviorMap: Map<String, IntentBehaviorProfile>,
        lastPlannedDates: Map<String, LocalDate>,
        settings: SystemSettings = SystemSettings(),
        profileMap: Map<String, UserBehaviorProfile> = emptyMap(),
        anchor: Anchor? = null
    ): GoalIntent? {
        if (intents.isEmpty()) return null
        val stillNeedingAllocation = intents.filter { intent ->
            val needed = unitsNeededToday(intent, date, getCompletedUnits)
            if (needed == null) true else (allocatedUnitsToday[intent.id] ?: 0) < needed
        }
        if (stillNeedingAllocation.isEmpty()) return null
        return stillNeedingAllocation.maxWithOrNull(
            compareBy<GoalIntent> {
                effectiveUrgency(
                    it, date, getCompletedUnits, progressMap, behaviorMap,
                    lastPlannedDates, settings, profileMap, anchor
                )
            }.thenByDescending { it.priority }
        )
    }

    /** Resolves effective minutes-per-unit from behavior map, falling back to static estimate. */
    private fun resolveEffectivePerUnit(
        intent: GoalIntent,
        behaviorMap: Map<String, IntentBehaviorProfile>
    ): Int? {
        val learned = behaviorMap[intent.id]?.learnedEstimate?.effectiveMinutesPerUnit
        return learned ?: intent.estimatedMinutesPerUnit
    }

    private fun computeCapacityAwareMinutes(
        blockMinutes: Int,
        intent: GoalIntent,
        date: LocalDate,
        getCompletedUnits: (String) -> Int,
        allocatedUnitsToday: Int,
        effectivePerUnit: Int? = null
    ): Int {
        val perUnit = effectivePerUnit ?: intent.estimatedMinutesPerUnit
        if (perUnit == null || perUnit <= 0) return blockMinutes

        val needed = unitsNeededToday(intent, date, getCompletedUnits) ?: return blockMinutes
        val unitsStillNeeded = (needed - allocatedUnitsToday).coerceAtLeast(0)
        if (unitsStillNeeded <= 0) return 0

        val targetCount = intent.targetCount ?: return blockMinutes
        val completed = getCompletedUnits(intent.id)
        val remaining = (targetCount - completed).coerceAtLeast(0)
        if (remaining <= 0) return blockMinutes

        val maxUnitsInBlock = blockMinutes / perUnit
        val unitsAssigned = min(unitsStillNeeded, min(maxUnitsInBlock, remaining)).coerceAtLeast(0)
        return (unitsAssigned * perUnit).coerceAtMost(blockMinutes)
    }

    private fun computeProgressList(
        intents: List<GoalIntent>,
        date: LocalDate,
        getCompletedUnits: (String) -> Int,
        getDaysWorked: (String) -> Int
    ): List<IntentProgress> {
        return intents
            .filter { it.targetCount != null && it.targetCount > 0 }
            .map { intent ->
                val target = intent.targetCount!!
                val completed = getCompletedUnits(intent.id)
                val remaining = (target - completed).coerceAtLeast(0)
                val daysRemaining = ChronoUnit.DAYS.between(date, intent.endDate).toInt().coerceAtLeast(0)

                val requiredPerDay = when {
                    remaining <= 0 -> 0.0
                    daysRemaining > 0 -> remaining.toDouble() / daysRemaining
                    else -> remaining.toDouble()
                }

                val daysWorkedRaw = getDaysWorked(intent.id)
                val currentPace = when {
                    daysWorkedRaw > 0 -> completed.toDouble() / daysWorkedRaw
                    else -> 0.0
                }

                IntentProgress(
                    intentId = intent.id,
                    title = intent.title,
                    domain = intent.domain,
                    targetCount = target,
                    completedUnits = completed,
                    remainingUnits = remaining,
                    daysRemaining = daysRemaining,
                    requiredUnitsPerDay = requiredPerDay,
                    currentPace = currentPace,
                    isBehind = remaining > 0 && currentPace < requiredPerDay
                )
            }
    }

    private fun computeOverloadDetails(
        intents: List<GoalIntent>,
        anchors: List<Anchor>,
        date: LocalDate,
        getCompletedUnits: (String) -> Int,
        focusForDomain: ((Domain) -> Focus?)? = null,
        behaviorMap: Map<String, IntentBehaviorProfile> = emptyMap()
    ): List<OverloadDetail> {
        return intents
            .filter { it.targetCount != null && it.estimatedMinutesPerUnit != null && it.estimatedMinutesPerUnit > 0 }
            .mapNotNull { intent ->
                val needed = unitsNeededToday(intent, date, getCompletedUnits) ?: 0
                val perUnit = resolveEffectivePerUnit(intent, behaviorMap)
                    ?: intent.estimatedMinutesPerUnit!!
                val anchorsUsableForIntent = if (focusForDomain != null) {
                    anchors.filter { anchor ->
                        anchor.domain == intent.domain &&
                            filterIntentsByFocus(listOf(intent), focusForDomain(anchor.domain)).isNotEmpty()
                    }
                } else {
                    anchors.filter { it.domain == intent.domain }
                }
                val capacity = anchorsUsableForIntent.sumOf { it.defaultMinutes / perUnit }
                if (needed > capacity) OverloadDetail(intentId = intent.id, needed = needed, capacity = capacity)
                else null
            }
    }
}
