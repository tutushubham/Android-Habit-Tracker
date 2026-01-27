package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.CaptureEntity
import com.tutushubham.pokidex.core.domain.entity.Capture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CaptureMapperTest {

    @Test
    fun `toDomain converts entity to domain correctly`() {
        // Given
        val entity = CaptureEntity(
            id = "capture-1",
            content = "Remember to call dentist",
            createdAt = Instant.ofEpochMilli(1705312800000),
            resolved = false,
            resolvedSessionId = null
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals("capture-1", domain.id)
        assertEquals("Remember to call dentist", domain.content)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(false, domain.resolved)
        assertNull(domain.resolvedSessionId)
    }

    @Test
    fun `toEntity converts domain to entity correctly`() {
        // Given
        val domain = Capture(
            id = "capture-2",
            content = "Buy groceries",
            createdAt = Instant.ofEpochMilli(1705316400000),
            resolved = true,
            resolvedSessionId = "session-1"
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("capture-2", entity.id)
        assertEquals("Buy groceries", entity.content)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(true, entity.resolved)
        assertEquals("session-1", entity.resolvedSessionId)
    }

    @Test
    fun `toDomain and toEntity are inverse operations`() {
        // Given
        val originalEntity = CaptureEntity(
            id = "capture-3",
            content = "Test capture",
            createdAt = Instant.ofEpochMilli(1705312800000),
            resolved = false,
            resolvedSessionId = "session-2"
        )

        // When
        val domain = originalEntity.toDomain()
        val convertedEntity = domain.toEntity()

        // Then
        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.content, convertedEntity.content)
        assertEquals(originalEntity.createdAt, convertedEntity.createdAt)
        assertEquals(originalEntity.resolved, convertedEntity.resolved)
        assertEquals(originalEntity.resolvedSessionId, convertedEntity.resolvedSessionId)
    }
}
