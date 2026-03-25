package com.tutushubham.pokidex.core.data.repository

import com.tutushubham.pokidex.core.data.local.db.dao.SessionDao
import com.tutushubham.pokidex.core.data.local.mapper.toDomain
import com.tutushubham.pokidex.core.data.local.mapper.toEntity
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import java.time.LocalDate

class SessionRepositoryImpl(
    private val dao: SessionDao
) : SessionRepository {

    override suspend fun getSessionsForDate(date: LocalDate): List<Session> {
        return dao.getSessionsForDate(date).map { it.toDomain() }
    }

    override suspend fun getCompletedUnitsForIntent(intentId: String): Int {
        return dao.getCompletedUnitsForIntent(intentId)
    }

    override suspend fun getDistinctDaysWorkedForIntent(intentId: String): Int {
        return dao.getDistinctDaysWorkedForIntent(intentId)
    }

    override suspend fun getTotalActualMinutesForIntent(intentId: String): Int {
        return dao.getTotalActualMinutesForIntent(intentId)
    }

    override suspend fun getSkippedSessionCountForIntent(intentId: String): Int {
        return dao.getSkippedSessionCountForIntent(intentId)
    }

    override suspend fun getRecentSessions(cutoffDate: LocalDate): List<Session> {
        return dao.getSessionsSince(cutoffDate).map { it.toDomain() }
    }

    override suspend fun insertSession(session: Session) {
        dao.insert(session.toEntity())
    }

    override suspend fun updateSession(session: Session) {
        dao.update(session.toEntity())
    }
}
