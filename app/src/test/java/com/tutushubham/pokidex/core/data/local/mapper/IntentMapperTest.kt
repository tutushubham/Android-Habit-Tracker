package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.IntentEntity
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class IntentMapperTest {

    @Test
    fun `toDomain converts entity to domain correctly`() {
        // Given
        val entity = IntentEntity(
            id = "intent-1",
            domain = Domain.FITNESS,
            title = "Workout daily",
            targetCount = 30,
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 1, 31),
            priority = 1
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("intent-1", domain.id)
        assertEquals(Domain.FITNESS, domain.domain)
        assertEquals("Workout daily", domain.title)
        assertEquals(30, domain.targetCount)
        assertEquals(LocalDate.of(2024, 1, 1), domain.startDate)
        assertEquals(LocalDate.of(2024, 1, 31), domain.endDate)
        assertEquals(1, domain.priority)
    }

    @Test
    fun `toEntity converts domain to entity correctly`() {
        // Given
        val domain = GoalIntent(
            id = "intent-2",
            domain = Domain.WORK,
            title = "Complete project",
            targetCount = null,
            startDate = LocalDate.of(2024, 2, 1),
            endDate = LocalDate.of(2024, 2, 28),
            priority = 2
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("intent-2", entity.id)
        assertEquals(Domain.WORK, entity.domain)
        assertEquals("Complete project", entity.title)
        assertNull(entity.targetCount)
        assertEquals(LocalDate.of(2024, 2, 1), entity.startDate)
        assertEquals(LocalDate.of(2024, 2, 28), entity.endDate)
        assertEquals(2, entity.priority)
    }

    @Test
    fun `toDomain and toEntity are inverse operations`() {
        // Given
        val originalEntity = IntentEntity(
            id = "intent-3",
            domain = Domain.STUDIES,
            title = "Learn Kotlin",
            targetCount = 100,
            startDate = LocalDate.of(2024, 3, 1),
            endDate = LocalDate.of(2024, 5, 1),
            priority = 1
        )

        // When
        val domain = originalEntity.toDomain()
        val convertedEntity = domain.toEntity()

        // Then
        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.domain, convertedEntity.domain)
        assertEquals(originalEntity.title, convertedEntity.title)
        assertEquals(originalEntity.targetCount, convertedEntity.targetCount)
        assertEquals(originalEntity.startDate, convertedEntity.startDate)
        assertEquals(originalEntity.endDate, convertedEntity.endDate)
        assertEquals(originalEntity.priority, convertedEntity.priority)
    }
}
