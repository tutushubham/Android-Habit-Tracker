package com.tutushubham.pokidex.feature_today

import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.engine.TodayEngine
import com.tutushubham.pokidex.core.engine.TodayPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ScreenOpened loads today plan into state`() = runTest {
        val session = sampleSession()
        val engine = FakeTodayEngine(
            TodayPlan(listOf(session))
        )
        val repo = FakeSessionRepository()

        val vm = TodayViewModel(
            engine,
            repo,
            fakeFocusRepository(),
            fakeDailyFocusOverrideRepository(),
            fakeFocusResolver()
        )

        vm.onEvent(TodayContract.TodayEvent.ScreenOpened)
        advanceUntilIdle()

        val state = vm.state.value

        assertFalse(state.isLoading)
        assertEquals(1, state.sessions.size)
        assertEquals("s1", state.sessions.first().id)
    }

    @Test
    fun `StartSession sets active session and emits StartSessionTimer`() = runTest {
        val session = sampleSession()
        val engine = FakeTodayEngine(TodayPlan(listOf(session)))
        val repo = FakeSessionRepository()

        val vm = TodayViewModel(
            engine,
            repo,
            fakeFocusRepository(),
            fakeDailyFocusOverrideRepository(),
            fakeFocusResolver()
        )

        vm.onEvent(TodayContract.TodayEvent.ScreenOpened)
        advanceUntilIdle()

        vm.onEvent(TodayContract.TodayEvent.StartSession("s1"))
        advanceUntilIdle()

        val effect = vm.effect.first()
        assertEquals(
            TodayContract.TodayEffect.StartSessionTimer("s1"),
            effect
        )

        val state = vm.state.value
        assertEquals("s1", state.activeSessionId)
    }

    @Test
    fun `SkipSession updates session and persists change`() = runTest {
        val session = sampleSession()
        val engine = FakeTodayEngine(TodayPlan(listOf(session)))
        val repo = FakeSessionRepository(listOf(session))

        val vm = TodayViewModel(
            engine,
            repo,
            fakeFocusRepository(),
            fakeDailyFocusOverrideRepository(),
            fakeFocusResolver()
        )

        vm.onEvent(TodayContract.TodayEvent.ScreenOpened)
        advanceUntilIdle()

        vm.onEvent(
            TodayContract.TodayEvent.SkipSession(
                sessionId = "s1",
                reason = SkipReason.LOW_ENERGY
            )
        )
        advanceUntilIdle()

        val effect = vm.effect.first()
        assertTrue(effect is TodayContract.TodayEffect.ShowMessage)

        val updated = vm.state.value.sessions.first()
        assertEquals(SessionStatus.SKIPPED, updated.status)
        assertEquals(SkipReason.LOW_ENERGY, updated.skipReason)

        assertEquals(1, repo.updatedSessions.size)
    }

    @Test
    fun `CompleteSession marks completed and emits StopSessionTimer`() = runTest {
        val session = sampleSession(status = SessionStatus.PLANNED)
        val engine = FakeTodayEngine(TodayPlan(listOf(session)))
        val repo = FakeSessionRepository(listOf(session))

        val vm = TodayViewModel(
            engine,
            repo,
            fakeFocusRepository(),
            fakeDailyFocusOverrideRepository(),
            fakeFocusResolver()
        )

        vm.onEvent(TodayContract.TodayEvent.ScreenOpened)
        advanceUntilIdle()
        
        // Consume the StartSessionTimer effect from starting the session
        vm.onEvent(TodayContract.TodayEvent.StartSession("s1"))
        advanceUntilIdle()
        vm.effect.first() // Consume StartSessionTimer effect

        vm.onEvent(
            TodayContract.TodayEvent.CompleteSession(
                sessionId = "s1",
                actualMinutes = 50
            )
        )
        advanceUntilIdle()

        val effect = vm.effect.first()
        assertEquals(TodayContract.TodayEffect.StopSessionTimer, effect)

        val updated = vm.state.value.sessions.first()
        assertEquals(SessionStatus.COMPLETED, updated.status)
        assertEquals(50, updated.actualMinutes)
        assertNull(vm.state.value.activeSessionId)
    }
}

// Fake implementations for testing

// Fake TodayEngine for testing - returns a fixed plan
class FakeTodayEngine(
    private val plan: TodayPlan
) : com.tutushubham.pokidex.core.engine.TodayEngine(
    intentRepository = object : com.tutushubham.pokidex.core.domain.repository.IntentRepository {
        override suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate) = emptyList<com.tutushubham.pokidex.core.domain.entity.GoalIntent>()
        override suspend fun insertIntent(intent: com.tutushubham.pokidex.core.domain.entity.GoalIntent) {}
        override suspend fun updateIntent(intent: com.tutushubham.pokidex.core.domain.entity.GoalIntent) {}
        override suspend fun getIntentById(id: String) = null
    },
    sessionRepository = object : SessionRepository {
        override suspend fun getSessionsForDate(date: LocalDate) = emptyList<Session>()
        override suspend fun insertSession(session: Session) {}
        override suspend fun updateSession(session: Session) {}
    },
    anchorRepository = object : com.tutushubham.pokidex.core.domain.repository.AnchorRepository {
        override suspend fun getAllAnchors() = emptyList<com.tutushubham.pokidex.core.domain.entity.Anchor>()
        override suspend fun getAnchorByBlockAndDomain(block: DayBlock, domain: Domain) = null
        override suspend fun insertAnchor(anchor: com.tutushubham.pokidex.core.domain.entity.Anchor) {}
        override suspend fun updateAnchor(anchor: com.tutushubham.pokidex.core.domain.entity.Anchor) {}
    },
    focusResolver = com.tutushubham.pokidex.core.engine.FocusResolver(
        object : com.tutushubham.pokidex.core.domain.repository.FocusRepository {
            override suspend fun getFocusById(id: String) = null
            override suspend fun getFocusesByDomain(domain: Domain) = emptyList<com.tutushubham.pokidex.core.domain.entity.Focus>()
            override suspend fun getAllFocuses() = emptyList<com.tutushubham.pokidex.core.domain.entity.Focus>()
            override suspend fun insertFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
            override suspend fun updateFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
            override suspend fun deleteFocus(id: String) {}
        },
        object : com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository {
            override suspend fun getConfig(domain: Domain) = null
            override suspend fun upsertConfig(config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig) {}
        },
        object : com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository {
            override suspend fun getOverride(domain: Domain, date: LocalDate) = null
            override suspend fun setOverride(override: com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride) {}
            override suspend fun clearOverride(domain: Domain, date: LocalDate) {}
        }
    )
) {
    override suspend fun generate(date: LocalDate): TodayPlan {
        return plan
    }
}

class FakeSessionRepository(
    initialSessions: List<Session> = emptyList()
) : SessionRepository {

    val updatedSessions = mutableListOf<Session>()

    private val sessions = initialSessions.toMutableList()

    override suspend fun getSessionsForDate(date: LocalDate): List<Session> {
        return sessions.filter { it.date == date }
    }

    override suspend fun insertSession(session: Session) {
        sessions.add(session)
    }

    override suspend fun updateSession(session: Session) {
        updatedSessions.add(session)
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) sessions[index] = session
    }
}

private fun fakeFocusRepository(): FocusRepository = object : FocusRepository {
    override suspend fun getFocusById(id: String) = null
    override suspend fun getFocusesByDomain(domain: Domain): List<com.tutushubham.pokidex.core.domain.entity.Focus> = emptyList()
    override suspend fun getAllFocuses() = emptyList<com.tutushubham.pokidex.core.domain.entity.Focus>()
    override suspend fun insertFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
    override suspend fun updateFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
    override suspend fun deleteFocus(id: String) {}
}

private fun fakeDailyFocusOverrideRepository(): DailyFocusOverrideRepository = object : DailyFocusOverrideRepository {
    override suspend fun getOverride(domain: Domain, date: LocalDate) = null
    override suspend fun setOverride(override: com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride) {}
    override suspend fun clearOverride(domain: Domain, date: LocalDate) {}
}

private fun fakeFocusResolver(): FocusResolver = FocusResolver(
    fakeFocusRepository(),
    object : DomainFocusConfigRepository {
        override suspend fun getConfig(domain: Domain) = null
        override suspend fun upsertConfig(config: DomainFocusConfig) {}
    },
    fakeDailyFocusOverrideRepository()
)

private fun sampleSession(
    id: String = "s1",
    status: SessionStatus = SessionStatus.PLANNED
) = Session(
    id = id,
    intentId = "intent-1",
    domain = Domain.FITNESS,
    date = LocalDate.of(2024, 1, 15),
    block = DayBlock.MORNING,
    plannedMinutes = 60,
    actualMinutes = null,
    status = status,
    skipReason = null,
    startedAt = null,
    endedAt = null
)
