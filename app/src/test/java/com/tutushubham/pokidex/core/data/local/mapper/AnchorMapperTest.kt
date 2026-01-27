package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.AnchorEntity
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorMapperTest {

    @Test
    fun `toDomain converts entity to domain correctly`() {
        // Given
        val entity = AnchorEntity(
            id = "anchor-1",
            block = DayBlock.MORNING,
            domain = Domain.FITNESS,
            defaultMinutes = 60
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("anchor-1", domain.id)
        assertEquals(DayBlock.MORNING, domain.block)
        assertEquals(Domain.FITNESS, domain.domain)
        assertEquals(60, domain.defaultMinutes)
    }

    @Test
    fun `toEntity converts domain to entity correctly`() {
        // Given
        val domain = Anchor(
            id = "anchor-2",
            block = DayBlock.EVENING,
            domain = Domain.STUDIES,
            defaultMinutes = 90
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("anchor-2", entity.id)
        assertEquals(DayBlock.EVENING, entity.block)
        assertEquals(Domain.STUDIES, entity.domain)
        assertEquals(90, entity.defaultMinutes)
    }

    @Test
    fun `toDomain and toEntity are inverse operations`() {
        // Given
        val originalEntity = AnchorEntity(
            id = "anchor-3",
            block = DayBlock.DAY,
            domain = Domain.WORK,
            defaultMinutes = 120
        )

        // When
        val domain = originalEntity.toDomain()
        val convertedEntity = domain.toEntity()

        // Then
        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.block, convertedEntity.block)
        assertEquals(originalEntity.domain, convertedEntity.domain)
        assertEquals(originalEntity.defaultMinutes, convertedEntity.defaultMinutes)
    }
}
