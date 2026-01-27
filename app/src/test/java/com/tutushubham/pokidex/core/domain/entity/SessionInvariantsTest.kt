package com.tutushubham.pokidex.core.domain.entity

import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.goalIntent
import com.tutushubham.pokidex.core.session
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SessionInvariantsTest {

    @Test
    fun `planned session has no timestamps`() {
        // Given
        val intent = goalIntent()
        val date = LocalDate.now()

        // When
        val session = Session.planned(intent, date, com.tutushubham.pokidex.core.domain.model.DayBlock.MORNING, 60)

        // Then
        assertNull("Planned session must not have startedAt", session.startedAt)
        assertNull("Planned session must not have endedAt", session.endedAt)
    }

    @Test
    fun `planned session has no actual minutes`() {
        // Given
        val intent = goalIntent()
        val date = LocalDate.now()

        // When
        val session = Session.planned(intent, date, com.tutushubham.pokidex.core.domain.model.DayBlock.MORNING, 60)

        // Then
        assertNull("Planned session must not have actualMinutes", session.actualMinutes)
    }

    @Test
    fun `skipped session must have skipReason`() {
        // Given
        val session = session(
            status = SessionStatus.SKIPPED,
            skipReason = SkipReason.LOW_ENERGY
        )

        // Then
        assert(session.status == SessionStatus.SKIPPED && session.skipReason != null) {
            "A skipped session must have a skipReason"
        }
    }

    @Test
    fun `completed session can have timestamps`() {
        // Given
        val now = Instant.now()
        val session = session(
            status = SessionStatus.COMPLETED,
            startedAt = now,
            endedAt = now.plusSeconds(3600)
        )

        // Then
        assert(session.status == SessionStatus.COMPLETED && session.startedAt != null && session.endedAt != null) {
            "A completed session can have timestamps"
        }
    }

    @Test
    fun `session can exist without artifact`() {
        // Given
        val session = session()

        // Then
        assert(session.id.isNotEmpty()) {
            "A session can exist independently without artifacts"
        }
    }

    @Test
    fun `in progress session has startedAt but no endedAt`() {
        // Given
        val session = session(
            status = SessionStatus.IN_PROGRESS,
            startedAt = Instant.now(),
            endedAt = null
        )

        // Then
        assert(session.status == SessionStatus.IN_PROGRESS && session.startedAt != null && session.endedAt == null) {
            "An in-progress session has startedAt but no endedAt"
        }
    }
}
