package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MomentumAnalyzerTest {

    private val today = LocalDate.of(2024, 1, 15)

    private fun session(
        daysAgo: Int,
        status: SessionStatus,
        intentId: String = "i1"
    ) = Session(
        id = "s-$daysAgo-$status",
        intentId = intentId,
        domain = Domain.STUDIES,
        date = today.minusDays(daysAgo.toLong()),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = if (status == SessionStatus.COMPLETED) 30 else null,
        status = status,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )

    @Test
    fun `empty sessions returns zero momentum`() {
        val result = MomentumAnalyzer.analyze(emptyList(), today)
        assertEquals(0, result.streakDays)
        assertEquals(0.0, result.recentCompletionRate, 0.01)
        assertFalse(result.isConsistent)
    }

    @Test
    fun `3 day streak with high completion rate is consistent`() {
        val sessions = listOf(
            session(1, SessionStatus.COMPLETED),
            session(2, SessionStatus.COMPLETED),
            session(3, SessionStatus.COMPLETED)
        )
        val result = MomentumAnalyzer.analyze(sessions, today)
        assertEquals(3, result.streakDays)
        assertEquals(1.0, result.recentCompletionRate, 0.01)
        assertTrue(result.isConsistent)
    }

    @Test
    fun `streak breaks when gap in completed days`() {
        val sessions = listOf(
            session(1, SessionStatus.COMPLETED),
            session(2, SessionStatus.COMPLETED),
            session(3, SessionStatus.SKIPPED),
            session(4, SessionStatus.COMPLETED)
        )
        val result = MomentumAnalyzer.analyze(sessions, today)
        assertEquals(2, result.streakDays)
    }

    @Test
    fun `today sessions excluded from streak calculation`() {
        val sessions = listOf(
            session(0, SessionStatus.COMPLETED),
            session(2, SessionStatus.COMPLETED)
        )
        val result = MomentumAnalyzer.analyze(sessions, today)
        assertEquals(0, result.streakDays)
    }

    @Test
    fun `low completion rate prevents consistency even with streak`() {
        val sessions = listOf(
            session(1, SessionStatus.COMPLETED),
            session(2, SessionStatus.COMPLETED),
            session(3, SessionStatus.COMPLETED),
            session(4, SessionStatus.SKIPPED),
            session(5, SessionStatus.SKIPPED),
            session(6, SessionStatus.SKIPPED),
            session(7, SessionStatus.SKIPPED)
        )
        val result = MomentumAnalyzer.analyze(sessions, today)
        assertEquals(3, result.streakDays)
        assertFalse(result.isConsistent)
    }

    @Test
    fun `completion rate computed correctly`() {
        val sessions = listOf(
            session(1, SessionStatus.COMPLETED),
            session(2, SessionStatus.SKIPPED),
            session(3, SessionStatus.COMPLETED),
            session(4, SessionStatus.SKIPPED)
        )
        val result = MomentumAnalyzer.analyze(sessions, today)
        assertEquals(0.5, result.recentCompletionRate, 0.01)
    }
}
