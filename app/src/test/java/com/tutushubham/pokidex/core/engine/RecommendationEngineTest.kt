package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FatigueSensitivity
import com.tutushubham.pokidex.core.domain.model.PlanningStyle
import com.tutushubham.pokidex.core.domain.model.SystemSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RecommendationEngineTest {

    private fun profile(
        fatigue: FatigueLevel = FatigueLevel.LOW,
        skipRate: Double = 0.0,
        skipStreak: Int = 0,
        completionRate: Double = 0.8,
        consistencyScore: Double = 0.7,
        velocityTrend: TrendDirection = TrendDirection.FLAT,
        streakDays: Int = 3,
        isConsistent: Boolean = true
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
        isBehind: Boolean = false,
        isCritical: Boolean = false,
        daysRemaining: Int = 20
    ) = IntentProgress(
        intentId = "i1",
        title = "Test",
        domain = Domain.STUDIES,
        targetCount = 100,
        completedUnits = 50,
        remainingUnits = 50,
        daysRemaining = daysRemaining,
        requiredUnitsPerDay = 2.5 + deficit,
        currentPace = 2.5,
        isBehind = isBehind,
        isOverloaded = isCritical
    )

    @Test
    fun `high fatigue generates recovery recommendation`() {
        val recs = RecommendationEngine.generate(
            profile = profile(fatigue = FatigueLevel.HIGH, skipRate = 0.5, skipStreak = 3),
            progress = null
        )
        assertTrue(recs.any { it.type == RecommendationType.RECOVERY })
    }

    @Test
    fun `behind schedule generates add session recommendation`() {
        val recs = RecommendationEngine.generate(
            profile = profile(),
            progress = progress(deficit = 1.5, isBehind = true)
        )
        assertTrue(recs.any { it.type == RecommendationType.SCHEDULE })
    }

    @Test
    fun `strong momentum generates maintain pace recommendation`() {
        val recs = RecommendationEngine.generate(
            profile = profile(velocityTrend = TrendDirection.UP, isConsistent = true, streakDays = 5),
            progress = progress()
        )
        assertTrue(recs.any { it.type == RecommendationType.STRETCH })
    }

    @Test
    fun `strict planning increases deficit recommendation score`() {
        val strictRecs = RecommendationEngine.generate(
            profile = profile(),
            progress = progress(deficit = 2.0, isBehind = true),
            settings = SystemSettings(planningStyle = PlanningStyle.STRICT)
        )
        val flexRecs = RecommendationEngine.generate(
            profile = profile(),
            progress = progress(deficit = 2.0, isBehind = true),
            settings = SystemSettings(planningStyle = PlanningStyle.FLEXIBLE)
        )

        val strictSchedule = strictRecs.firstOrNull { it.type == RecommendationType.SCHEDULE }
        val flexSchedule = flexRecs.firstOrNull { it.type == RecommendationType.SCHEDULE }

        assertTrue(strictSchedule != null && flexSchedule != null)
        assertTrue(strictSchedule!!.score > flexSchedule!!.score)
    }

    @Test
    fun `high fatigue sensitivity increases recovery recommendation score`() {
        val highSens = RecommendationEngine.generate(
            profile = profile(fatigue = FatigueLevel.HIGH, skipRate = 0.4),
            progress = null,
            settings = SystemSettings(fatigueSensitivity = FatigueSensitivity.HIGH)
        )
        val lowSens = RecommendationEngine.generate(
            profile = profile(fatigue = FatigueLevel.HIGH, skipRate = 0.4),
            progress = null,
            settings = SystemSettings(fatigueSensitivity = FatigueSensitivity.LOW)
        )

        val highScore = highSens.first { it.type == RecommendationType.RECOVERY }.score
        val lowScore = lowSens.first { it.type == RecommendationType.RECOVERY }.score
        assertTrue(highScore > lowScore)
    }

    @Test
    fun `recommendations are sorted by score descending`() {
        val recs = RecommendationEngine.generate(
            profile = profile(fatigue = FatigueLevel.HIGH, skipRate = 0.5, skipStreak = 3, consistencyScore = 0.2),
            progress = progress(deficit = 2.0, isBehind = true, daysRemaining = 15)
        )
        for (i in 0 until recs.size - 1) {
            assertTrue(recs[i].score >= recs[i + 1].score)
        }
    }

    @Test
    fun `no recommendations for healthy low-urgency profile`() {
        val recs = RecommendationEngine.generate(
            profile = profile(consistencyScore = 0.8, isConsistent = false, velocityTrend = TrendDirection.FLAT),
            progress = progress()
        )
        assertTrue(recs.isEmpty() || recs.none { it.type == RecommendationType.RECOVERY })
    }
}
