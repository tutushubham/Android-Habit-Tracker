package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.IntentEntity
import com.tutushubham.pokidex.core.data.local.db.dao.IntentDao
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.model.Domain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class IntentRepositoryImplTest {

    @Test
    fun `getIntentsForDateRange maps entities to domain correctly`() = runTest {
        // Given
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 1, 31)
        val entities = listOf(
            IntentEntity(
                id = "intent-1",
                domain = Domain.FITNESS,
                title = "Workout",
                targetCount = 30,
                startDate = startDate,
                endDate = endDate,
                priority = 1
            )
        )
        val fakeDao = FakeIntentDao(entities.toMutableList())
        val repository = IntentRepositoryImpl(fakeDao)

        // When
        val intents = repository.getIntentsForDateRange(startDate, endDate)

        // Then
        assertEquals(1, intents.size)
        assertEquals("intent-1", intents[0].id)
        assertEquals(Domain.FITNESS, intents[0].domain)
        assertEquals("Workout", intents[0].title)
    }

    @Test
    fun `getIntentById returns mapped domain when found`() = runTest {
        // Given
        val entity = IntentEntity(
            id = "intent-1",
            domain = Domain.WORK,
            title = "Code",
            targetCount = null,
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 1, 31),
            priority = 1
        )
        val fakeDao = FakeIntentDao(mutableListOf(entity))
        val repository = IntentRepositoryImpl(fakeDao)

        // When
        val intent = repository.getIntentById("intent-1")

        // Then
        assertEquals("intent-1", intent?.id)
        assertEquals(Domain.WORK, intent?.domain)
    }

    @Test
    fun `getIntentById returns null when not found`() = runTest {
        // Given
        val fakeDao = FakeIntentDao(mutableListOf())
        val repository = IntentRepositoryImpl(fakeDao)

        // When
        val intent = repository.getIntentById("non-existent")

        // Then
        assertNull(intent)
    }

    @Test
    fun `insertIntent maps domain to entity correctly`() = runTest {
        // Given
        val fakeDao = FakeIntentDao(mutableListOf())
        val repository = IntentRepositoryImpl(fakeDao)
        val intent = GoalIntent(
            id = "intent-1",
            domain = Domain.STUDIES,
            title = "Learn Kotlin",
            targetCount = 100,
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 3, 1),
            priority = 1
        )

        // When
        repository.insertIntent(intent)

        // Then
        assertEquals(1, fakeDao.insertedIntents.size)
        assertEquals("intent-1", fakeDao.insertedIntents[0].id)
        assertEquals(Domain.STUDIES, fakeDao.insertedIntents[0].domain)
    }
}

// Fake DAO for testing
class FakeIntentDao(
    private val intents: MutableList<IntentEntity>
) : IntentDao {
    val insertedIntents = mutableListOf<IntentEntity>()
    val updatedIntents = mutableListOf<IntentEntity>()

    override suspend fun getIntentsForDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): List<IntentEntity> {
        return intents.filter { it.startDate <= endDate && it.endDate >= startDate }
    }

    override suspend fun getIntentById(id: String): IntentEntity? {
        return intents.firstOrNull { it.id == id }
    }

    override suspend fun insert(intent: IntentEntity) {
        insertedIntents.add(intent)
        intents.add(intent)
    }

    override suspend fun update(intent: IntentEntity) {
        updatedIntents.add(intent)
        val index = intents.indexOfFirst { it.id == intent.id }
        if (index >= 0) {
            intents[index] = intent
        }
    }
}
