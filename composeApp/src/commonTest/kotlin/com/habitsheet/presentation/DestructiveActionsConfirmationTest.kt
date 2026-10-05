package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.backup.BackupFixtures
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DestructiveActionsConfirmationTest {
    private val day = LocalDate(2026, 8, 5)
    private val run = DailyHabit("run", "Morning Run", "c1", 10, 0, true, LocalDate(2026, 8, 1), null, 1, 1)
    private val gym = WeeklyHabit("gym", "Gym", "c1", 0, true, LocalDate(2026, 8, 1), null, 1, 1)
    private val snapshot = HabitSnapshot(
        categories = listOf(Category("c1", "Fitness", 0, true, 1)),
        dailyHabits = listOf(run),
        dailyCompletions = listOf(
            DailyHabitCompletion("run", day, true, 1),
            DailyHabitCompletion("run", day.plus(1, kotlinx.datetime.DateTimeUnit.DAY), true, 1),
            DailyHabitCompletion("run", day.plus(2, kotlinx.datetime.DateTimeUnit.DAY), false, 1),
        ),
        weeklyHabits = listOf(gym),
        weeklyCompletions = listOf(WeeklyHabitCompletion("gym", day, true, 1)),
        weeklyPlans = listOf(WeeklyPlan("run", 2, "Intervals", 1)),
        dayPlans = listOf(DayPlan("run", day, "Easy 6 km", false, 1)),
    )

    // ---- confirmation text names what is affected -------------------------------------------------

    @Test
    fun deletingAHabitNamesItAndWhatGoesWithIt() {
        val text = DestructiveAction.delete(run, snapshot).confirmation
        assertTrue("'Morning Run'" in text.message)
        assertTrue("2 check-offs" in text.message, text.message)
        assertTrue("2 planned sessions" in text.message, text.message)
        assertTrue("cannot be undone" in text.message && "Archive" in text.message)
        assertEquals("Delete", text.confirmLabel)

        val weekly = DestructiveAction.delete(gym, snapshot).confirmation
        assertTrue("'Gym'" in weekly.message && "1 check-off" in weekly.message, weekly.message)
    }

    @Test
    fun deletingACategoryNamesItAndSaysHabitsStay() {
        val text = DestructiveAction.delete(snapshot.categories.single(), snapshot).confirmation.message
        assertTrue("'Fitness'" in text && "2 habits" in text && "stay" in text, text)
        assertTrue("No habits use it" in DestructiveAction.DeleteCategory("Empty", 0).confirmation.message)
    }

    @Test
    fun removingASessionNamesTheHabitAndTheSession() {
        val daily = DestructiveAction.RemoveDaySession("Morning Run", day, "Easy 6 km").confirmation.message
        assertTrue("Easy 6 km" in daily && "Morning Run" in daily && "2026-08-05" in daily && "kept" in daily, daily)
        val weekly = DestructiveAction.RemoveWeeklySession("Morning Run", "Tuesday", "Intervals").confirmation.message
        assertTrue("Intervals" in weekly && "every Tuesday" in weekly, weekly)
    }

    @Test
    fun resetTextSaysItIsLocalOnlyAndWhatItRemoves() {
        val text = DestructiveAction.ResetAllData(DataSummary.of(snapshot)).confirmation
        assertTrue("deletes 1 daily habit, 1 weekly habit and 3 check-offs on this device" in text.message, text.message)
        assertTrue("Your Google Sheet is untouched" in text.message)
        assertTrue("disconnects the sheet link" in text.message)
        assertTrue("cannot be undone" in text.message)
        assertTrue("no habits or history" in DestructiveAction.ResetAllData(DataSummary(0, 0, 0)).confirmation.message)
    }

    @Test
    fun startingWithEmptyDataSaysTheOldFileIsKeptAndTheSheetUntouched() {
        val text = DestructiveAction.SetAsideDamagedData.confirmation
        assertTrue("moves it aside" in text.message && "kept on the device" in text.message, text.message)
        assertTrue("Google Sheet is untouched" in text.message && "save a copy first" in text.message, text.message)
        assertEquals("Start with empty data", text.confirmLabel)
    }

    @Test
    fun importTextSaysWhatWillBeReplacedAndThatTheSheetIsUntouched() {
        val text = DestructiveAction.ReplaceWithBackup(DataSummary.of(snapshot)).confirmation.message
        assertTrue("replaces 1 daily habit, 1 weekly habit and 3 check-offs" in text, text)
        assertTrue("Google Sheet is untouched" in text && "cannot be undone" in text, text)
    }

    // ---- reset: sheet link and sync state -----------------------------------------------------------

    @Test
    fun resetClearsTheSheetLinkAndAllSyncStateButKeepsTheTheme() = runTest {
        val repo = InMemoryHabitRepository(snapshot)
        repo.setThemeMode(2)
        repo.setSheetUrl("https://docs.google.com/spreadsheets/d/mine/edit")
        repo.applySheetSync(SheetSyncChanges(managedHabitIds = setOf("run")), setOf("run-1"), 99)
        repo.setDailyCompletion(DailyHabitCompletion("run", day.plus(3, kotlinx.datetime.DateTimeUnit.DAY), true, 5))
        assertTrue(repo.snapshot.value.pendingCompletions.isNotEmpty())

        repo.clearAllData()

        assertEquals("", repo.getSheetUrl())
        assertEquals(emptySet(), repo.getSheetSyncedKeys())
        assertEquals(0L, repo.getSheetLastSync())
        assertEquals(HabitSnapshot(), repo.snapshot.value)
        assertFalse(repo.isOnboardingCompleted())
        assertEquals(2, repo.getThemeMode())
    }

    // ---- the app is told when data was replaced (settings re-read link/theme/sync status) ----------

    private class FakeBackupService(private val payload: String? = null) : BackupService {
        override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) = Unit
        override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) = Unit
        override fun importBackup(onImport: (String) -> Unit, onFailure: (String) -> Unit) {
            payload?.let(onImport)
        }
    }

    @Test
    fun resetAndSuccessfulImportNotifyButFailedImportDoesNot() = runTest {
        var notified = 0
        fun viewModel(repo: InMemoryHabitRepository, payload: String?) = BackupViewModel(
            repo,
            FakeBackupService(payload),
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
            onDataReplaced = { notified++ },
        )

        viewModel(InMemoryHabitRepository(snapshot), null).clearAllData()
        runCurrent()
        assertEquals(1, notified)

        viewModel(InMemoryHabitRepository(), BackupFixtures.V4).importBackup({}, { error(it) })
        runCurrent()
        assertEquals(2, notified)

        viewModel(InMemoryHabitRepository(snapshot), "not json").importBackup({}, {})
        runCurrent()
        assertEquals(2, notified)
    }

    @Test
    fun viewModelSummaryMatchesTheRepository() = runTest {
        val viewModel = BackupViewModel(InMemoryHabitRepository(snapshot), FakeBackupService(), scope = backgroundScope)
        assertEquals(DataSummary(1, 1, 3), viewModel.currentDataSummary())
        assertEquals(snapshot.dailyHabits.size, BackupSerializer.deserialize(BackupFixtures.V3).dailyHabits.size)
    }

    @Test
    fun settingsReloadPicksUpTheClearedLink() = runTest {
        val repo = InMemoryHabitRepository(snapshot)
        repo.setSheetUrl("https://docs.google.com/spreadsheets/d/mine/edit")
        val settings = SettingsViewModel(repo, backgroundScope, UnconfinedTestDispatcher(testScheduler))
        runCurrent()
        assertEquals("https://docs.google.com/spreadsheets/d/mine/edit", settings.sheetUrl.value)

        repo.clearAllData()
        settings.reloadFromStorage()
        runCurrent()

        assertEquals("", settings.sheetUrl.value)
    }
}
