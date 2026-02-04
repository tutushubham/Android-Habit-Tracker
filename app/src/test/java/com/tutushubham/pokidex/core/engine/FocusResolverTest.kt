package com.tutushubham.pokidex.core.engine

import com.tutushubham.pokidex.core.domain.entity.DailyFocusOverride
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.repository.DailyFocusOverrideRepository
import com.tutushubham.pokidex.core.domain.repository.DomainFocusConfigRepository
import com.tutushubham.pokidex.core.domain.repository.FocusRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.LocalDate

@RunWith(JUnit4::class)
class FocusResolverTest {

    @Test
    fun `daily override wins over all strategies`() = runTest {
        val today = LocalDate.of(2024, 6, 10)

        val dsa = focus("DSA", Domain.STUDIES)
        val android = focus("Android", Domain.STUDIES)

        val resolver = FocusResolver(
            focusRepository = FakeFocusRepositoryForResolver(listOf(dsa, android)),
            configRepository = FakeDomainFocusConfigRepositoryForResolver(
                DomainFocusConfig(
                    domain = Domain.STUDIES,
                    strategy = FocusStrategy.Rotation(listOf("DSA", "Android")),
                    manualOverrideFocusId = null,
                    createdAt = today.minusDays(10)
                )
            ),
            overrideRepository = FakeDailyFocusOverrideRepositoryForResolver(
                mutableListOf(
                    DailyFocusOverride(
                        domain = Domain.STUDIES,
                        date = today,
                        focusId = "Android"
                    )
                )
            )
        )

        val result = resolver.resolve(Domain.STUDIES, today)

        assertEquals("Android", result?.id)
    }

    @Test
    fun `invalid override falls back to strategy`() = runTest {
        val today = LocalDate.of(2024, 6, 10)

        val dsa = focus("DSA", Domain.STUDIES)
        val android = focus("Android", Domain.STUDIES)

        val resolver = FocusResolver(
            FakeFocusRepository(listOf(dsa, android)),
            FakeDomainFocusConfigRepository(
                DomainFocusConfig(
                    domain = Domain.STUDIES,
                    strategy = FocusStrategy.Manual,
                    manualOverrideFocusId = "DSA",
                    createdAt = today.minusDays(5)
                )
            ),
            FakeDailyFocusOverrideRepositoryForResolver(
                mutableListOf(
                    DailyFocusOverride(
                        domain = Domain.STUDIES,
                        date = today,
                        focusId = "NON_EXISTENT"
                    )
                )
            )
        )

        val result = resolver.resolve(Domain.STUDIES, today)

        assertEquals("DSA", result?.id)
    }

    @Test
    fun `deadline driven focus wins when active`() = runTest {
        val today = LocalDate.of(2024, 6, 10)

        val exam = focus(
            id = "ExamPrep",
            domain = Domain.STUDIES,
            deadline = today.plusDays(3)
        )
        val android = focus("Android", Domain.STUDIES)

        val resolver = FocusResolver(
            FakeFocusRepositoryForResolver(listOf(exam, android)),
            FakeDomainFocusConfigRepositoryForResolver(
                DomainFocusConfig(
                    domain = Domain.STUDIES,
                    strategy = FocusStrategy.DeadlineDriven,
                    manualOverrideFocusId = null,
                    createdAt = today.minusDays(20)
                )
            ),
            FakeDailyFocusOverrideRepositoryForResolver()
        )

        val result = resolver.resolve(Domain.STUDIES, today)

        assertEquals("ExamPrep", result?.id)
    }

    @Test
    fun `rotation strategy rotates deterministically`() = runTest {
        val start = LocalDate.of(2024, 6, 1)
        val date = start.plusDays(1)

        val dsa = focus("DSA", Domain.STUDIES)
        val android = focus("Android", Domain.STUDIES)

        val resolver = FocusResolver(
            FakeFocusRepositoryForResolver(listOf(dsa, android)),
            FakeDomainFocusConfigRepositoryForResolver(
                DomainFocusConfig(
                    domain = Domain.STUDIES,
                    strategy = FocusStrategy.Rotation(listOf("DSA", "Android")),
                    manualOverrideFocusId = null,
                    createdAt = start
                )
            ),
            FakeDailyFocusOverrideRepositoryForResolver()
        )

        val result = resolver.resolve(Domain.STUDIES, date)

        assertEquals("Android", result?.id)
    }

    @Test
    fun `weighted strategy respects weight distribution`() = runTest {
        val start = LocalDate.of(2024, 6, 1)
        val date = start.plusDays(3) // 3rd index

        val dsa = focus("DSA", Domain.STUDIES, weight = 3)
        val android = focus("Android", Domain.STUDIES, weight = 1)

        val resolver = FocusResolver(
            FakeFocusRepositoryForResolver(listOf(dsa, android)),
            FakeDomainFocusConfigRepositoryForResolver(
                DomainFocusConfig(
                    domain = Domain.STUDIES,
                    strategy = FocusStrategy.Weighted(
                        mapOf("DSA" to 3, "Android" to 1)
                    ),
                    manualOverrideFocusId = null,
                    createdAt = start
                )
            ),
            FakeDailyFocusOverrideRepositoryForResolver()
        )

        val result = resolver.resolve(Domain.STUDIES, date)

        assertEquals("Android", result?.id)
    }

    @Test
    fun `no config falls back to first focus`() = runTest {
        val today = LocalDate.of(2024, 6, 10)

        val dsa = focus("DSA", Domain.STUDIES)
        val resolver = FocusResolver(
            FakeFocusRepositoryForResolver(listOf(dsa)),
            FakeDomainFocusConfigRepositoryForResolver(null),
            FakeDailyFocusOverrideRepositoryForResolver()
        )

        val result = resolver.resolve(Domain.STUDIES, today)

        assertEquals("DSA", result?.id)
    }
}

// Fakes (named ForResolver to avoid redeclaration with TodayEngineTest fakes)
class FakeFocusRepositoryForResolver(
    private val focuses: List<Focus>
) : FocusRepository {

    override suspend fun getFocusById(id: String): Focus? =
        focuses.firstOrNull { it.id == id }

    override suspend fun getFocusesByDomain(domain: Domain): List<Focus> =
        focuses.filter { it.domain == domain }

    override suspend fun getAllFocuses(): List<Focus> = focuses

    override suspend fun insertFocus(focus: Focus) {}
    override suspend fun updateFocus(focus: Focus) {}
    override suspend fun deleteFocus(id: String) {}
}

class FakeDomainFocusConfigRepositoryForResolver(
    private val config: DomainFocusConfig?
) : DomainFocusConfigRepository {

    override suspend fun getConfig(domain: Domain): DomainFocusConfig? = config
    override suspend fun upsertConfig(config: DomainFocusConfig) {}
}

class FakeDailyFocusOverrideRepositoryForResolver(
    private val overrides: MutableList<DailyFocusOverride> = mutableListOf()
) : DailyFocusOverrideRepository {

    override suspend fun getOverride(domain: Domain, date: LocalDate): DailyFocusOverride? =
        overrides.firstOrNull { it.domain == domain && it.date == date }

    override suspend fun setOverride(override: DailyFocusOverride) {
        overrides.removeIf { it.domain == override.domain && it.date == override.date }
        overrides.add(override)
    }

    override suspend fun clearOverride(domain: Domain, date: LocalDate) {
        overrides.removeIf { it.domain == domain && it.date == date }
    }
}

// Test data helpers
private fun focus(
    id: String,
    domain: Domain,
    deadline: LocalDate? = null,
    weight: Int = 1
) = Focus(
    id = id,
    domain = domain,
    name = id,
    deadline = deadline,
    weight = weight
)
