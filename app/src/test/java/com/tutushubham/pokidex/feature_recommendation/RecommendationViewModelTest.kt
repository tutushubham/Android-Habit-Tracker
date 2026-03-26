package com.tutushubham.pokidex.feature_recommendation

import com.tutushubham.pokidex.core.domain.entity.DomainBehaviorProfile
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.entity.UserIntentStats
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.repository.BehaviorRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.domain.usecase.BehaviorProfileUseCase
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.core.engine.RecommendationAction
import com.tutushubham.pokidex.core.engine.TodayPlan
import com.tutushubham.pokidex.core.goalIntent
import com.tutushubham.pokidex.core.session
import com.tutushubham.pokidex.feature_today.FakeTodayPlannerUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val today: LocalDate get() = LocalDate.now()

    private class FakeSessionRepository(
        private val sessions: List<Session> = emptyList()
    ) : SessionRepository {
        override suspend fun getSessionsForDate(date: LocalDate) = sessions.filter { it.date == date }
        override suspend fun getCompletedUnitsForIntent(intentId: String) = 0
        override suspend fun getDistinctDaysWorkedForIntent(intentId: String) = 0
        override suspend fun getTotalActualMinutesForIntent(intentId: String) = 0
        override suspend fun getSkippedSessionCountForIntent(intentId: String) = 0
        override suspend fun getRecentSessions(cutoffDate: LocalDate) =
            sessions.filter { it.date >= cutoffDate }
        override suspend fun insertSession(session: Session) {}
        override suspend fun updateSession(session: Session) {}
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

    private class MutableIntentRepository(
        private val intents: MutableList<GoalIntent> = mutableListOf(),
        private val gate: CompletableDeferred<Unit>? = null
    ) : IntentRepository {
        override suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate) =
            intents.filter { it.endDate >= startDate && it.startDate <= endDate }

        override suspend fun insertIntent(intent: GoalIntent) {
            intents.add(intent)
        }

        override suspend fun updateIntent(intent: GoalIntent) {
            val i = intents.indexOfFirst { it.id == intent.id }
            if (i >= 0) intents[i] = intent
        }

        override suspend fun getIntentById(id: String): GoalIntent? {
            gate?.await()
            return intents.firstOrNull { it.id == id }
        }
    }

    private fun sampleProgress(intentId: String) = IntentProgress(
        intentId = intentId,
        title = "Test",
        domain = Domain.STUDIES,
        targetCount = 100,
        completedUnits = 20,
        remainingUnits = 80,
        daysRemaining = 10,
        requiredUnitsPerDay = 8.0,
        currentPace = 5.0,
        isBehind = true
    )

    private fun vm(
        intentRepo: IntentRepository,
        progressList: List<IntentProgress> = emptyList()
    ): RecommendationViewModel {
        val behaviorUseCase = BehaviorProfileUseCase(
            FakeSessionRepository(
                listOf(
                    session(
                        id = "s1",
                        intentId = "intent-1",
                        domain = Domain.STUDIES,
                        date = today.minusDays(1),
                        status = SessionStatus.COMPLETED,
                        actualMinutes = 30
                    )
                )
            ),
            intentRepo,
            FakeBehaviorRepository()
        )
        val planner = FakeTodayPlannerUseCase(TodayPlan(emptyList(), false, false))
        return RecommendationViewModel(intentRepo, behaviorUseCase, planner, progressList)
    }

    @Test
    fun `screenOpened loads recommendations for given intent`() = runTest {
        val intent = goalIntent(
            id = "intent-1",
            domain = Domain.STUDIES,
            startDate = today.minusDays(10),
            endDate = today.plusDays(20)
        )
        val intentRepo = MutableIntentRepository(mutableListOf(intent))
        val progress = sampleProgress("intent-1")
        val viewModel = vm(intentRepo, listOf(progress))

        viewModel.onEvent(RecommendationContract.RecommendationEvent.ScreenOpened("intent-1"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("intent-1", state.intentId)
        assertEquals(intent.title, state.goalTitle)
        assertEquals(5.0, state.actualPace, 0.001)
        assertEquals(8.0, state.requiredPace, 0.001)
        assertTrue(state.recommendations.isNotEmpty())
    }

    @Test
    fun `action selected triggers replan and navigates back`() = runTest {
        val intent = goalIntent(
            id = "intent-1",
            domain = Domain.STUDIES,
            startDate = today.minusDays(10),
            endDate = today.plusDays(20)
        )
        val intentRepo = MutableIntentRepository(mutableListOf(intent))
        val viewModel = vm(intentRepo)

        viewModel.onEvent(RecommendationContract.RecommendationEvent.ScreenOpened("intent-1"))
        advanceUntilIdle()

        viewModel.onEvent(
            RecommendationContract.RecommendationEvent.ActionSelected(RecommendationAction.TakeBreak)
        )
        advanceUntilIdle()

        assertEquals(
            RecommendationContract.RecommendationEffect.ShowMessage("Break scheduled"),
            viewModel.effect.first()
        )
        assertEquals(
            RecommendationContract.RecommendationEffect.NavigateBack,
            viewModel.effect.first()
        )
    }

    @Test
    fun `loading state shown while fetching`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val intent = goalIntent(
            id = "intent-1",
            domain = Domain.STUDIES,
            startDate = today.minusDays(10),
            endDate = today.plusDays(20)
        )
        val intentRepo = MutableIntentRepository(mutableListOf(intent), gate)
        val viewModel = vm(intentRepo, listOf(sampleProgress("intent-1")))

        viewModel.onEvent(RecommendationContract.RecommendationEvent.ScreenOpened("intent-1"))
        testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.goalTitle)
    }

    @Test
    fun `error state set on failure`() = runTest {
        val intentRepo = MutableIntentRepository(mutableListOf())
        val viewModel = vm(intentRepo)

        viewModel.onEvent(RecommendationContract.RecommendationEvent.ScreenOpened("missing"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }
}
