package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.FocusEntity
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FocusMapperTest {

    @Test
    fun `toDomain converts entity to domain correctly`() {
        // Given
        val entity = FocusEntity(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 2,
            deadline = LocalDate.of(2024, 12, 31)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("focus-1", domain.id)
        assertEquals(Domain.FITNESS, domain.domain)
        assertEquals("Running", domain.name)
        assertEquals(2, domain.weight)
        assertEquals(LocalDate.of(2024, 12, 31), domain.deadline)
    }

    @Test
    fun `toDomain handles null deadline`() {
        // Given
        val entity = FocusEntity(
            id = "focus-2",
            domain = Domain.STUDIES,
            name = "DSA",
            weight = 1,
            deadline = null
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("focus-2", domain.id)
        assertNull(domain.deadline)
    }

    @Test
    fun `toEntity converts domain to entity correctly`() {
        // Given
        val domain = Focus(
            id = "focus-3",
            domain = Domain.WORK,
            name = "Android",
            weight = 3,
            deadline = LocalDate.of(2024, 6, 30)
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("focus-3", entity.id)
        assertEquals(Domain.WORK, entity.domain)
        assertEquals("Android", entity.name)
        assertEquals(3, entity.weight)
        assertEquals(LocalDate.of(2024, 6, 30), entity.deadline)
    }

    @Test
    fun `toEntity handles null deadline`() {
        // Given
        val domain = Focus(
            id = "focus-4",
            domain = Domain.HOBBY,
            name = "Guitar",
            weight = 1,
            deadline = null
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("focus-4", entity.id)
        assertNull(entity.deadline)
    }

    @Test
    fun `toDomain and toEntity are inverse operations`() {
        // Given
        val originalEntity = FocusEntity(
            id = "focus-5",
            domain = Domain.FITNESS,
            name = "Yoga",
            weight = 2,
            deadline = LocalDate.of(2024, 8, 15)
        )

        // When
        val domain = originalEntity.toDomain()
        val convertedEntity = domain.toEntity()

        // Then
        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.domain, convertedEntity.domain)
        assertEquals(originalEntity.name, convertedEntity.name)
        assertEquals(originalEntity.weight, convertedEntity.weight)
        assertEquals(originalEntity.deadline, convertedEntity.deadline)
    }

    @Test
    fun `toDomain and toEntity are inverse operations with null deadline`() {
        // Given
        val originalEntity = FocusEntity(
            id = "focus-6",
            domain = Domain.STUDIES,
            name = "Math",
            weight = 1,
            deadline = null
        )

        // When
        val domain = originalEntity.toDomain()
        val convertedEntity = domain.toEntity()

        // Then
        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.domain, convertedEntity.domain)
        assertEquals(originalEntity.name, convertedEntity.name)
        assertEquals(originalEntity.weight, convertedEntity.weight)
        assertEquals(originalEntity.deadline, convertedEntity.deadline)
        assertNull(convertedEntity.deadline)
    }
}
