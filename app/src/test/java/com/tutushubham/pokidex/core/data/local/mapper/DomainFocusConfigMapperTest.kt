package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.DomainFocusConfigEntity
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DomainFocusConfigMapperTest {

    @Test
    fun `toDomain converts MANUAL strategy correctly`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.FITNESS,
            strategyType = "MANUAL",
            strategyData = null,
            manualOverrideFocusId = "focus-1",
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(Domain.FITNESS, domain.domain)
        assertEquals(FocusStrategy.Manual, domain.strategy)
        assertEquals("focus-1", domain.manualOverrideFocusId)
        assertEquals(LocalDate.of(2024, 1, 1), domain.createdAt)
    }

    @Test
    fun `toDomain converts ROTATION strategy correctly`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.STUDIES,
            strategyType = "ROTATION",
            strategyData = """{"order":["focus-1","focus-2","focus-3"]}""",
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(Domain.STUDIES, domain.domain)
        assert(domain.strategy is FocusStrategy.Rotation)
        val rotation = domain.strategy as FocusStrategy.Rotation
        assertEquals(listOf("focus-1", "focus-2", "focus-3"), rotation.order)
        assertNull(domain.manualOverrideFocusId)
    }

    @Test
    fun `toDomain converts WEIGHTED strategy correctly`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.WORK,
            strategyType = "WEIGHTED",
            strategyData = """{"weights":{"focus-1":2,"focus-2":1}}""",
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(Domain.WORK, domain.domain)
        assert(domain.strategy is FocusStrategy.Weighted)
        val weighted = domain.strategy as FocusStrategy.Weighted
        assertEquals(mapOf("focus-1" to 2, "focus-2" to 1), weighted.weights)
    }

    @Test
    fun `toDomain converts DEADLINE strategy correctly`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.HOBBY,
            strategyType = "DEADLINE",
            strategyData = null,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(Domain.HOBBY, domain.domain)
        assertEquals(FocusStrategy.DeadlineDriven, domain.strategy)
    }

    @Test
    fun `toDomain defaults to MANUAL for unknown strategy type`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.FITNESS,
            strategyType = "UNKNOWN",
            strategyData = null,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(FocusStrategy.Manual, domain.strategy)
    }

    @Test
    fun `toEntity converts MANUAL strategy correctly`() {
        // Given
        val domain = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = "focus-1",
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals(Domain.FITNESS, entity.domain)
        assertEquals("MANUAL", entity.strategyType)
        assertNull(entity.strategyData)
        assertEquals("focus-1", entity.manualOverrideFocusId)
        assertEquals(LocalDate.of(2024, 1, 1), entity.createdAt)
    }

    @Test
    fun `toEntity converts ROTATION strategy correctly`() {
        // Given
        val domain = DomainFocusConfig(
            domain = Domain.STUDIES,
            strategy = FocusStrategy.Rotation(listOf("focus-1", "focus-2")),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("ROTATION", entity.strategyType)
        assertEquals("""{"order":["focus-1","focus-2"]}""", entity.strategyData)
    }

    @Test
    fun `toEntity converts WEIGHTED strategy correctly`() {
        // Given
        val domain = DomainFocusConfig(
            domain = Domain.WORK,
            strategy = FocusStrategy.Weighted(mapOf("focus-1" to 3, "focus-2" to 1)),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("WEIGHTED", entity.strategyType)
        assert(entity.strategyData?.contains("focus-1") == true)
        assert(entity.strategyData?.contains("3") == true)
    }

    @Test
    fun `toEntity converts DEADLINE strategy correctly`() {
        // Given
        val domain = DomainFocusConfig(
            domain = Domain.HOBBY,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = domain.toEntity()

        // Then
        assertEquals("DEADLINE", entity.strategyType)
        assertNull(entity.strategyData)
    }

    @Test
    fun `toDomain and toEntity are inverse operations for MANUAL`() {
        // Given
        val originalDomain = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = "focus-1",
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = originalDomain.toEntity()
        val convertedDomain = entity.toDomain()

        // Then
        assertEquals(originalDomain.domain, convertedDomain.domain)
        assertEquals(originalDomain.strategy, convertedDomain.strategy)
        assertEquals(originalDomain.manualOverrideFocusId, convertedDomain.manualOverrideFocusId)
        assertEquals(originalDomain.createdAt, convertedDomain.createdAt)
    }

    @Test
    fun `toDomain and toEntity are inverse operations for ROTATION`() {
        // Given
        val originalDomain = DomainFocusConfig(
            domain = Domain.STUDIES,
            strategy = FocusStrategy.Rotation(listOf("focus-1", "focus-2", "focus-3")),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = originalDomain.toEntity()
        val convertedDomain = entity.toDomain()

        // Then
        assertEquals(originalDomain.domain, convertedDomain.domain)
        assert(convertedDomain.strategy is FocusStrategy.Rotation)
        val originalRotation = originalDomain.strategy as FocusStrategy.Rotation
        val convertedRotation = convertedDomain.strategy as FocusStrategy.Rotation
        assertEquals(originalRotation.order, convertedRotation.order)
        assertEquals(originalDomain.createdAt, convertedDomain.createdAt)
    }

    @Test
    fun `toDomain and toEntity are inverse operations for WEIGHTED`() {
        // Given
        val originalDomain = DomainFocusConfig(
            domain = Domain.WORK,
            strategy = FocusStrategy.Weighted(mapOf("focus-1" to 2, "focus-2" to 1)),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val entity = originalDomain.toEntity()
        val convertedDomain = entity.toDomain()

        // Then
        assertEquals(originalDomain.domain, convertedDomain.domain)
        assert(convertedDomain.strategy is FocusStrategy.Weighted)
        val originalWeighted = originalDomain.strategy as FocusStrategy.Weighted
        val convertedWeighted = convertedDomain.strategy as FocusStrategy.Weighted
        assertEquals(originalWeighted.weights, convertedWeighted.weights)
        assertEquals(originalDomain.createdAt, convertedDomain.createdAt)
    }

    @Test
    fun `toDomain handles empty ROTATION order`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.FITNESS,
            strategyType = "ROTATION",
            strategyData = null,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assert(domain.strategy is FocusStrategy.Rotation)
        val rotation = domain.strategy as FocusStrategy.Rotation
        assertEquals(emptyList<String>(), rotation.order)
    }

    @Test
    fun `toDomain handles empty WEIGHTED weights`() {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.FITNESS,
            strategyType = "WEIGHTED",
            strategyData = null,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        val domain = entity.toDomain()

        // Then
        assert(domain.strategy is FocusStrategy.Weighted)
        val weighted = domain.strategy as FocusStrategy.Weighted
        assertEquals(emptyMap<String, Int>(), weighted.weights)
    }
}
