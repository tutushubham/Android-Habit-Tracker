package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FatigueAnalyzerTest {

    private val baseDate = LocalDate.of(2024, 1, 15)

    private fun session(
        daysAgo: Int,
        status: SessionStatus,
        intentId: String = "i1"
    ) = Session(
        id = "s-$daysAgo-$status",
        intentId = intentId,
        domain = Domain.STUDIES,
        date = baseDate.minusDays(daysAgo.toLong()),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = if (status == SessionStatus.COMPLETED) 30 else null,
        status = status,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )

    @Test
    fun `empty sessions returns LOW fatigue`() {
        val result = FatigueAnalyzer.analyze(emptyList())
        assertEquals(FatigueLevel.LOW, result.level)
        assertEquals(0, result.skipStreak)
        assertEquals(0.0, result.recentSkipRate, 0.01)
    }

    @Test
    fun `all completed sessions returns LOW fatigue`() {
        val sessions = (1..5).map { session(it, SessionStatus.COMPLETED) }
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(FatigueLevel.LOW, result.level)
        assertEquals(0, result.skipStreak)
        assertEquals(0.0, result.recentSkipRate, 0.01)
    }

    @Test
    fun `2 recent skips returns MEDIUM fatigue`() {
        val sessions = listOf(
            session(0, SessionStatus.SKIPPED),
            session(1, SessionStatus.SKIPPED),
            session(2, SessionStatus.COMPLETED),
            session(3, SessionStatus.COMPLETED),
            session(4, SessionStatus.COMPLETED)
        )
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(FatigueLevel.MEDIUM, result.level)
        assertEquals(2, result.skipStreak)
    }

    @Test
    fun `3 recent skips returns HIGH fatigue`() {
        val sessions = listOf(
            session(0, SessionStatus.SKIPPED),
            session(1, SessionStatus.SKIPPED),
            session(2, SessionStatus.SKIPPED),
            session(3, SessionStatus.COMPLETED),
            session(4, SessionStatus.COMPLETED)
        )
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(FatigueLevel.HIGH, result.level)
        assertEquals(3, result.skipStreak)
    }

    @Test
    fun `high skip rate without streak returns HIGH fatigue`() {
        val sessions = (1..10).map { i ->
            session(i, if (i <= 7) SessionStatus.SKIPPED else SessionStatus.COMPLETED)
        }
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(FatigueLevel.HIGH, result.level)
        assertTrue(result.recentSkipRate > 0.6)
    }

    @Test
    fun `moderate skip rate returns MEDIUM fatigue`() {
        // Interleave skips so no streak >= 3, but skipRate = 4/10 = 0.4 > 0.3 → MEDIUM
        val sessions = (1..10).map { i ->
            session(i, if (i % 2 == 0 && i <= 8) SessionStatus.SKIPPED else SessionStatus.COMPLETED)
        }
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(FatigueLevel.MEDIUM, result.level)
    }

    @Test
    fun `skip streak broken by completed session resets streak`() {
        val sessions = listOf(
            session(0, SessionStatus.COMPLETED),
            session(1, SessionStatus.SKIPPED),
            session(2, SessionStatus.SKIPPED),
            session(3, SessionStatus.SKIPPED)
        )
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(0, result.skipStreak)
    }

    @Test
    fun `single skip returns LOW fatigue`() {
        val sessions = listOf(
            session(0, SessionStatus.SKIPPED),
            session(1, SessionStatus.COMPLETED),
            session(2, SessionStatus.COMPLETED),
            session(3, SessionStatus.COMPLETED),
            session(4, SessionStatus.COMPLETED)
        )
        val result = FatigueAnalyzer.analyze(sessions)
        assertEquals(FatigueLevel.LOW, result.level)
        assertEquals(1, result.skipStreak)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
