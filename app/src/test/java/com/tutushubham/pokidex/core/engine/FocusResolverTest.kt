package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FocusResolverTest {

    @Test
    fun `resolve returns null when no focuses exist for domain`() = runTest {
        // Given
        val focusRepo = FakeFocusRepositoryForResolver(emptyList())
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(null)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertNull(result)
    }

    @Test
    fun `resolve returns first focus when no config exists`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, null)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(null)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertEquals("focus-1", result?.id)
    }

    @Test
    fun `resolveManual returns manual override focus when valid`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, null)
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = "focus-2",
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertEquals("focus-2", result?.id)
    }

    @Test
    fun `resolveManual returns first focus when manual override is null`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, null)
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertEquals("focus-1", result?.id)
    }

    @Test
    fun `resolveManual returns first focus when manual override is invalid`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, null)
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = "non-existent",
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertEquals("focus-1", result?.id)
    }

    @Test
    fun `resolveRotation cycles through focuses based on days since start`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, null),
            Focus("focus-3", Domain.FITNESS, "Swimming", 1, null)
        )
        val createdAt = LocalDate.of(2024, 1, 1)
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Rotation(listOf("focus-1", "focus-2", "focus-3")),
            manualOverrideFocusId = null,
            createdAt = createdAt
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When - Day 0 (same day as creation)
        val result0 = resolver.resolve(Domain.FITNESS, createdAt)

        // Then
        assertEquals("focus-1", result0?.id)

        // When - Day 1
        val result1 = resolver.resolve(Domain.FITNESS, createdAt.plusDays(1))

        // Then
        assertEquals("focus-2", result1?.id)

        // When - Day 2
        val result2 = resolver.resolve(Domain.FITNESS, createdAt.plusDays(2))

        // Then
        assertEquals("focus-3", result2?.id)

        // When - Day 3 (wraps around)
        val result3 = resolver.resolve(Domain.FITNESS, createdAt.plusDays(3))

        // Then
        assertEquals("focus-1", result3?.id)
    }

    @Test
    fun `resolveRotation returns first focus when order is empty`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null)
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Rotation(emptyList()),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertEquals("focus-1", result?.id)
    }

    @Test
    fun `resolveWeighted selects focus based on expanded weights`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, null)
        )
        val createdAt = LocalDate.of(2024, 1, 1)
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Weighted(mapOf("focus-1" to 2, "focus-2" to 1)),
            manualOverrideFocusId = null,
            createdAt = createdAt
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When - Day 0 (should get focus-1 from expanded list [focus-1, focus-1, focus-2])
        val result0 = resolver.resolve(Domain.FITNESS, createdAt)

        // Then
        assertEquals("focus-1", result0?.id)

        // When - Day 1
        val result1 = resolver.resolve(Domain.FITNESS, createdAt.plusDays(1))

        // Then
        assertEquals("focus-1", result1?.id)

        // When - Day 2
        val result2 = resolver.resolve(Domain.FITNESS, createdAt.plusDays(2))

        // Then
        assertEquals("focus-2", result2?.id)

        // When - Day 3 (wraps around)
        val result3 = resolver.resolve(Domain.FITNESS, createdAt.plusDays(3))

        // Then
        assertEquals("focus-1", result3?.id)
    }

    @Test
    fun `resolveWeighted returns first focus when weights are empty`() = runTest {
        // Given
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null)
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Weighted(emptyMap()),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, LocalDate.of(2024, 6, 15))

        // Then
        assertEquals("focus-1", result?.id)
    }

    @Test
    fun `resolveDeadline returns focus with closest upcoming deadline`() = runTest {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, today.plusDays(10)),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, today.plusDays(5)),
            Focus("focus-3", Domain.FITNESS, "Swimming", 1, today.plusDays(20))
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, today)

        // Then
        assertEquals("focus-2", result?.id) // Closest deadline (5 days)
    }

    @Test
    fun `resolveDeadline filters out past deadlines`() = runTest {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, today.minusDays(5)), // Past
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, today.plusDays(10)) // Future
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, today)

        // Then
        assertEquals("focus-2", result?.id) // Only future deadline
    }

    @Test
    fun `resolveDeadline returns first focus when no active deadlines`() = runTest {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, null),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, today.minusDays(1)) // Past
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, today)

        // Then
        assertEquals("focus-1", result?.id)
    }

    @Test
    fun `resolveDeadline handles deadline on same day`() = runTest {
        // Given
        val today = LocalDate.of(2024, 6, 15)
        val focuses = listOf(
            Focus("focus-1", Domain.FITNESS, "Running", 1, today),
            Focus("focus-2", Domain.FITNESS, "Yoga", 1, today.plusDays(5))
        )
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val focusRepo = FakeFocusRepositoryForResolver(focuses)
        val configRepo = FakeDomainFocusConfigRepositoryForResolver(config)
        val resolver = FocusResolver(focusRepo, configRepo)

        // When
        val result = resolver.resolve(Domain.FITNESS, today)

        // Then
        assertEquals("focus-1", result?.id) // Today's deadline is closest
    }
}

// Fake repositories for testing
class FakeFocusRepositoryForResolver(
    private val focuses: List<Focus>
) : FocusRepository {
    override suspend fun getFocusById(id: String): Focus? =
        focuses.firstOrNull { it.id == id }

    override suspend fun getFocusesByDomain(domain: Domain): List<Focus> =
        focuses.filter { it.domain == domain }

    override suspend fun insertFocus(focus: Focus) {}
    override suspend fun updateFocus(focus: Focus) {}
    override suspend fun deleteFocus(id: String) {}
}

class FakeDomainFocusConfigRepositoryForResolver(
    private val config: DomainFocusConfig?
) : DomainFocusConfigRepository {
    override suspend fun getConfig(domain: Domain): DomainFocusConfig? {
        return if (config?.domain == domain) config else null
    }

    override suspend fun upsertConfig(config: DomainFocusConfig) {}
}
