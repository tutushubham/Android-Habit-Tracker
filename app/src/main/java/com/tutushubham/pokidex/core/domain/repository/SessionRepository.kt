package com.tutushubham.pokidex.core.domain.repository

import com.tutushubham.pokidex.core.domain.entity.Session
import java.time.LocalDate

interface SessionRepository {
    suspend fun getSessionsForDate(date: LocalDate): List<Session>
    suspend fun insertSession(session: Session)
    suspend fun updateSession(session: Session)
}
