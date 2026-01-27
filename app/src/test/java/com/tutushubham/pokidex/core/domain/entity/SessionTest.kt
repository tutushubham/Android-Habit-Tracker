package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SessionTest {

    @Test
    fun `planned factory creates session with correct defaults`() {
        // Given
        val intent = GoalIntent(
            id = "intent-1",
            domain = Domain.FITNESS,
            title = "Workout daily",
            targetCount = 30,
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(30),
            priority = 1
        )
        val date = LocalDate.now()
        val block = DayBlock.MORNING
        val minutes = 60

        // When
        val session = Session.planned(intent, date, block, minutes)

        // Then
        assertNotNull(session.id)
        assertEquals("intent-1", session.intentId)
        assertEquals(Domain.FITNESS, session.domain)
        assertEquals(date, session.date)
        assertEquals(block, session.block)
        assertEquals(minutes, session.plannedMinutes)
        assertNull(session.actualMinutes)
        assertEquals(SessionStatus.PLANNED, session.status)
        assertNull(session.skipReason)
        assertNull(session.startedAt)
        assertNull(session.endedAt)
    }

    @Test
    fun `planned factory generates unique IDs`() {
        // Given
        val intent = GoalIntent(
            id = "intent-1",
            domain = Domain.WORK,
            title = "Complete project",
            targetCount = null,
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(7),
            priority = 2
        )
        val date = LocalDate.now()
        val block = DayBlock.DAY

        // When
        val session1 = Session.planned(intent, date, block, 30)
        val session2 = Session.planned(intent, date, block, 30)

        // Then
        assertNotNull(session1.id)
        assertNotNull(session2.id)
        assertEquals(session1.id != session2.id, true)
    }

    @Test
    fun `planned factory uses intent domain`() {
        // Given
        val intent = GoalIntent(
            id = "intent-1",
            domain = Domain.STUDIES,
            title = "Learn Kotlin",
            targetCount = 100,
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(60),
            priority = 1
        )

        // When
        val session = Session.planned(intent, LocalDate.now(), DayBlock.EVENING, 90)

        // Then
        assertEquals(Domain.STUDIES, session.domain)
        assertEquals(intent.domain, session.domain)
    }
}
