package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.anchor
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.goalIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SimulationEngineTest {

    private val date = LocalDate.of(2024, 3, 1)

    private fun profile(intentId: String = "i1") = UserBehaviorProfile(
        intentId = intentId,
        fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.1),
        momentum = MomentumSignal(3, 0.8, true),
        learnedEstimate = null,
        consistencyScore = 0.7,
        skipRate = 0.1,
        completionRate = 0.8,
        peakFocusHours = listOf(9, 10),
        velocityTrend = TrendDirection.FLAT,
        durationTrend = TrendDirection.FLAT,
        weeklyMinutesByDay = DayOfWeek.entries.associateWith { 30 }
    )

    @Test
    fun `simulate with no adjustments returns baseline`() {
        val intent = goalIntent(
            id = "i1", domain = Domain.STUDIES, targetCount = 30,
            startDate = date, endDate = date.plusDays(30),
            priority = 1
        ).copy(estimatedMinutesPerUnit = 25)

        val anchors = listOf(
            anchor(id = "a1", block = DayBlock.MORNING, domain = Domain.STUDIES, defaultMinutes = 60)
        )

        val result = SimulationEngine.simulate(
            intents = listOf(intent),
            anchors = anchors,
            currentProfiles = mapOf("i1" to profile()),
            adjustments = SimulationInput(),
            date = date,
            getCompletedUnits = { 0 },
            getDaysWorked = { 0 }
        )

        assertTrue(result.predictedCompletionDates.containsKey("i1"))
        assertTrue(result.feasibilityScore in 0.0..1.0)
    }

    @Test
    fun `deadline extension improves feasibility`() {
        val intent = goalIntent(
            id = "i1", domain = Domain.STUDIES, targetCount = 100,
            startDate = date, endDate = date.plusDays(5),
            priority = 1
        ).copy(estimatedMinutesPerUnit = 30)

        val anchors = listOf(
            anchor(id = "a1", block = DayBlock.MORNING, domain = Domain.STUDIES, defaultMinutes = 60)
        )

        val tight = SimulationEngine.simulate(
            intents = listOf(intent),
            anchors = anchors,
            currentProfiles = mapOf("i1" to profile()),
            adjustments = SimulationInput(),
            date = date,
            getCompletedUnits = { 0 },
            getDaysWorked = { 0 }
        )

        val extended = SimulationEngine.simulate(
            intents = listOf(intent),
            anchors = anchors,
            currentProfiles = mapOf("i1" to profile()),
            adjustments = SimulationInput(
                deadlineAdjustments = mapOf("i1" to date.plusDays(60))
            ),
            date = date,
            getCompletedUnits = { 0 },
            getDaysWorked = { 0 }
        )

        assertTrue(extended.feasibilityScore >= tight.feasibilityScore)
    }

    @Test
    fun `zero intents returns feasibility 1`() {
        val result = SimulationEngine.simulate(
            intents = emptyList(),
            anchors = emptyList(),
            currentProfiles = emptyMap(),
            adjustments = SimulationInput(),
            date = date
        )
        assertEquals(1.0, result.feasibilityScore, 0.001)
        assertTrue(result.overloadChanges.isEmpty())
    }

    @Test
    fun `capacity adjustment scales anchors`() {
        val intent = goalIntent(
            id = "i1", domain = Domain.STUDIES, targetCount = 20,
            startDate = date, endDate = date.plusDays(30),
            priority = 1
        ).copy(estimatedMinutesPerUnit = 25)

        val anchors = listOf(
            anchor(id = "a1", block = DayBlock.MORNING, domain = Domain.STUDIES, defaultMinutes = 60),
            anchor(id = "a2", block = DayBlock.DAY, domain = Domain.STUDIES, defaultMinutes = 60)
        )

        val result = SimulationEngine.simulate(
            intents = listOf(intent),
            anchors = anchors,
            currentProfiles = mapOf("i1" to profile()),
            adjustments = SimulationInput(dailyCapacityMinutes = 30),
            date = date,
            getCompletedUnits = { 0 },
            getDaysWorked = { 0 }
        )

        assertTrue(result.requiredDailyEffort.containsKey("i1"))
    }

    @Test
    fun `overload changes track before and after`() {
        val intent = goalIntent(
            id = "i1", domain = Domain.STUDIES, targetCount = 100,
            startDate = date, endDate = date.plusDays(2),
            priority = 1
        ).copy(estimatedMinutesPerUnit = 30)

        val anchors = listOf(
            anchor(id = "a1", block = DayBlock.MORNING, domain = Domain.STUDIES, defaultMinutes = 30)
        )

        val result = SimulationEngine.simulate(
            intents = listOf(intent),
            anchors = anchors,
            currentProfiles = mapOf("i1" to profile()),
            adjustments = SimulationInput(
                deadlineAdjustments = mapOf("i1" to date.plusDays(365))
            ),
            date = date,
            getCompletedUnits = { 0 },
            getDaysWorked = { 0 }
        )

        val change = result.overloadChanges.firstOrNull { it.intentId == "i1" }
        if (change != null) {
            assertTrue(change.wasBefore || !change.isAfter)
        }
    }
}
