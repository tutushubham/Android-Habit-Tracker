package com.tutushubham.pokidex.core.domain.usecase

import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride
import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.TodayEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodayPlannerUseCaseTest {

    private val date = LocalDate.of(2024, 1, 15)

    private val sampleIntent = GoalIntent(
        id = "i1", domain = Domain.STUDIES, title = "DSA",
        targetCount = 100, startDate = date.minusDays(10), endDate = date.plusDays(20),
        priority = 1, estimatedMinutesPerUnit = 25
    )

    private val sampleAnchor = Anchor(
        id = "a1", block = DayBlock.MORNING, domain = Domain.STUDIES, defaultMinutes = 60
    )

    @Test
    fun `planToday loads data and returns plan`() = runTest {
        val useCase = buildUseCase()
        val plan = useCase.planToday(date)
        assertNotNull(plan)
    }

    @Test
    fun `planToday persists updated stats after planning`() = runTest {
        val behaviorRepo = FakeBehaviorRepository()
        val sessionRepo = FakeUseCaseSessionRepository(
            listOf(completedSession("i1", 1, 30))
        )
        val useCase = buildUseCase(
            sessionRepo = sessionRepo,
            behaviorRepo = behaviorRepo
        )

        useCase.planToday(date)

        assertTrue(behaviorRepo.savedStats.isNotEmpty())
        val saved = behaviorRepo.savedStats.first()
        assertEquals("i1", saved.intentId)
        assertEquals(date, saved.lastUpdated)
    }

    @Test
    fun `no persisted data still produces a plan (cold start)`() = runTest {
        val useCase = buildUseCase(behaviorRepo = FakeBehaviorRepository())
        val plan = useCase.planToday(date)
        assertNotNull(plan)
    }

    @Test
    fun `domain profiles saved after each plan`() = runTest {
        val behaviorRepo = FakeBehaviorRepository()
        val sessionRepo = FakeUseCaseSessionRepository(
            listOf(completedSession("i1", 1, 30))
        )
        val useCase = buildUseCase(
            sessionRepo = sessionRepo,
            behaviorRepo = behaviorRepo
        )

        useCase.planToday(date)

        assertTrue(behaviorRepo.savedProfiles.isNotEmpty())
        val profile = behaviorRepo.savedProfiles.first()
        assertEquals(Domain.STUDIES, profile.domain)
        assertEquals(date, profile.lastUpdated)
    }

    private fun completedSession(intentId: String, daysAgo: Int, actualMinutes: Int) = Session(
        id = "s-$intentId-$daysAgo",
        intentId = intentId,
        domain = Domain.STUDIES,
        date = date.minusDays(daysAgo.toLong()),
        block = DayBlock.MORNING,
        plannedMinutes = 30,
        actualMinutes = actualMinutes,
        status = SessionStatus.COMPLETED,
        skipReason = null,
        startedAt = null,
        endedAt = null
    )

    private fun buildUseCase(
        sessionRepo: SessionRepository = FakeUseCaseSessionRepository(),
        behaviorRepo: BehaviorRepository? = null
    ): TodayPlannerUseCase {
        val intentRepo = object : IntentRepository {
            override suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate) =
                listOf(sampleIntent)
            override suspend fun insertIntent(intent: GoalIntent) {}
            override suspend fun updateIntent(intent: GoalIntent) {}
            override suspend fun getIntentById(id: String) = null
        }
        val anchorRepo = object : AnchorRepository {
            override suspend fun getAllAnchors() = listOf(sampleAnchor)
            override suspend fun getAnchorByBlockAndDomain(block: DayBlock, domain: Domain) = null
            override suspend fun insertAnchor(anchor: Anchor) {}
            override suspend fun updateAnchor(anchor: Anchor) {}
            override suspend fun deleteAnchor(id: String) {}
        }
        val focusResolver = FocusResolver(
            object : FocusRepository {
                override suspend fun getFocusById(id: String) = null
                override suspend fun getFocusesByDomain(domain: Domain) = emptyList<Focus>()
                override suspend fun getAllFocuses() = emptyList<Focus>()
                override suspend fun insertFocus(focus: Focus) {}
                override suspend fun updateFocus(focus: Focus) {}
                override suspend fun deleteFocus(id: String) {}
            },
            object : DomainFocusConfigRepository {
                override suspend fun getConfig(domain: Domain) = null
                override suspend fun upsertConfig(config: DomainFocusConfig) {}
            },
            object : DailyFocusOverrideRepository {
                override suspend fun getOverride(domain: Domain, date: LocalDate) = null
                override suspend fun setOverride(override: DailyFocusOverride) {}
                override suspend fun clearOverride(domain: Domain, date: LocalDate) {}
            }
        )

        return TodayPlannerUseCase(
            intentRepository = intentRepo,
            sessionRepository = sessionRepo,
            anchorRepository = anchorRepo,
            focusResolver = focusResolver,
            behaviorRepository = behaviorRepo,
            engine = TodayEngine()
        )
    }
}

private class FakeUseCaseSessionRepository(
    private val sessions: List<Session> = emptyList()
) : SessionRepository {
    override suspend fun getSessionsForDate(date: LocalDate) = sessions.filter { it.date == date }
    override suspend fun getCompletedUnitsForIntent(intentId: String) =
        sessions.count { it.intentId == intentId && it.status == SessionStatus.COMPLETED }
    override suspend fun getDistinctDaysWorkedForIntent(intentId: String) =
        sessions.filter { it.intentId == intentId && it.status == SessionStatus.COMPLETED }
            .map { it.date }.distinct().size
    override suspend fun getTotalActualMinutesForIntent(intentId: String) =
        sessions.filter { it.intentId == intentId && it.status == SessionStatus.COMPLETED }
            .sumOf { it.actualMinutes ?: 0 }
    override suspend fun getSkippedSessionCountForIntent(intentId: String) =
        sessions.count { it.intentId == intentId && it.status == SessionStatus.SKIPPED }
    override suspend fun getRecentSessions(cutoffDate: LocalDate) =
        sessions.filter { it.date >= cutoffDate }
    override suspend fun insertSession(session: Session) {}
    override suspend fun updateSession(session: Session) {}
}

private class FakeBehaviorRepository : BehaviorRepository {
    val savedStats = mutableListOf<UserIntentStats>()
    val savedProfiles = mutableListOf<DomainBehaviorProfile>()

    override suspend fun getIntentStats(intentId: String) = null
    override suspend fun getAllIntentStats() = emptyList<UserIntentStats>()
    override suspend fun saveIntentStats(stats: UserIntentStats) { savedStats.add(stats) }
    override suspend fun getDomainProfile(domain: Domain) = null
    override suspend fun getAllDomainProfiles() = emptyList<DomainBehaviorProfile>()
    override suspend fun saveDomainProfile(profile: DomainBehaviorProfile) { savedProfiles.add(profile) }
    override suspend fun clearAll() {
        savedStats.clear()
        savedProfiles.clear()
    }
}
