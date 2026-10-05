package com.habitsheet.presentation

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.repository.BackupStore
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.domain.repository.HabitStore
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Each consumer is built from a wrapper that exposes exactly one narrow store, so this file stops compiling if a
 * ViewModel (or sync) starts depending on more of the repository than its role needs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NarrowStoresTest {
    private class OnlyHabits(repository: HabitRepository) : HabitStore by repository
    private class OnlySettings(repository: HabitRepository) : SettingsStore by repository
    private class OnlyBackups(repository: HabitRepository) : BackupStore by repository

    private val day = LocalDate(2026, 10, 5)
    private val dates = object : DateProvider {
        override fun today() = day
        override fun nowEpochMillis() = 1_000L
    }

    @Test
    fun manageHabitsNeedsOnlyTheHabitStore() = runTest {
        val repository = InMemoryHabitRepository()
        val viewModel = ManageHabitsViewModel(OnlyHabits(repository), DefaultIdGenerator(), dates, backgroundScope)

        viewModel.saveCategory("c1", "Health", 0)
        runCurrent()

        assertEquals(listOf("Health"), repository.snapshot.value.categories.map { it.name })
    }

    @Test
    fun settingsNeedOnlyTheSettingsStore() = runTest {
        val repository = InMemoryHabitRepository()
        val viewModel = SettingsViewModel(OnlySettings(repository), backgroundScope, UnconfinedTestDispatcher(testScheduler))

        viewModel.setThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark.ordinal, repository.getThemeMode())
    }

    @Test
    fun monthNeedsTheHabitStoreForDataAndTheSettingsStoreForTheTutorial() = runTest {
        val repository = InMemoryHabitRepository(
            HabitSnapshot(dailyHabits = listOf(DailyHabit("read", "Read", null, 10, 0, true, day, null, 1, 1))),
        )
        val viewModel = MonthViewModel(OnlyHabits(repository), OnlySettings(repository), dates, backgroundScope)

        viewModel.toggleDaily("read", day)
        viewModel.completeOnboarding()
        runCurrent()

        assertTrue(repository.snapshot.value.dailyCompletions.single().completed)
        assertTrue(repository.isOnboardingCompleted())
    }

    @Test
    fun backupNeedsOnlyTheBackupStore() = runTest {
        val repository = InMemoryHabitRepository(HabitSnapshot(categories = listOf(Category("c1", "Health", 0, true, 1))))
        repository.setThemeMode(2)
        var exported: String? = null
        val service = object : BackupService {
            override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) {
                exported = json
            }
            override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) = Unit
            override fun importBackup(onImport: (String) -> Unit, onFailure: (String) -> Unit) = Unit
        }
        val viewModel = BackupViewModel(
            OnlyBackups(repository),
            service,
            dates,
            backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.exportBackup()
        runCurrent()
        assertEquals(2, BackupSerializer.parse(assertNotNull(exported)).settings?.themeMode)

        viewModel.clearAllData()
        runCurrent()
        assertTrue(repository.snapshot.value.categories.isEmpty())
    }

    @Test
    fun sheetSyncNeedsTheHabitStoreAndTheSettingsStore() = runTest {
        val repository = InMemoryHabitRepository()
        val noToken = object : SheetTokenProvider {
            override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion(null, null)
        }
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        val sync = SheetSync(OnlyHabits(repository), OnlySettings(repository), noToken, client, dates)

        sync.sync(interactive = true)

        assertEquals("Add a spreadsheet link first.", sync.state.value.message)
    }
}
