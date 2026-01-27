package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.FocusEntity
import com.tutushubham.pokidex.core.data.local.db.dao.FocusDao
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FocusRepositoryImplTest {

    @Test
    fun `getFocusById returns correct focus`() = runTest {
        // Given
        val entity = FocusEntity(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 2,
            deadline = LocalDate.of(2024, 12, 31)
        )
        val fakeDao = FakeFocusDao(mutableListOf(entity))
        val repository = FocusRepositoryImpl(fakeDao)

        // When
        val focus = repository.getFocusById("focus-1")

        // Then
        assertEquals("focus-1", focus?.id)
        assertEquals(Domain.FITNESS, focus?.domain)
        assertEquals("Running", focus?.name)
        assertEquals(2, focus?.weight)
    }

    @Test
    fun `getFocusById returns null for non-existent focus`() = runTest {
        // Given
        val fakeDao = FakeFocusDao(mutableListOf())
        val repository = FocusRepositoryImpl(fakeDao)

        // When
        val focus = repository.getFocusById("non-existent")

        // Then
        assertNull(focus)
    }

    @Test
    fun `getFocusesByDomain filters by domain correctly`() = runTest {
        // Given
        val entities = listOf(
            FocusEntity("focus-1", Domain.FITNESS, "Running", 1, null),
            FocusEntity("focus-2", Domain.FITNESS, "Yoga", 1, null),
            FocusEntity("focus-3", Domain.STUDIES, "DSA", 1, null)
        )
        val fakeDao = FakeFocusDao(entities.toMutableList())
        val repository = FocusRepositoryImpl(fakeDao)

        // When
        val focuses = repository.getFocusesByDomain(Domain.FITNESS)

        // Then
        assertEquals(2, focuses.size)
        assert(focuses.all { it.domain == Domain.FITNESS })
        assertEquals(setOf("Running", "Yoga"), focuses.map { it.name }.toSet())
    }

    @Test
    fun `getFocusesByDomain returns empty list when no focuses exist for domain`() = runTest {
        // Given
        val fakeDao = FakeFocusDao(mutableListOf())
        val repository = FocusRepositoryImpl(fakeDao)

        // When
        val focuses = repository.getFocusesByDomain(Domain.WORK)

        // Then
        assertEquals(0, focuses.size)
    }

    @Test
    fun `insertFocus maps domain to entity correctly`() = runTest {
        // Given
        val fakeDao = FakeFocusDao(mutableListOf())
        val repository = FocusRepositoryImpl(fakeDao)
        val focus = Focus(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 2,
            deadline = LocalDate.of(2024, 12, 31)
        )

        // When
        repository.insertFocus(focus)

        // Then
        assertEquals(1, fakeDao.insertedFocuses.size)
        assertEquals("focus-1", fakeDao.insertedFocuses[0].id)
        assertEquals(Domain.FITNESS, fakeDao.insertedFocuses[0].domain)
        assertEquals("Running", fakeDao.insertedFocuses[0].name)
    }

    @Test
    fun `updateFocus maps domain to entity correctly`() = runTest {
        // Given
        val existingEntity = FocusEntity(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 1,
            deadline = null
        )
        val fakeDao = FakeFocusDao(mutableListOf(existingEntity))
        val repository = FocusRepositoryImpl(fakeDao)
        val updatedFocus = Focus(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 3,
            deadline = LocalDate.of(2024, 12, 31)
        )

        // When
        repository.updateFocus(updatedFocus)

        // Then
        assertEquals(1, fakeDao.updatedFocuses.size)
        assertEquals("focus-1", fakeDao.updatedFocuses[0].id)
        assertEquals(3, fakeDao.updatedFocuses[0].weight)
        assertEquals(LocalDate.of(2024, 12, 31), fakeDao.updatedFocuses[0].deadline)
    }

    @Test
    fun `deleteFocus removes focus correctly`() = runTest {
        // Given
        val entity = FocusEntity(
            id = "focus-1",
            domain = Domain.FITNESS,
            name = "Running",
            weight = 1,
            deadline = null
        )
        val fakeDao = FakeFocusDao(mutableListOf(entity))
        val repository = FocusRepositoryImpl(fakeDao)

        // When
        repository.deleteFocus("focus-1")

        // Then
        assertEquals("focus-1", fakeDao.deletedId)
    }
}

// Fake DAO for testing
class FakeFocusDao(
    private val focuses: MutableList<FocusEntity>
) : FocusDao {
    val insertedFocuses = mutableListOf<FocusEntity>()
    val updatedFocuses = mutableListOf<FocusEntity>()
    var deletedId: String? = null

    override suspend fun getFocusById(id: String): FocusEntity? {
        return focuses.firstOrNull { it.id == id }
    }

    override suspend fun getFocusesByDomain(domain: Domain): List<FocusEntity> {
        return focuses.filter { it.domain == domain }
    }

    override suspend fun insert(focus: FocusEntity) {
        insertedFocuses.add(focus)
        focuses.add(focus)
    }

    override suspend fun update(focus: FocusEntity) {
        updatedFocuses.add(focus)
        val index = focuses.indexOfFirst { it.id == focus.id }
        if (index >= 0) {
            focuses[index] = focus
        }
    }

    override suspend fun delete(id: String) {
        deletedId = id
        focuses.removeAll { it.id == id }
    }
}
