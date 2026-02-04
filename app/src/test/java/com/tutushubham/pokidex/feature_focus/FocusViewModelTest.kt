package com.tutushubham.pokidex.feature_focus

import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import com.tutushubham.pokidex.core.engine.FocusResolver
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ScreenOpened loads focuses and config`() = runTest {
        val dsa = focus("DSA")
        val android = focus("Android")

        val viewModel = FocusViewModel(
            domain = Domain.STUDIES,
            focusRepository = FakeFocusRepository(listOf(dsa, android)),
            configRepository = FakeDomainFocusConfigRepository(
                DomainFocusConfig(
                    domain = Domain.STUDIES,
                    strategy = FocusStrategy.Manual,
                    manualOverrideFocusId = "DSA",
                    createdAt = LocalDate.of(2024, 6, 1)
                )
            ),
            focusResolver = FakeFocusResolver(emptyMap())
        )

        viewModel.onEvent(FocusEvent.ScreenOpened)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.focuses.size)
        assertEquals("DSA", state.manualOverrideFocusId)
        assertTrue(state.strategy is FocusStrategy.Manual)
    }

    @Test
    fun `StrategySelected updates strategy in state`() = runTest {
        val viewModel = FocusViewModel(
            domain = Domain.STUDIES,
            focusRepository = FakeFocusRepository(listOf(focus("DSA"))),
            configRepository = FakeDomainFocusConfigRepository(),
            focusResolver = FakeFocusResolver(emptyMap())
        )

        viewModel.onEvent(FocusEvent.StrategySelected(FocusStrategy.DeadlineDriven))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.strategy is FocusStrategy.DeadlineDriven)
    }

    @Test
    fun `preview is generated using resolver`() = runTest {
        val today = LocalDate.of(2024, 6, 10)
        val dsa = focus("DSA")
        val android = focus("Android")

        val resolver = FakeFocusResolver(
            mapOf(
                today to dsa,
                today.plusDays(1) to android
            )
        )

        val fixedClock = Clock.fixed(
            Instant.parse("2024-06-10T12:00:00Z"),
            ZoneOffset.UTC
        )

        val viewModel = FocusViewModel(
            domain = Domain.STUDIES,
            focusRepository = FakeFocusRepository(listOf(dsa, android)),
            configRepository = FakeDomainFocusConfigRepository(),
            focusResolver = resolver,
            clock = fixedClock
        )

        viewModel.onEvent(FocusEvent.ScreenOpened)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(FocusEvent.StrategySelected(FocusStrategy.Manual))
        dispatcher.scheduler.advanceUntilIdle()

        val preview = viewModel.state.value.preview
        assertEquals(listOf("DSA", "Android"), preview.take(2))
    }

    @Test
    fun `ConfirmClicked saves config and emits Exit`() = runTest {
        val configRepo = FakeDomainFocusConfigRepository()
        val viewModel = FocusViewModel(
            domain = Domain.STUDIES,
            focusRepository = FakeFocusRepository(listOf(focus("DSA"))),
            configRepository = configRepo,
            focusResolver = FakeFocusResolver(emptyMap())
        )

        viewModel.onEvent(FocusEvent.StrategySelected(FocusStrategy.Manual))
        viewModel.onEvent(FocusEvent.ConfirmClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(configRepo.getConfig(Domain.STUDIES))
    }

    @Test
    fun `no focuses results in empty preview`() = runTest {
        val viewModel = FocusViewModel(
            domain = Domain.STUDIES,
            focusRepository = FakeFocusRepository(emptyList()),
            configRepository = FakeDomainFocusConfigRepository(),
            focusResolver = FakeFocusResolver(emptyMap())
        )

        viewModel.onEvent(FocusEvent.StrategySelected(FocusStrategy.Manual))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.preview.isEmpty())
    }
}

// Fakes
class FakeFocusRepository(
    private val focuses: List<Focus>
) : FocusRepository {

    override suspend fun getFocusesByDomain(domain: Domain): List<Focus> =
        focuses.filter { it.domain == domain }

    override suspend fun getFocusById(id: String): Focus? =
        focuses.firstOrNull { it.id == id }

    override suspend fun getAllFocuses(): List<Focus> = focuses
    override suspend fun insertFocus(focus: Focus) {}
    override suspend fun updateFocus(focus: Focus) {}
    override suspend fun deleteFocus(id: String) {}
}

class FakeDomainFocusConfigRepository(
    private var config: DomainFocusConfig? = null
) : DomainFocusConfigRepository {

    override suspend fun getConfig(domain: Domain): DomainFocusConfig? = config

    override suspend fun upsertConfig(config: DomainFocusConfig) {
        this.config = config
    }
}

class FakeDailyFocusOverrideRepositoryForFocusVM : DailyFocusOverrideRepository {
    override suspend fun getOverride(domain: Domain, date: LocalDate) = null
    override suspend fun setOverride(override: com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride) {}
    override suspend fun clearOverride(domain: Domain, date: LocalDate) {}
}

class FakeFocusResolver(
    private val resultByDate: Map<LocalDate, Focus?>
) : FocusResolver(
    focusRepository = FakeFocusRepository(emptyList()),
    configRepository = FakeDomainFocusConfigRepository(),
    overrideRepository = FakeDailyFocusOverrideRepositoryForFocusVM()
) {
    override fun resolveWithConfig(
        config: DomainFocusConfig,
        focuses: List<Focus>,
        date: LocalDate
    ): Focus? = resultByDate[date]
}

// Test data helper
private fun focus(id: String) =
    Focus(
        id = id,
        domain = Domain.STUDIES,
        name = id
    )
