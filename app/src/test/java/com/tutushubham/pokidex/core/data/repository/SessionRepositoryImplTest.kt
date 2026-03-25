package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.SessionEntity
import com.tutushubham.pokidex.core.data.local.db.dao.SessionDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionRepositoryImplTest {

    @Test
    fun `getSessionsForDate maps entities to domain correctly`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val entities = listOf(
            SessionEntity(
                id = "session-1",
                intentId = "intent-1",
                domain = Domain.FITNESS,
                date = date,
                block = DayBlock.MORNING,
                plannedMinutes = 60,
                actualMinutes = null,
                status = SessionStatus.PLANNED,
                skipReason = null,
                startedAt = null,
                endedAt = null
            )
        )
        val fakeDao = FakeSessionDao(entities.toMutableList())
        val repository = SessionRepositoryImpl(fakeDao)

        // When
        val sessions = repository.getSessionsForDate(date)

        // Then
        assertEquals(1, sessions.size)
        assertEquals("session-1", sessions[0].id)
        assertEquals(Domain.FITNESS, sessions[0].domain)
        assertEquals(SessionStatus.PLANNED, sessions[0].status)
    }

    @Test
    fun `insertSession maps domain to entity correctly`() = runTest {
        // Given
        val fakeDao = FakeSessionDao(mutableListOf())
        val repository = SessionRepositoryImpl(fakeDao)
        val session = Session.planned(
            GoalIntent("intent-1", Domain.WORK, "Code", null, LocalDate.now(), LocalDate.now().plusDays(30), 1),
            LocalDate.of(2024, 1, 15),
            DayBlock.DAY,
            120
        )

        // When
        repository.insertSession(session)

        // Then
        assertEquals(1, fakeDao.insertedSessions.size)
        assertEquals(session.id, fakeDao.insertedSessions[0].id)
        assertEquals(session.intentId, fakeDao.insertedSessions[0].intentId)
        assertEquals(session.domain, fakeDao.insertedSessions[0].domain)
    }

    @Test
    fun `updateSession maps domain to entity correctly`() = runTest {
        // Given
        val existingEntity = SessionEntity(
            id = "session-1",
            intentId = "intent-1",
            domain = Domain.FITNESS,
            date = LocalDate.of(2024, 1, 15),
            block = DayBlock.MORNING,
            plannedMinutes = 60,
            actualMinutes = null,
            status = SessionStatus.PLANNED,
            skipReason = null,
            startedAt = null,
            endedAt = null
        )
        val fakeDao = FakeSessionDao(mutableListOf(existingEntity))
        val repository = SessionRepositoryImpl(fakeDao)
        val updatedSession = existingEntity.toDomain().copy(status = SessionStatus.COMPLETED)

        // When
        repository.updateSession(updatedSession)

        // Then
        assertEquals(1, fakeDao.updatedSessions.size)
        assertEquals("session-1", fakeDao.updatedSessions[0].id)
        assertEquals(SessionStatus.COMPLETED, fakeDao.updatedSessions[0].status)
    }
}

// Fake DAO for testing
class FakeSessionDao(
    private val sessions: MutableList<SessionEntity>
) : SessionDao {
    val insertedSessions = mutableListOf<SessionEntity>()
    val updatedSessions = mutableListOf<SessionEntity>()

    override suspend fun getSessionsForDate(date: java.time.LocalDate): List<SessionEntity> {
        return sessions.filter { it.date == date }
    }

    override suspend fun getCompletedUnitsForIntent(intentId: String): Int =
        sessions.count { it.intentId == intentId && it.status == SessionStatus.COMPLETED }

    override suspend fun getDistinctDaysWorkedForIntent(intentId: String): Int =
        sessions.filter { it.intentId == intentId && it.status == SessionStatus.COMPLETED }
            .map { it.date }.distinct().size

    override suspend fun getTotalActualMinutesForIntent(intentId: String): Int =
        sessions.filter { it.intentId == intentId && it.status == SessionStatus.COMPLETED }
            .sumOf { it.actualMinutes ?: 0 }

    override suspend fun getSkippedSessionCountForIntent(intentId: String): Int =
        sessions.count { it.intentId == intentId && it.status == SessionStatus.SKIPPED }

    override suspend fun getSessionsSince(cutoffDate: LocalDate): List<SessionEntity> =
        sessions.filter { it.date >= cutoffDate }.sortedWith(compareBy<SessionEntity> { it.intentId }.thenByDescending { it.date })

    override suspend fun insert(session: SessionEntity) {
        insertedSessions.add(session)
        sessions.add(session)
    }

    override suspend fun update(session: SessionEntity) {
        updatedSessions.add(session)
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = session
        }
    }
}

