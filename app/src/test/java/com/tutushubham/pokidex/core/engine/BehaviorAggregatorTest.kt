package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BehaviorAggregatorTest {

    private val date = LocalDate.of(2024, 1, 15)

    private fun intent(
        id: String,
        estimatedMinutesPerUnit: Int? = 25
    ) = GoalIntent(
        id = id, domain = Domain.STUDIES, title = "Goal $id",
        targetCount = 100, startDate = date.minusDays(10), endDate = date.plusDays(20),
        priority = 1, estimatedMinutesPerUnit = estimatedMinutesPerUnit
    )

    private fun session(
        intentId: String,
        daysAgo: Int,
        status: SessionStatus,
        actualMinutes: Int? = 30
    ) = Session(
        id = "s-$intentId-$daysAgo",
        intentId = intentId,
        domain = Domain.STUDIES,
        date = date.minusDays(daysAgo.toLong()),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = if (status == SessionStatus.COMPLETED) actualMinutes else null,
        status = status,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )

    @Test
    fun `aggregate creates profile for each intent`() {
        val intents = listOf(intent("a"), intent("b"))
        val result = BehaviorAggregator.aggregate(emptyMap(), intents, date)
        assertEquals(2, result.size)
        assertNotNull(result["a"])
        assertNotNull(result["b"])
    }

    @Test
    fun `aggregate populates fatigue and momentum from sessions`() {
        val sessions = mapOf(
            "a" to listOf(
                session("a", 0, SessionStatus.SKIPPED),
                session("a", 1, SessionStatus.SKIPPED),
                session("a", 2, SessionStatus.SKIPPED)
            )
        )
        val intents = listOf(intent("a"))
        val result = BehaviorAggregator.aggregate(sessions, intents, date)
        assertEquals(FatigueLevel.HIGH, result["a"]!!.fatigue.level)
    }

    @Test
    fun `aggregate with no matching sessions returns neutral profile`() {
        val intents = listOf(intent("a"))
        val result = BehaviorAggregator.aggregate(emptyMap(), intents, date)
        assertEquals(FatigueLevel.LOW, result["a"]!!.fatigue.level)
        assertEquals(0, result["a"]!!.momentum.streakDays)
    }

    @Test
    fun `aggregate with null estimatedMinutesPerUnit returns null learnedEstimate`() {
        val intents = listOf(intent("a", estimatedMinutesPerUnit = null))
        val sessions = mapOf(
            "a" to listOf(session("a", 1, SessionStatus.COMPLETED))
        )
        val result = BehaviorAggregator.aggregate(sessions, intents, date)
        assertNull(result["a"]!!.learnedEstimate)
    }
}
