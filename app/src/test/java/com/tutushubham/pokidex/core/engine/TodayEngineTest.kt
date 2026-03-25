package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.anchor
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.goalIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodayEngineTest {

    private fun engine() = TodayEngine()

    @Test
    fun `generate creates sessions for anchors without existing sessions`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor1 = anchor(id = "anchor-1", block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val anchor2 = anchor(id = "anchor-2", block = DayBlock.DAY, domain = Domain.WORK, defaultMinutes = 120)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 1)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.WORK, title = "Code", startDate = date, endDate = date.plusDays(30), priority = 1)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor1, anchor2)
        )

        assertEquals(2, plan.sessions.size)
        assertTrue(plan.sessions.any { it.block == DayBlock.MORNING && it.domain == Domain.FITNESS })
        assertTrue(plan.sessions.any { it.block == DayBlock.DAY && it.domain == Domain.WORK })
        plan.sessions.forEach { session ->
            assertEquals(SessionStatus.PLANNED, session.status)
            assertEquals(date, session.date)
        }
    }

    @Test
    fun `generate skips anchors with existing sessions in same block`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor1 = Anchor("anchor-1", DayBlock.MORNING, Domain.FITNESS, 60)
        val anchor2 = Anchor("anchor-2", DayBlock.DAY, Domain.WORK, 120)
        val intent1 = GoalIntent("intent-1", Domain.FITNESS, "Workout", null, date, date.plusDays(30), 1)
        val intent2 = GoalIntent("intent-2", Domain.WORK, "Code", null, date, date.plusDays(30), 1)

        val existingSession = Session.planned(intent1, date, DayBlock.MORNING, 60)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor1, anchor2),
            existingSessions = listOf(existingSession)
        )

        assertEquals(1, plan.sessions.size)
        assertEquals(DayBlock.DAY, plan.sessions[0].block)
        assertEquals(Domain.WORK, plan.sessions[0].domain)
    }

    @Test
    fun `generate selects one intent when multiple intents match domain`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga", startDate = date, endDate = date.plusDays(30), priority = 1)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor)
        )

        assertEquals(1, plan.sessions.size)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
        assertTrue(plan.sessions[0].intentId in listOf("intent-1", "intent-2"))
    }

    @Test
    fun `generate selects one measurable intent when two same domain`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.FITNESS, 60))
        val lowUrgency = GoalIntent("low", Domain.FITNESS, "Workout", 30, date, date.plusDays(30), 2, estimatedMinutesPerUnit = 25, focusId = null)
        val highUrgency = GoalIntent("high", Domain.FITNESS, "Yoga", 10, date, date.plusDays(5), 1, estimatedMinutesPerUnit = 25, focusId = null)

        val plan = engine().generate(
            date = date,
            intents = listOf(lowUrgency, highUrgency),
            anchors = anchors
        )

        assertEquals(1, plan.sessions.size)
        assertTrue(plan.sessions[0].intentId in listOf("low", "high"))
        assertTrue(plan.sessions[0].plannedMinutes >= 25)
    }

    @Test
    fun `generate returns empty plan when no anchors exist`() {
        val date = LocalDate.of(2024, 1, 15)
        val plan = engine().generate(date = date, intents = emptyList(), anchors = emptyList())
        assertTrue(plan.sessions.isEmpty())
    }

    @Test
    fun `generate returns empty plan when no matching intents exist`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = Anchor("anchor-1", DayBlock.MORNING, Domain.FITNESS, 60)
        val intent = GoalIntent("intent-1", Domain.WORK, "Code", null, date, date.plusDays(30), 1)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent),
            anchors = listOf(anchor)
        )

        assertTrue(plan.sessions.isEmpty())
    }

    @Test
    fun `generate uses anchor defaultMinutes for planned minutes`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 90)
        val intent = goalIntent(domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 1)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent),
            anchors = listOf(anchor)
        )

        assertEquals(1, plan.sessions.size)
        assertEquals(90, plan.sessions[0].plannedMinutes)
    }

    @Test
    fun `generate selects one intent when no focus configured`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga", startDate = date, endDate = date.plusDays(30), priority = 1)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor)
        )

        assertEquals(1, plan.sessions.size)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
    }

    @Test
    fun `generate selects correct intent when focus is configured`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Running workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga session", startDate = date, endDate = date.plusDays(30), priority = 1)

        val focus = com.tutushubham.pokidex.core.domain.entity.Focus("focus-1", Domain.FITNESS, "Running", 1, null)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor),
            resolveFocus = { if (it == Domain.FITNESS) focus else null }
        )

        assertEquals(1, plan.sessions.size)
        assertEquals("intent-1", plan.sessions[0].intentId)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
    }

    @Test
    fun `generate creates no session when focus exists but no matching intent`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Yoga session", startDate = date, endDate = date.plusDays(30), priority = 1)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Swimming workout", startDate = date, endDate = date.plusDays(30), priority = 2)

        val focus = com.tutushubham.pokidex.core.domain.entity.Focus("focus-1", Domain.FITNESS, "Running", 1, null)

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor),
            resolveFocus = { if (it == Domain.FITNESS) focus else null }
        )

        assertEquals(0, plan.sessions.size)
    }

    @Test
    fun `generate prioritizes deadline focus over rotation focus`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Running workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga session", startDate = date, endDate = date.plusDays(30), priority = 1)

        val deadlineFocus = com.tutushubham.pokidex.core.domain.entity.Focus("focus-deadline", Domain.FITNESS, "Yoga", 1, date.plusDays(5))

        val plan = engine().generate(
            date = date,
            intents = listOf(intent1, intent2),
            anchors = listOf(anchor),
            resolveFocus = { if (it == Domain.FITNESS) deadlineFocus else null }
        )

        assertEquals(1, plan.sessions.size)
        assertEquals("intent-2", plan.sessions[0].intentId)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
    }

    // --- Urgency, capacity, overload tests ---

    @Test
    fun `2 blocks same domain allocates domain-wide urgency across blocks`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(
            Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60),
            Anchor("a2", DayBlock.DAY, Domain.STUDIES, 60)
        )
        val intent = GoalIntent(
            "dsa", Domain.STUDIES, "DSA", 6, date, date.plusDays(6), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val plan = engine().generate(date = date, intents = listOf(intent), anchors = anchors)

        assertEquals(1, plan.sessions.size)
        assertEquals("dsa", plan.sessions[0].intentId)
        assertEquals(25, plan.sessions[0].plannedMinutes)
        assertEquals(1, plan.progressList.size)
        assertTrue(plan.progressList[0].isBehind)
    }

    @Test
    fun `2 blocks same domain and urgency 3 units per day splits allocation`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(
            Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60),
            Anchor("a2", DayBlock.DAY, Domain.STUDIES, 60)
        )
        val intent = GoalIntent(
            "dsa", Domain.STUDIES, "DSA", 30, date, date.plusDays(10), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val plan = engine().generate(date = date, intents = listOf(intent), anchors = anchors)

        assertEquals(2, plan.sessions.size)
        val totalMinutes = plan.sessions.sumOf { it.plannedMinutes }
        assertEquals(75, totalMinutes) // 25*2 + 25*1 = 75
        assertTrue(plan.overloadedIntentIds.isEmpty())
    }

    @Test
    fun `3 intents same domain picks by urgency then fills second block`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(
            Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60),
            Anchor("a2", DayBlock.DAY, Domain.STUDIES, 60)
        )
        val intentA = GoalIntent("a", Domain.STUDIES, "A", 10, date, date.plusDays(5), 1, estimatedMinutesPerUnit = 25, focusId = null)
        val intentB = GoalIntent("b", Domain.STUDIES, "B", 5, date, date.plusDays(5), 2, estimatedMinutesPerUnit = 25, focusId = null)
        val intentC = GoalIntent("c", Domain.STUDIES, "C", null, date, date.plusDays(5), 3, focusId = null)

        val plan = engine().generate(
            date = date,
            intents = listOf(intentA, intentB, intentC),
            anchors = anchors
        )

        assertEquals(2, plan.sessions.size)
        assertTrue(plan.sessions.all { it.domain == Domain.STUDIES })
        assertTrue(plan.sessions.sumOf { it.plannedMinutes } >= 25)
    }

    @Test
    fun `overdue goal still picks intent and caps urgency`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val intent = GoalIntent(
            "overdue", Domain.STUDIES, "Overdue", 5,
            date.minusDays(10), date.minusDays(1), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val plan = engine().generate(date = date, intents = listOf(intent), anchors = anchors)

        assertEquals(1, plan.sessions.size)
        assertEquals("overdue", plan.sessions[0].intentId)
        assertEquals(50, plan.sessions[0].plannedMinutes)
    }

    @Test
    fun `time-based wins when measurable has zero remaining`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.FITNESS, 60))
        val measurable = GoalIntent("m", Domain.FITNESS, "Measurable", 10, date, date.plusDays(5), 2, estimatedMinutesPerUnit = 30, focusId = null)
        val timeBased = GoalIntent("t", Domain.FITNESS, "Gym", null, date, date.plusDays(5), 1, focusId = null)

        val plan = engine().generate(
            date = date,
            intents = listOf(measurable, timeBased),
            anchors = anchors,
            getCompletedUnits = { id -> if (id == "m") 10 else 0 }
        )

        assertEquals(1, plan.sessions.size)
        assertEquals("t", plan.sessions[0].intentId)
        assertEquals(60, plan.sessions[0].plannedMinutes)
    }

    @Test
    fun `capacity zero produces no session for that block`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 10))
        val intent = GoalIntent(
            "dsa", Domain.STUDIES, "DSA", 6, date, date.plusDays(6), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val plan = engine().generate(date = date, intents = listOf(intent), anchors = anchors)

        assertEquals(0, plan.sessions.size)
        assertTrue(plan.overloadedIntentIds.contains("dsa"))
    }

    @Test
    fun `zero remaining excludes intent from allocation`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val intent = GoalIntent(
            "done", Domain.STUDIES, "Done", 5, date, date.plusDays(10), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val plan = engine().generate(
            date = date,
            intents = listOf(intent),
            anchors = anchors,
            getCompletedUnits = { id -> if (id == "done") 5 else 0 }
        )

        assertEquals(0, plan.sessions.size)
        assertTrue(plan.overloadedIntentIds.isEmpty())
    }

    @Test
    fun `severe overload reports overloadedIntentIds and high severity`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val intent = GoalIntent(
            "heavy", Domain.STUDIES, "Heavy", 60, date, date.plusDays(2), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val plan = engine().generate(date = date, intents = listOf(intent), anchors = anchors)

        assertTrue(plan.overloadedIntentIds.contains("heavy"))
        val detail = plan.overloadDetails.single { it.intentId == "heavy" }
        assertTrue(detail.severity >= 3.0)
        assertEquals(30, detail.needed)
        assertEquals(2, detail.capacity)

        val progress = plan.progressList.single { it.intentId == "heavy" }
        assertTrue(progress.isOverloaded)
        assertTrue(progress.isCritical)
        assertTrue(progress.overloadSeverity!! >= 3.0)
    }

    // --- Behavior Intelligence tests ---

    @Test
    fun `high fatigue still selects high urgency intent`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val urgentIntent = GoalIntent(
            "urgent", Domain.STUDIES, "Urgent", 50, date, date.plusDays(2), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val behaviorMap = mapOf(
            "urgent" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.HIGH, skipStreak = 5, recentSkipRate = 0.8),
                momentum = MomentumSignal(streakDays = 0, recentCompletionRate = 0.2, isConsistent = false),
                learnedEstimate = null
            )
        )

        val plan = engine().generate(
            date = date,
            intents = listOf(urgentIntent),
            anchors = anchors,
            behaviorMap = behaviorMap
        )

        assertEquals(1, plan.sessions.size)
        assertEquals("urgent", plan.sessions[0].intentId)
    }

    @Test
    fun `starvation boost prioritizes unplanned intent`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val intentA = GoalIntent("a", Domain.STUDIES, "A", 20, date, date.plusDays(10), 1, estimatedMinutesPerUnit = 25, focusId = null)
        val intentB = GoalIntent("b", Domain.STUDIES, "B", 20, date, date.plusDays(10), 1, estimatedMinutesPerUnit = 25, focusId = null)

        val lastPlannedDates = mapOf("a" to date.minusDays(1))

        val plan = engine().generate(
            date = date,
            intents = listOf(intentA, intentB),
            anchors = anchors,
            lastPlannedDates = lastPlannedDates
        )

        assertEquals("b", plan.sessions[0].intentId)
    }

    @Test
    fun `momentum boost gives consistent intent edge in selection`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val intentA = GoalIntent("a", Domain.STUDIES, "A", 20, date, date.plusDays(10), 1, estimatedMinutesPerUnit = 25, focusId = null)
        val intentB = GoalIntent("b", Domain.STUDIES, "B", 20, date, date.plusDays(10), 1, estimatedMinutesPerUnit = 25, focusId = null)

        val behaviorMap = mapOf(
            "a" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.0),
                momentum = MomentumSignal(streakDays = 5, recentCompletionRate = 0.9, isConsistent = true),
                learnedEstimate = null
            ),
            "b" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.0),
                momentum = MomentumSignal(streakDays = 0, recentCompletionRate = 0.3, isConsistent = false),
                learnedEstimate = null
            )
        )

        val plan = engine().generate(
            date = date,
            intents = listOf(intentA, intentB),
            anchors = anchors,
            behaviorMap = behaviorMap
        )

        assertEquals("a", plan.sessions[0].intentId)
    }

    @Test
    fun `learned estimate affects capacity allocation`() {
        val date = LocalDate.of(2024, 1, 15)
        val anchors = listOf(Anchor("a1", DayBlock.MORNING, Domain.STUDIES, 60))
        val intent = GoalIntent(
            "dsa", Domain.STUDIES, "DSA", 30, date.minusDays(10), date.plusDays(10), 1,
            estimatedMinutesPerUnit = 25, focusId = null
        )

        val planStatic = engine().generate(date = date, intents = listOf(intent), anchors = anchors)

        val learnedEstimate = LearnedEstimate(
            effectiveMinutesPerUnit = 40, learnedMinutesPerUnit = 40.0,
            confidence = 1.0, staticEstimate = 25
        )
        val behaviorMap = mapOf(
            "dsa" to IntentBehaviorProfile(
                fatigue = FatigueSignal(FatigueLevel.LOW, 0, 0.0),
                momentum = MomentumSignal(0, 0.0, false),
                learnedEstimate = learnedEstimate
            )
        )

        val planLearned = engine().generate(
            date = date, intents = listOf(intent), anchors = anchors,
            behaviorMap = behaviorMap
        )

        val staticMinutes = planStatic.sessions.firstOrNull()?.plannedMinutes ?: 0
        val learnedMinutes = planLearned.sessions.firstOrNull()?.plannedMinutes ?: 0
        assertTrue("Learned estimate should change capacity: static=$staticMinutes learned=$learnedMinutes",
            staticMinutes != learnedMinutes)
    }
}

// Shared fake implementations used by TodayEngineTest and FocusResolverTest

class FakeFocusRepository(
    private val focuses: List<com.tutushubham.pokidex.core.domain.entity.Focus>
) : FocusRepository {
    override suspend fun getFocusById(id: String) = focuses.firstOrNull { it.id == id }
    override suspend fun getFocusesByDomain(domain: Domain) = focuses.filter { it.domain == domain }
    override suspend fun getAllFocuses() = focuses
    override suspend fun insertFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
    override suspend fun updateFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
    override suspend fun deleteFocus(id: String) {}
}

class FakeDomainFocusConfigRepository(
    private val config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig?
) : DomainFocusConfigRepository {
    override suspend fun getConfig(domain: Domain) = if (config?.domain == domain) config else null
    override suspend fun upsertConfig(config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig) {}
}
