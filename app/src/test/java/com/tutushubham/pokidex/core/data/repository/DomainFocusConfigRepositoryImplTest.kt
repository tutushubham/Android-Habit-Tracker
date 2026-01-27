package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.DomainFocusConfigEntity
import com.tutushubham.pokidex.core.data.local.db.dao.DomainFocusConfigDao
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DomainFocusConfigRepositoryImplTest {

    @Test
    fun `getConfig returns correct config for domain`() = runTest {
        // Given
        val entity = DomainFocusConfigEntity(
            domain = Domain.FITNESS,
            strategyType = "MANUAL",
            strategyData = null,
            manualOverrideFocusId = "focus-1",
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf(entity))
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)

        // When
        val config = repository.getConfig(Domain.FITNESS)

        // Then
        assertEquals(Domain.FITNESS, config?.domain)
        assertEquals(FocusStrategy.Manual, config?.strategy)
        assertEquals("focus-1", config?.manualOverrideFocusId)
    }

    @Test
    fun `getConfig returns null for non-existent domain`() = runTest {
        // Given
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf())
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)

        // When
        val config = repository.getConfig(Domain.WORK)

        // Then
        assertNull(config)
    }

    @Test
    fun `upsertConfig creates new config`() = runTest {
        // Given
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf())
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)
        val config = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Manual,
            manualOverrideFocusId = "focus-1",
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        repository.upsertConfig(config)

        // Then
        assertEquals(1, fakeDao.upsertedConfigs.size)
        assertEquals(Domain.FITNESS, fakeDao.upsertedConfigs[0].domain)
        assertEquals("MANUAL", fakeDao.upsertedConfigs[0].strategyType)
    }

    @Test
    fun `upsertConfig updates existing config`() = runTest {
        // Given
        val existingEntity = DomainFocusConfigEntity(
            domain = Domain.FITNESS,
            strategyType = "MANUAL",
            strategyData = null,
            manualOverrideFocusId = "focus-1",
            createdAt = LocalDate.of(2024, 1, 1)
        )
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf(existingEntity))
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)
        val updatedConfig = DomainFocusConfig(
            domain = Domain.FITNESS,
            strategy = FocusStrategy.Rotation(listOf("focus-1", "focus-2")),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        repository.upsertConfig(updatedConfig)

        // Then
        assertEquals(1, fakeDao.upsertedConfigs.size)
        assertEquals("ROTATION", fakeDao.upsertedConfigs[0].strategyType)
        assert(fakeDao.upsertedConfigs[0].strategyData?.contains("focus-1") == true)
    }

    @Test
    fun `upsertConfig handles ROTATION strategy`() = runTest {
        // Given
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf())
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)
        val config = DomainFocusConfig(
            domain = Domain.STUDIES,
            strategy = FocusStrategy.Rotation(listOf("focus-1", "focus-2", "focus-3")),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        repository.upsertConfig(config)

        // Then
        assertEquals("ROTATION", fakeDao.upsertedConfigs[0].strategyType)
        assert(fakeDao.upsertedConfigs[0].strategyData?.contains("focus-1") == true)
    }

    @Test
    fun `upsertConfig handles WEIGHTED strategy`() = runTest {
        // Given
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf())
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)
        val config = DomainFocusConfig(
            domain = Domain.WORK,
            strategy = FocusStrategy.Weighted(mapOf("focus-1" to 2, "focus-2" to 1)),
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        repository.upsertConfig(config)

        // Then
        assertEquals("WEIGHTED", fakeDao.upsertedConfigs[0].strategyType)
        assert(fakeDao.upsertedConfigs[0].strategyData?.contains("focus-1") == true)
    }

    @Test
    fun `upsertConfig handles DEADLINE strategy`() = runTest {
        // Given
        val fakeDao = FakeDomainFocusConfigDao(mutableListOf())
        val repository = DomainFocusConfigRepositoryImpl(fakeDao)
        val config = DomainFocusConfig(
            domain = Domain.HOBBY,
            strategy = FocusStrategy.DeadlineDriven,
            manualOverrideFocusId = null,
            createdAt = LocalDate.of(2024, 1, 1)
        )

        // When
        repository.upsertConfig(config)

        // Then
        assertEquals("DEADLINE", fakeDao.upsertedConfigs[0].strategyType)
        assertNull(fakeDao.upsertedConfigs[0].strategyData)
    }
}

// Fake DAO for testing
class FakeDomainFocusConfigDao(
    private val configs: MutableList<DomainFocusConfigEntity>
) : DomainFocusConfigDao {
    val upsertedConfigs = mutableListOf<DomainFocusConfigEntity>()

    override suspend fun getConfigByDomain(domain: Domain): DomainFocusConfigEntity? {
        return configs.firstOrNull { it.domain == domain }
    }

    override suspend fun getAllConfigs(): List<DomainFocusConfigEntity> {
        return configs.toList()
    }

    override suspend fun upsertConfig(config: DomainFocusConfigEntity) {
        upsertedConfigs.add(config)
        val index = configs.indexOfFirst { it.domain == config.domain }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
    }
}
