package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.SessionEntity
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SessionMapperTest {

    @Test
    fun `toDomain converts entity to domain correctly`() {
        // Given
        val entity = SessionEntity(
            id = "session-1",
            intentId = "intent-1",
            domain = Domain.FITNESS,
            date = LocalDate.of(2024, 1, 15),
            block = DayBlock.MORNING,
            plannedMinutes = 60,
            actualMinutes = 55,
            status = SessionStatus.COMPLETED,
            skipReason = null,
            startedAt = Instant.ofEpochMilli(1705312800000),
            endedAt = Instant.ofEpochMilli(1705316400000)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("session-1", domain.id)
        assertEquals("intent-1", domain.intentId)
        assertEquals(Domain.FITNESS, domain.domain)
        assertEquals(LocalDate.of(2024, 1, 15), domain.date)
        assertEquals(DayBlock.MORNING, domain.block)
        assertEquals(60, domain.plannedMinutes)
        assertEquals(55, domain.actualMinutes)
        assertEquals(SessionStatus.COMPLETED, domain.status)
        assertNull(domain.skipReason)
        assertEquals(entity.startedAt, domain.startedAt)
        assertEquals(entity.endedAt, domain.endedAt)
    }

    @Test
    fun `toEntity converts domain to entity correctly`() {
        // Given
        val domain = Session(
            id = "session-2",
            intentId = "intent-2",
            domain = Domain.WORK,
            date = LocalDate.of(2024, 2, 20),
            block = DayBlock.DAY,
            plannedMinutes = 120,
            actualMinutes = null,
            status = SessionStatus.PLANNED,
            skipReason = null,
            startedAt = null,
            endedAt = null
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("session-2", entity.id)
        assertEquals("intent-2", entity.intentId)
        assertEquals(Domain.WORK, entity.domain)
        assertEquals(LocalDate.of(2024, 2, 20), entity.date)
        assertEquals(DayBlock.DAY, entity.block)
        assertEquals(120, entity.plannedMinutes)
        assertNull(entity.actualMinutes)
        assertEquals(SessionStatus.PLANNED, entity.status)
        assertNull(entity.skipReason)
        assertNull(entity.startedAt)
        assertNull(entity.endedAt)
    }

    @Test
    fun `toDomain and toEntity are inverse operations`() {
        // Given
        val originalEntity = SessionEntity(
            id = "session-3",
            intentId = "intent-3",
            domain = Domain.HOBBY,
            date = LocalDate.of(2024, 3, 10),
            block = DayBlock.EVENING,
            plannedMinutes = 90,
            actualMinutes = 85,
            status = SessionStatus.COMPLETED,
            skipReason = SkipReason.LOW_ENERGY,
            startedAt = Instant.ofEpochMilli(1705312800000),
            endedAt = Instant.ofEpochMilli(1705316400000)
        )

        // When
        val domain = originalEntity.toDomain()
        val convertedEntity = domain.toEntity()

        // Then
        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.intentId, convertedEntity.intentId)
        assertEquals(originalEntity.domain, convertedEntity.domain)
        assertEquals(originalEntity.date, convertedEntity.date)
        assertEquals(originalEntity.block, convertedEntity.block)
        assertEquals(originalEntity.plannedMinutes, convertedEntity.plannedMinutes)
        assertEquals(originalEntity.actualMinutes, convertedEntity.actualMinutes)
        assertEquals(originalEntity.status, convertedEntity.status)
        assertEquals(originalEntity.skipReason, convertedEntity.skipReason)
        assertEquals(originalEntity.startedAt, convertedEntity.startedAt)
        assertEquals(originalEntity.endedAt, convertedEntity.endedAt)
    }
}
