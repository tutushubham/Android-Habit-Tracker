package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IntentProgressTest {

    private fun engine() = TodayEngine()

    private fun intent(
        id: String = "i1",
        targetCount: Int? = 100,
        endDate: LocalDate = LocalDate.of(2024, 1, 25),
        estimatedMinutesPerUnit: Int? = 25
    ) = GoalIntent(
        id = id, domain = Domain.STUDIES, title = "DSA",
        targetCount = targetCount, startDate = LocalDate.of(2024, 1, 1),
        endDate = endDate, priority = 1,
        estimatedMinutesPerUnit = estimatedMinutesPerUnit, focusId = null
    )

    private val date = LocalDate.of(2024, 1, 15)
    private val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))

    @Test
    fun `completed 0 and 30 days remaining -- isBehind true, requiredPerDay correct`() {
        val plan = engine().generate(
            date = date, intents = listOf(intent(endDate = date.plusDays(30))),
            anchors = anchors
        )
        val p = plan.progressList.single()
        assertTrue(p.isBehind)
        assertEquals(100, p.remainingUnits)
        assertEquals(100.0 / 30, p.requiredUnitsPerDay, 0.01)
        assertEquals(0.0, p.currentPace, 0.01)
    }

    @Test
    fun `completed exceeds target -- remaining 0, isBehind false`() {
        val plan = engine().generate(
            date = date, intents = listOf(intent(targetCount = 50)),
            anchors = anchors,
            getCompletedUnits = { 60 }, getDaysWorked = { 5 }
        )
        val p = plan.progressList.single()
        assertFalse(p.isBehind)
        assertEquals(0, p.remainingUnits)
        assertEquals(0.0, p.requiredUnitsPerDay, 0.01)
    }

    @Test
    fun `deadline passed, remaining greater than 0 -- requiredPerDay equals remaining, isBehind true`() {
        val pastDeadline = date.minusDays(2)
        val plan = engine().generate(
            date = date, intents = listOf(intent(endDate = pastDeadline)),
            anchors = anchors
        )
        val p = plan.progressList.single()
        assertTrue(p.isBehind)
        assertEquals(0, p.daysRemaining)
        assertEquals(100.0, p.requiredUnitsPerDay, 0.01)
    }

    @Test
    fun `deadline passed, remaining 0 -- isBehind false`() {
        val pastDeadline = date.minusDays(2)
        val plan = engine().generate(
            date = date, intents = listOf(intent(targetCount = 10, endDate = pastDeadline)),
            anchors = anchors,
            getCompletedUnits = { 10 }, getDaysWorked = { 3 }
        )
        val p = plan.progressList.single()
        assertFalse(p.isBehind)
        assertEquals(0.0, p.requiredUnitsPerDay, 0.01)
    }

    @Test
    fun `targetCount null -- excluded from progressList`() {
        val plan = engine().generate(
            date = date, intents = listOf(intent(targetCount = null)),
            anchors = anchors
        )
        assertTrue(plan.progressList.isEmpty())
    }

    @Test
    fun `on track pace -- isBehind false`() {
        val plan = engine().generate(
            date = date,
            intents = listOf(intent(targetCount = 100, endDate = date.plusDays(10))),
            anchors = anchors,
            getCompletedUnits = { 50 }, getDaysWorked = { 5 }
        )
        val p = plan.progressList.single()
        assertFalse(p.isBehind)
        assertEquals(50, p.remainingUnits)
        assertEquals(5.0, p.requiredUnitsPerDay, 0.01)
        assertEquals(10.0, p.currentPace, 0.01)
    }

    @Test
    fun `large overload 200 needed in 2 days -- correct math`() {
        val plan = engine().generate(
            date = date,
            intents = listOf(intent(targetCount = 200, endDate = date.plusDays(2))),
            anchors = anchors
        )
        val p = plan.progressList.single()
        assertTrue(p.isBehind)
        assertEquals(200, p.remainingUnits)
        assertEquals(100.0, p.requiredUnitsPerDay, 0.01)
    }

    @Test
    fun `behind goal gets urgency boost -- session planned for behind intent over non-behind`() {
        val behindIntent = GoalIntent(
            "behind", Domain.STUDIES, "Behind", 100,
            date.minusDays(10), date.plusDays(5), 2,
            estimatedMinutesPerUnit = 25, focusId = null
        )
        val onTrackIntent = GoalIntent(
            "ontrack", Domain.STUDIES, "OnTrack", 10,
            date.minusDays(10), date.plusDays(30), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )
        val plan = engine().generate(
            date = date, anchors = anchors,
            intents = listOf(behindIntent, onTrackIntent),
            getCompletedUnits = { id -> if (id == "ontrack") 5 else 0 },
            getDaysWorked = { id -> if (id == "ontrack") 5 else 0 }
        )
        assertEquals("behind", plan.sessions.first().intentId)
    }

    @Test
    fun `overloaded and behind is isCritical -- verify enrichment`() {
        val singleSmallAnchor = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 30))
        val plan = engine().generate(
            date = date,
            intents = listOf(intent(targetCount = 100, endDate = date.plusDays(2))),
            anchors = singleSmallAnchor
        )
        val p = plan.progressList.single()
        assertTrue(p.isOverloaded)
        assertTrue(p.isBehind)
        assertTrue(p.isCritical)
        assertTrue(p.overloadSeverity != null && p.overloadSeverity > 1.0)
    }

    @Test
    fun `learned estimate affects capacity -- produces different minutes than static`() {
        val i = GoalIntent(
            "dsa", Domain.STUDIES, "DSA", 30,
            date.minusDays(10), date.plusDays(10), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )
        val planStatic = engine().generate(date = date, intents = listOf(i), anchors = anchors)

        val learnedEstimate = LearnedEstimate(40, 40.0, 1.0, 25)
        val behaviorMap = mapOf(
            "dsa" to IntentBehaviorProfile(
                FatigueSignal(FatigueLevel.LOW, 0, 0.0),
                MomentumSignal(0, 0.0, false),
                learnedEstimate
            )
        )
        val planLearned = engine().generate(
            date = date, intents = listOf(i), anchors = anchors,
            behaviorMap = behaviorMap
        )
        val staticMinutes = planStatic.sessions.firstOrNull()?.plannedMinutes ?: 0
        val learnedMinutes = planLearned.sessions.firstOrNull()?.plannedMinutes ?: 0
        assertTrue("static=$staticMinutes learned=$learnedMinutes", staticMinutes != learnedMinutes)
    }

    @Test
    fun `progressList sorted critical first, then behind, then on-track`() {
        val multiAnchors = listOf(
            Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60),
            Anchor("a2", DayBlock.DAY, Domain.STUDIES, 60),
            Anchor("a3", DayBlock.EVENING, Domain.STUDIES, 60)
        )
        val critical = GoalIntent(
            "critical", Domain.STUDIES, "Critical", 200,
            date.minusDays(10), date.plusDays(1), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )
        val behind = GoalIntent(
            "behind", Domain.STUDIES, "Behind", 20,
            date.minusDays(10), date.plusDays(50), 2,
            estimatedMinutesPerUnit = 25, focusId = null
        )
        val onTrack = GoalIntent(
            "ontrack", Domain.STUDIES, "OnTrack", 10,
            date.minusDays(10), date.plusDays(30), 3,
            estimatedMinutesPerUnit = 25, focusId = null
        )
        val plan = engine().generate(
            date = date, anchors = multiAnchors,
            intents = listOf(onTrack, behind, critical),
            getCompletedUnits = { id -> if (id == "ontrack") 8 else 0 },
            getDaysWorked = { id -> if (id == "ontrack") 8 else 0 }
        )
        assertEquals(3, plan.progressList.size)
        assertEquals("critical", plan.progressList[0].intentId)
        assertTrue(plan.progressList[0].isCritical)
        assertEquals("behind", plan.progressList[1].intentId)
        assertTrue(plan.progressList[1].isBehind)
        assertFalse(plan.progressList[1].isCritical)
        assertEquals("ontrack", plan.progressList[2].intentId)
        assertFalse(plan.progressList[2].isBehind)
    }
}
