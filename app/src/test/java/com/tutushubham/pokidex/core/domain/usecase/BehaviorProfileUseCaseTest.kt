package com.tutushubham.pokidex.core.domain.usecase

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.core.goalIntent
import com.tutushubham.pokidex.core.session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BehaviorProfileUseCaseTest {

    private val date = LocalDate.of(2024, 3, 15)

    private class FakeSessionRepository(
        private val sessions: List<Session> = emptyList()
    ) : SessionRepository {
        override suspend fun getSessionsForDate(date: LocalDate) = sessions.filter { it.date == date }
        override suspend fun getCompletedUnitsForIntent(intentId: String) = 0
        override suspend fun getDistinctDaysWorkedForIntent(intentId: String) = 0
        override suspend fun getTotalActualMinutesForIntent(intentId: String) = 0
        override suspend fun getSkippedSessionCountForIntent(intentId: String) = 0
        override suspend fun getRecentSessions(cutoffDate: LocalDate) = sessions.filter { it.date >= cutoffDate }
        override suspend fun insertSession(session: Session) {}
        override suspend fun updateSession(session: Session) {}
    }

    private class FakeIntentRepository(
        private val intents: List<GoalIntent> = emptyList()
    ) : IntentRepository {
        override suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate) = intents
        override suspend fun insertIntent(intent: GoalIntent) {}
        override suspend fun updateIntent(intent: GoalIntent) {}
        override suspend fun getIntentById(id: String) = intents.firstOrNull { it.id == id }
    }

    private class FakeBehaviorRepository : BehaviorRepository {
        override suspend fun getIntentStats(intentId: String): UserIntentStats? = null
        override suspend fun getAllIntentStats() = emptyList<UserIntentStats>()
        override suspend fun saveIntentStats(stats: UserIntentStats) {}
        override suspend fun getDomainProfile(domain: Domain): DomainBehaviorProfile? = null
        override suspend fun getAllDomainProfiles() = emptyList<DomainBehaviorProfile>()
        override suspend fun saveDomainProfile(profile: DomainBehaviorProfile) {}
        override suspend fun clearAll() {}
    }

    @Test
    fun `getProfiles returns empty map when no intents`() = runBlocking {
        val useCase = BehaviorProfileUseCase(
            FakeSessionRepository(), FakeIntentRepository(), FakeBehaviorRepository()
        )
        val profiles = useCase.getProfiles(date)
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `getProfiles returns profile per intent`() = runBlocking {
        val intent1 = goalIntent(id = "i1", domain = Domain.STUDIES, startDate = date.minusDays(10), endDate = date.plusDays(20))
        val intent2 = goalIntent(id = "i2", domain = Domain.FITNESS, startDate = date.minusDays(10), endDate = date.plusDays(20))

        val sessions = listOf(
            session(id = "s1", intentId = "i1", domain = Domain.STUDIES, date = date.minusDays(1), status = SessionStatus.COMPLETED, actualMinutes = 30),
            session(id = "s2", intentId = "i2", domain = Domain.FITNESS, date = date.minusDays(1), status = SessionStatus.COMPLETED, actualMinutes = 45)
        )

        val useCase = BehaviorProfileUseCase(
            FakeSessionRepository(sessions), FakeIntentRepository(listOf(intent1, intent2)), FakeBehaviorRepository()
        )

        val profiles = useCase.getProfiles(date)
        assertEquals(2, profiles.size)
        assertTrue(profiles.containsKey("i1"))
        assertTrue(profiles.containsKey("i2"))
    }

    @Test
    fun `cache returns same result on second call`() = runBlocking {
        val intent = goalIntent(id = "i1", domain = Domain.STUDIES, startDate = date.minusDays(10), endDate = date.plusDays(20))
        val useCase = BehaviorProfileUseCase(
            FakeSessionRepository(), FakeIntentRepository(listOf(intent)), FakeBehaviorRepository()
        )

        val first = useCase.getProfiles(date)
        val second = useCase.getProfiles(date)
        assertTrue(first === second)
    }

    @Test
    fun `invalidateCache forces recomputation`() = runBlocking {
        val intent = goalIntent(id = "i1", domain = Domain.STUDIES, startDate = date.minusDays(10), endDate = date.plusDays(20))
        val useCase = BehaviorProfileUseCase(
            FakeSessionRepository(), FakeIntentRepository(listOf(intent)), FakeBehaviorRepository()
        )

        val first = useCase.getProfiles(date)
        useCase.invalidateCache()
        val second = useCase.getProfiles(date)
        assertTrue(first !== second)
    }

    @Test
    fun `deriveConsistency returns 0 for no sessions`() {
        val result = BehaviorProfileUseCase.deriveConsistency(emptyList(), date)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `deriveSkipRate computes correctly`() {
        val sessions = listOf(
            session(id = "s1", status = SessionStatus.COMPLETED),
            session(id = "s2", status = SessionStatus.SKIPPED),
            session(id = "s3", status = SessionStatus.COMPLETED),
            session(id = "s4", status = SessionStatus.SKIPPED)
        )
        val rate = BehaviorProfileUseCase.deriveSkipRate(sessions)
        assertEquals(0.5, rate, 0.001)
    }

    @Test
    fun `deriveVelocityTrend returns UP when recent has more completed`() {
        val recent = listOf(
            session(id = "r1", status = SessionStatus.COMPLETED),
            session(id = "r2", status = SessionStatus.COMPLETED),
            session(id = "r3", status = SessionStatus.COMPLETED)
        )
        val prev = listOf(
            session(id = "p1", status = SessionStatus.COMPLETED)
        )
        val trend = BehaviorProfileUseCase.deriveVelocityTrend(recent, prev)
        assertEquals(TrendDirection.UP, trend)
    }

    @Test
    fun `deriveWeeklyMinutes sums by day of week`() {
        val mon = date.minusDays(date.dayOfWeek.value.toLong() - 1)
        val sessions = listOf(
            session(id = "s1", date = mon, status = SessionStatus.COMPLETED, actualMinutes = 30),
            session(id = "s2", date = mon, status = SessionStatus.COMPLETED, actualMinutes = 20)
        )
        val weekly = BehaviorProfileUseCase.deriveWeeklyMinutes(sessions, date)
        assertEquals(50, weekly[mon.dayOfWeek] ?: 0)
    }
}
