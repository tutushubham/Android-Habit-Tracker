package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ExplainabilityEngineTest {

    private fun profile(
        fatigue: FatigueLevel = FatigueLevel.LOW,
        skipRate: Double = 0.1,
        skipStreak: Int = 0,
        completionRate: Double = 0.8,
        consistencyScore: Double = 0.7,
        streakDays: Int = 3,
        isConsistent: Boolean = true,
        velocityTrend: TrendDirection = TrendDirection.FLAT
    ) = UserBehaviorProfile(
        intentId = "i1",
        fatigue = FatigueSignal(fatigue, skipStreak, skipRate),
        momentum = MomentumSignal(streakDays, completionRate, isConsistent),
        learnedEstimate = null,
        consistencyScore = consistencyScore,
        skipRate = skipRate,
        completionRate = completionRate,
        peakFocusHours = listOf(9, 10),
        velocityTrend = velocityTrend,
        durationTrend = TrendDirection.FLAT,
        weeklyMinutesByDay = DayOfWeek.entries.associateWith { 0 }
    )

    private fun progress(
        deficit: Double = 0.0,
        isBehind: Boolean = false
    ) = IntentProgress(
        intentId = "i1",
        title = "Test Goal",
        domain = Domain.STUDIES,
        targetCount = 100,
        completedUnits = 50,
        remainingUnits = 50,
        daysRemaining = 20,
        requiredUnitsPerDay = 2.5 + deficit,
        currentPace = 2.5,
        isBehind = isBehind
    )

    @Test
    fun `session priority explanation includes deficit factor when behind`() {
        val exp = ExplainabilityEngine.explainSessionPriority(
            progress(deficit = 0.5, isBehind = true),
            profile()
        )
        assertTrue(exp.factors.any { it.contains("Behind by") })
        assertEquals("High Priority", exp.title)
    }

    @Test
    fun `session priority critical when deficit over 1`() {
        val exp = ExplainabilityEngine.explainSessionPriority(
            progress(deficit = 2.0, isBehind = true).copy(isOverloaded = true),
            profile()
        )
        assertEquals("Critical Priority", exp.title)
    }

    @Test
    fun `fatigue explanation includes skip rate and level`() {
        val exp = ExplainabilityEngine.explainFatigue(
            profile(fatigue = FatigueLevel.HIGH, skipRate = 0.4, skipStreak = 3)
        )
        assertEquals("High Fatigue", exp.title)
        assertTrue(exp.factors.any { it.contains("Skip rate: 40%") })
        assertTrue(exp.factors.any { it.contains("3-day skip streak") })
    }

    @Test
    fun `prediction explanation includes consistency and velocity`() {
        val prediction = PredictionInsight(
            predictedDate = LocalDate.of(2024, 6, 1),
            confidence = 0.85,
            confidenceLabel = "High"
        )
        val exp = ExplainabilityEngine.explainPrediction(
            prediction,
            profile(consistencyScore = 0.8, velocityTrend = TrendDirection.UP)
        )
        assertEquals("High Confidence Prediction", exp.title)
        assertTrue(exp.factors.any { it.contains("Consistency: 80%") })
        assertTrue(exp.factors.any { it.contains("Velocity trend: UP") })
    }

    @Test
    fun `momentum explanation reflects streak`() {
        val exp = ExplainabilityEngine.explainMomentum(
            profile(streakDays = 10, completionRate = 0.9)
        )
        assertEquals("Strong Momentum", exp.title)
        assertTrue(exp.description.contains("10-day streak"))
    }

    @Test
    fun `low fatigue returns positive message`() {
        val exp = ExplainabilityEngine.explainFatigue(profile())
        assertEquals("Low Fatigue", exp.title)
        assertTrue(exp.description.contains("good energy"))
    }

    @Test
    fun `explanation always has non-empty factors`() {
        val cases = listOf(
            ExplainabilityEngine.explainSessionPriority(progress(), profile()),
            ExplainabilityEngine.explainFatigue(profile()),
            ExplainabilityEngine.explainMomentum(profile())
        )
        cases.forEach { exp ->
            assertTrue("Factors should not be empty for ${exp.title}", exp.factors.isNotEmpty())
        }
    }
}
