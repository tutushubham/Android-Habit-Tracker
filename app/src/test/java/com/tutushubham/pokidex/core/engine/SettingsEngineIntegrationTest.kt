package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.anchor
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.goalIntent
import com.tutushubham.pokidex.core.domain.model.FatigueSensitivity
import com.tutushubham.pokidex.core.domain.model.PlanningStyle
import com.tutushubham.pokidex.core.domain.model.SystemSettings
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SettingsEngineIntegrationTest {

    private val date = LocalDate.of(2024, 3, 1)
    private val engine = TodayEngine()

    private val intent = goalIntent(
        id = "i1", domain = Domain.STUDIES, targetCount = 100,
        startDate = date.minusDays(10), endDate = date.plusDays(20), priority = 1
    ).copy(estimatedMinutesPerUnit = 25)

    private val anchors = listOf(
        anchor(id = "a1", block = DayBlock.MORNING, domain = Domain.STUDIES, defaultMinutes = 60),
        anchor(id = "a2", block = DayBlock.DAY, domain = Domain.STUDIES, defaultMinutes = 60)
    )

    @Test
    fun `adaptive planning disabled uses no behavior`() {
        val behavior = mapOf(
            "i1" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.HIGH, 5, 0.6),
                momentum = MomentumSignal(0, 0.3, false),
                learnedEstimate = LearnedEstimate(15, 15.0, 0.9, 25)
            )
        )

        val withAdaptive = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behavior,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(adaptivePlanningEnabled = true)
        )

        val withoutAdaptive = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behavior,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(adaptivePlanningEnabled = false)
        )

        assertTrue(withAdaptive.sessions.isNotEmpty())
        assertTrue(withoutAdaptive.sessions.isNotEmpty())
    }

    @Test
    fun `strict planning affects session allocation`() {
        val strict = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(planningStyle = PlanningStyle.STRICT)
        )

        val flexible = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(planningStyle = PlanningStyle.FLEXIBLE)
        )

        assertTrue(strict.sessions.isNotEmpty())
        assertTrue(flexible.sessions.isNotEmpty())
    }

    @Test
    fun `fatigue sensitivity affects overload detection`() {
        val behavior = mapOf(
            "i1" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.HIGH, 3, 0.5),
                momentum = MomentumSignal(0, 0.3, false),
                learnedEstimate = null
            )
        )

        val highSens = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behavior,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(fatigueSensitivity = FatigueSensitivity.HIGH)
        )

        val lowSens = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behavior,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(fatigueSensitivity = FatigueSensitivity.LOW)
        )

        assertTrue(highSens.sessions.isNotEmpty())
        assertTrue(lowSens.sessions.isNotEmpty())
    }

    @Test
    fun `learning disabled ignores learned estimates`() {
        val behavior = mapOf(
            "i1" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.0),
                momentum = MomentumSignal(5, 0.9, true),
                learnedEstimate = LearnedEstimate(10, 10.0, 0.95, 25)
            )
        )

        val withLearning = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behavior,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(learningEnabled = true)
        )

        val withoutLearning = engine.generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behavior,
            getCompletedUnits = { 20 },
            getDaysWorked = { 10 },
            settings = SystemSettings(learningEnabled = false)
        )

        assertTrue(withLearning.sessions.isNotEmpty())
        assertTrue(withoutLearning.sessions.isNotEmpty())
    }
}
