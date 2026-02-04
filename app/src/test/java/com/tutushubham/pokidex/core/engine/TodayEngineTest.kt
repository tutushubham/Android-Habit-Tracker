package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.anchor
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.entity.GoalIntent
import com.tutushubham.pokidex.core.domain.entity.Session
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.repository.AnchorRepository
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.domain.repository.IntentRepository
import com.tutushubham.pokidex.core.domain.repository.SessionRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.goalIntent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodayEngineTest {

    @Test
    fun `generate creates sessions for anchors without existing sessions`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor1 = anchor(id = "anchor-1", block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val anchor2 = anchor(id = "anchor-2", block = DayBlock.DAY, domain = Domain.WORK, defaultMinutes = 120)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 1)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.WORK, title = "Code", startDate = date, endDate = date.plusDays(30), priority = 1)

        val anchorRepo = FakeAnchorRepository(listOf(anchor1, anchor2))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        val focusResolver = FocusResolver(
            FakeFocusRepository(emptyList()),
            FakeDomainFocusConfigRepository(null),
            FakeDailyFocusOverrideRepository()
        )

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(2, plan.sessions.size)
        assertTrue(plan.sessions.any { it.block == DayBlock.MORNING && it.domain == Domain.FITNESS })
        assertTrue(plan.sessions.any { it.block == DayBlock.DAY && it.domain == Domain.WORK })
        plan.sessions.forEach { session ->
            assertEquals(SessionStatus.PLANNED, session.status)
            assertEquals(date, session.date)
        }
    }

    @Test
    fun `generate skips anchors with existing sessions in same block`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor1 = Anchor("anchor-1", DayBlock.MORNING, Domain.FITNESS, 60)
        val anchor2 = Anchor("anchor-2", DayBlock.DAY, Domain.WORK, 120)
        val intent1 = GoalIntent("intent-1", Domain.FITNESS, "Workout", null, date, date.plusDays(30), 1)
        val intent2 = GoalIntent("intent-2", Domain.WORK, "Code", null, date, date.plusDays(30), 1)

        val existingSession = Session.planned(intent1, date, DayBlock.MORNING, 60)

        val anchorRepo = FakeAnchorRepository(listOf(anchor1, anchor2))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf(existingSession))
        val focusResolver = FocusResolver(
            FakeFocusRepository(emptyList()),
            FakeDomainFocusConfigRepository(null),
            FakeDailyFocusOverrideRepository()
        )

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(1, plan.sessions.size)
        assertEquals(DayBlock.DAY, plan.sessions[0].block)
        assertEquals(Domain.WORK, plan.sessions[0].domain)
    }

    @Test
    fun `generate selects intent with lowest priority when multiple intents match domain`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga", startDate = date, endDate = date.plusDays(30), priority = 1) // Lower priority = higher priority

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        val focusResolver = FocusResolver(
            FakeFocusRepository(emptyList()),
            FakeDomainFocusConfigRepository(null),
            FakeDailyFocusOverrideRepository()
        )

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(1, plan.sessions.size)
        assertEquals("intent-2", plan.sessions[0].intentId) // Should select intent with priority 1
    }

    @Test
    fun `generate returns empty plan when no anchors exist`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchorRepo = FakeAnchorRepository(emptyList())
        val intentRepo = FakeIntentRepository(emptyList())
        val sessionRepo = FakeSessionRepository(mutableListOf())
        val focusResolver = FocusResolver(
            FakeFocusRepository(emptyList()),
            FakeDomainFocusConfigRepository(null),
            FakeDailyFocusOverrideRepository()
        )

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertTrue(plan.sessions.isEmpty())
    }

    @Test
    fun `generate returns empty plan when no matching intents exist`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = Anchor("anchor-1", DayBlock.MORNING, Domain.FITNESS, 60)
        val intent = GoalIntent("intent-1", Domain.WORK, "Code", null, date, date.plusDays(30), 1) // Different domain

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        val focusResolver = FocusResolver(
            FakeFocusRepository(emptyList()),
            FakeDomainFocusConfigRepository(null),
            FakeDailyFocusOverrideRepository()
        )

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertTrue(plan.sessions.isEmpty())
    }

    @Test
    fun `generate uses anchor defaultMinutes for planned minutes`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 90)
        val intent = goalIntent(domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 1)

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        val focusResolver = FocusResolver(
            FakeFocusRepository(emptyList()),
            FakeDomainFocusConfigRepository(null),
            FakeDailyFocusOverrideRepository()
        )

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(1, plan.sessions.size)
        assertEquals(90, plan.sessions[0].plannedMinutes)
    }

    @Test
    fun `generate behaves same as before when no focus configured`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga", startDate = date, endDate = date.plusDays(30), priority = 1) // Lower priority = higher priority

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        // No focus configured - resolver returns null
        val focusResolver = FakeFocusResolver(null)

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(1, plan.sessions.size)
        // Should select highest priority intent (priority 1 = intent-2)
        assertEquals("intent-2", plan.sessions[0].intentId)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
    }

    @Test
    fun `generate selects correct intent when focus is configured`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Running workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga session", startDate = date, endDate = date.plusDays(30), priority = 1) // Higher priority but doesn't match focus

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        // Focus configured with name "Running"
        val focus = com.tutushubham.pokidex.core.domain.entity.Focus("focus-1", Domain.FITNESS, "Running", 1, null)
        val focusResolver = FakeFocusResolver(focus)

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(1, plan.sessions.size)
        // Should select intent matching focus name "Running" (intent-1), not the higher priority one
        assertEquals("intent-1", plan.sessions[0].intentId)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
    }

    @Test
    fun `generate creates no session when focus exists but no matching intent`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Yoga session", startDate = date, endDate = date.plusDays(30), priority = 1)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Swimming workout", startDate = date, endDate = date.plusDays(30), priority = 2)

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        // Focus configured with name "Running" but no intents contain "Running"
        val focus = com.tutushubham.pokidex.core.domain.entity.Focus("focus-1", Domain.FITNESS, "Running", 1, null)
        val focusResolver = FakeFocusResolver(focus)

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        // Should create no session because no intent matches the focus name
        assertEquals(0, plan.sessions.size)
    }

    @Test
    fun `generate prioritizes deadline focus over rotation focus`() = runTest {
        // Given
        val date = LocalDate.of(2024, 1, 15)
        val anchor = anchor(block = DayBlock.MORNING, domain = Domain.FITNESS, defaultMinutes = 60)
        val intent1 = goalIntent(id = "intent-1", domain = Domain.FITNESS, title = "Running workout", startDate = date, endDate = date.plusDays(30), priority = 2)
        val intent2 = goalIntent(id = "intent-2", domain = Domain.FITNESS, title = "Yoga session", startDate = date, endDate = date.plusDays(30), priority = 1)

        val anchorRepo = FakeAnchorRepository(listOf(anchor))
        val intentRepo = FakeIntentRepository(listOf(intent1, intent2))
        val sessionRepo = FakeSessionRepository(mutableListOf())
        // Deadline focus with name "Yoga" should override rotation (which would select "Running")
        val deadlineFocus = com.tutushubham.pokidex.core.domain.entity.Focus("focus-deadline", Domain.FITNESS, "Yoga", 1, date.plusDays(5))
        val focusResolver = FakeFocusResolver(deadlineFocus)

        val engine = TodayEngine(intentRepo, sessionRepo, anchorRepo, focusResolver)

        // When
        val plan = engine.generate(date)

        // Then
        assertEquals(1, plan.sessions.size)
        // Should select intent matching deadline focus "Yoga" (intent-2)
        assertEquals("intent-2", plan.sessions[0].intentId)
        assertEquals(Domain.FITNESS, plan.sessions[0].domain)
    }
}

// Fake implementations for testing
class FakeAnchorRepository(
    private val anchors: List<Anchor>
) : AnchorRepository {
    override suspend fun getAllAnchors(): List<Anchor> = anchors
    override suspend fun getAnchorByBlockAndDomain(block: DayBlock, domain: Domain): Anchor? =
        anchors.firstOrNull { it.block == block && it.domain == domain }
    override suspend fun insertAnchor(anchor: Anchor) {}
    override suspend fun updateAnchor(anchor: Anchor) {}
}

class FakeIntentRepository(
    private val intents: List<GoalIntent>
) : IntentRepository {
    override suspend fun getIntentsForDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): List<GoalIntent> =
        intents.filter { it.startDate <= endDate && it.endDate >= startDate }
    override suspend fun insertIntent(intent: GoalIntent) {}
    override suspend fun updateIntent(intent: GoalIntent) {}
    override suspend fun getIntentById(id: String): GoalIntent? = intents.firstOrNull { it.id == id }
}

class FakeSessionRepository(
    private val sessions: MutableList<Session> = mutableListOf()
) : SessionRepository {
    override suspend fun getSessionsForDate(date: java.time.LocalDate): List<Session> =
        sessions.filter { it.date == date }
    override suspend fun insertSession(session: Session) {
        sessions.add(session)
    }
    override suspend fun updateSession(session: Session) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = session
        }
    }
}

class FakeFocusRepository(
    private val focuses: List<com.tutushubham.pokidex.core.domain.entity.Focus>
) : FocusRepository {
    override suspend fun getFocusById(id: String): com.tutushubham.pokidex.core.domain.entity.Focus? =
        focuses.firstOrNull { it.id == id }
    override suspend fun getFocusesByDomain(domain: Domain): List<com.tutushubham.pokidex.core.domain.entity.Focus> =
        focuses.filter { it.domain == domain }
    override suspend fun getAllFocuses(): List<com.tutushubham.pokidex.core.domain.entity.Focus> = focuses
    override suspend fun insertFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
    override suspend fun updateFocus(focus: com.tutushubham.pokidex.core.domain.entity.Focus) {}
    override suspend fun deleteFocus(id: String) {}
}

class FakeDomainFocusConfigRepository(
    private val config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig?
) : DomainFocusConfigRepository {
    override suspend fun getConfig(domain: Domain): com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig? =
        if (config?.domain == domain) config else null
    override suspend fun upsertConfig(config: com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig) {}
}

class FakeDailyFocusOverrideRepository : DailyFocusOverrideRepository {
    override suspend fun getOverride(domain: Domain, date: LocalDate) = null
    override suspend fun setOverride(override: com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride) {}
    override suspend fun clearOverride(domain: Domain, date: LocalDate) {}
}

// Fake FocusResolver for testing - returns a fixed focus or null
// This bypasses the real FocusResolver logic for unit testing TodayEngine's focus integration
class FakeFocusResolver(
    private val focus: com.tutushubham.pokidex.core.domain.entity.Focus?
) : FocusResolver(
    FakeFocusRepository(emptyList()),
    FakeDomainFocusConfigRepository(null),
    FakeDailyFocusOverrideRepository()
) {
    override suspend fun resolve(domain: Domain, date: LocalDate): com.tutushubham.pokidex.core.domain.entity.Focus? {
        return if (focus?.domain == domain) focus else null
    }
}
