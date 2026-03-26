package com.tutushubham.pokidex.feature_recommendation

import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.engine.FatigueLevel
import com.tutushubham.pokidex.core.engine.FatigueSignal
import com.tutushubham.pokidex.core.engine.InsightExplanation
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.engine.MomentumSignal
import com.tutushubham.pokidex.core.engine.RecommendationAction
import com.tutushubham.pokidex.core.engine.RecommendationType
import com.tutushubham.pokidex.core.engine.ScoredRecommendation
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RecommendationUiMapperTest {

    private fun profile(intentId: String = "i1") = UserBehaviorProfile(
        intentId = intentId,
        fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.0),
        momentum = MomentumSignal(1, 0.5, true),
        learnedEstimate = null,
        consistencyScore = 0.5,
        skipRate = 0.1,
        completionRate = 0.6,
        peakFocusHours = emptyList(),
        velocityTrend = TrendDirection.FLAT,
        durationTrend = TrendDirection.FLAT,
        weeklyMinutesByDay = DayOfWeek.entries.associateWith { 0 }
    )

    private fun progress(intentId: String = "i1") = IntentProgress(
        intentId = intentId,
        title = "T",
        domain = Domain.STUDIES,
        targetCount = 10,
        completedUnits = 3,
        remainingUnits = 7,
        daysRemaining = 5,
        requiredUnitsPerDay = 2.0,
        currentPace = 1.5,
        isBehind = true
    )

    private fun scored() = ScoredRecommendation(
        type = RecommendationType.SCHEDULE,
        priority = 1,
        title = "Add time",
        message = "msg",
        icon = "ic",
        action = RecommendationAction.AddSession,
        explanation = InsightExplanation(title = "t", description = "d", factors = emptyList()),
        score = 0.9
    )

    @Test
    fun `mapState creates state with correct fields`() {
        val p = profile("intent-99")
        val prog = progress("intent-99")
        val recs = listOf(scored())
        val state = RecommendationUiMapper.mapState(
            intentId = "intent-99",
            goalTitle = "My goal",
            profile = p,
            progress = prog,
            recommendations = recs
        )
        assertFalse(state.isLoading)
        assertEquals("intent-99", state.intentId)
        assertEquals("My goal", state.goalTitle)
        assertEquals(1.5, state.actualPace, 0.001)
        assertEquals(2.0, state.requiredPace, 0.001)
        assertEquals(1, state.recommendations.size)
        assertEquals(RecommendationType.SCHEDULE, state.recommendations.single().type)
    }

    @Test
    fun `mapState with null progress uses zero values`() {
        val state = RecommendationUiMapper.mapState(
            intentId = "i",
            goalTitle = "G",
            profile = profile("i"),
            progress = null,
            recommendations = emptyList()
        )
        assertEquals(0.0, state.actualPace, 0.001)
        assertEquals(0.0, state.requiredPace, 0.001)
        assertTrue(state.recommendations.isEmpty())
    }
}
