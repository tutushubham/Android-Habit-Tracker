package com.habitsheet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The ViewModels with their *own* scope (no test scope injected), as the app runs them: work goes through the
 * ViewModel's background scope and stops when the store is cleared. Main is replaced by a test dispatcher, as for
 * any Android ViewModel test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelLifecycleTest {
    private val today = LocalDate(2026, 8, 26)
    private val dates = object : DateProvider {
        override fun today() = today
        override fun nowEpochMillis() = 1_000L
    }
    private val repository = InMemoryHabitRepository(
        HabitSnapshot(dailyHabits = listOf(DailyHabit("read", "Read", null, 20, 0, true, LocalDate(2020, 1, 1), null, 0, 0))),
    )
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private val noBackup = object : BackupService {
        override val usesClipboard = false
        override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) = Unit
        override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) = Unit
        override fun importBackup(onFailure: (String) -> Unit, onImport: (String) -> Unit) = Unit
    }
    private var replaced = 0
    private val factory = viewModelFactory {
        initializer { MonthViewModel(repository, repository, dates) }
        initializer { ManageHabitsViewModel(repository, DefaultIdGenerator(), dates) }
        initializer { SettingsViewModel(repository) }
        initializer {
            BackupViewModel(repository, noBackup, dates, callbackDispatcher = Dispatchers.Main, onDataReplaced = { replaced++ })
        }
    }
    private val provider = ViewModelProvider.create(owner, factory)

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun cleanUp() {
        owner.viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private suspend fun realTime(block: suspend () -> Unit) = withContext(Dispatchers.Default) { withTimeout(10_000) { block() } }

    @Test
    fun theStoreKeepsOneInstancePerClassAndSeparateStoresDoNotShare() {
        assertSame(provider[MonthViewModel::class], provider[MonthViewModel::class])
        val other = ViewModelProvider.create(object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }, factory)
        assertNotSame(provider[MonthViewModel::class], other[MonthViewModel::class])
    }

    @Test
    fun monthViewModelWorksOnItsOwnScope() = runTest {
        val viewModel = provider[MonthViewModel::class]
        viewModel.toggleDaily("read", today)
        realTime { repository.snapshot.first { s -> s.dailyCompletions.any { it.completed } } }
        realTime { viewModel.state.first { it.dailyCompletionKeys.isNotEmpty() } }
    }

    @Test
    fun clearingTheStoreStopsMonthViewModelWork() = runTest {
        val viewModel = provider[MonthViewModel::class]
        owner.viewModelStore.clear()
        viewModel.toggleDaily("read", today)
        realTime { delay(200) }
        assertTrue(repository.snapshot.value.dailyCompletions.isEmpty(), "a cleared ViewModel must not keep working")
    }

    @Test
    fun clearingTheStoreStopsOtherViewModelsToo() = runTest {
        val manage = provider[ManageHabitsViewModel::class]
        val settings = provider[SettingsViewModel::class]
        realTime { delay(50) } // let the settings load finish before clearing
        owner.viewModelStore.clear()
        manage.addCategory("Fitness")
        settings.setThemeMode(ThemeMode.Dark)
        realTime { delay(200) }
        assertTrue(repository.snapshot.value.categories.isEmpty())
        assertEquals(0, repository.getThemeMode())
    }

    @Test
    fun manageHabitsViewModelWorksOnItsOwnScope() = runTest {
        provider[ManageHabitsViewModel::class].addCategory("Fitness")
        realTime { repository.snapshot.first { s -> s.categories.any { it.name == "Fitness" } } }
    }

    @Test
    fun settingsViewModelWorksOnItsOwnScope() = runTest {
        val settings = provider[SettingsViewModel::class]
        realTime { delay(50) } // the initial load from storage runs first, as in the app
        settings.setThemeMode(ThemeMode.Dark)
        realTime { while (repository.getThemeMode() != ThemeMode.Dark.ordinal) delay(10) }
        assertEquals(ThemeMode.Dark, settings.themeMode.value)
    }

    @Test
    fun backupViewModelReportsADataResetOnItsCallbackDispatcher() = runTest {
        provider[BackupViewModel::class].clearAllData()
        realTime { while (replaced == 0) delay(10) }
        assertEquals(1, replaced)
    }

    @Test
    fun aViewModelIsToldWhenItIsCleared() {
        val probe = ViewModelProvider.create(owner, viewModelFactory { initializer { Probe() } })[Probe::class]
        owner.viewModelStore.clear()
        assertTrue(probe.cleared)
    }
}

private class Probe : ViewModel() {
    var cleared = false
    public override fun onCleared() { cleared = true }
}
