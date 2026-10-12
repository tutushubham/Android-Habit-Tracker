package com.habitsheet.presentation

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.platform.LogLevel
import com.habitsheet.testing.English
import com.habitsheet.testing.FailingHabitStore
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.FakeLogger
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the person is told when storage fails or a limit is hit, for the habit-editing and settings ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorSurfacesTest {
    private val today = LocalDate(2026, 10, 12)
    private val dates = FakeDateProvider(today = today, nowMillis = 5_000L)
    private val logger = FakeLogger()

    private val snapshot = buildSnapshot(stamp = 1) {
        category("c1", "Fitness")
        dailyHabit("run", "Run", category = "c1", createdOn = LocalDate(2026, 9, 1))
        dailyHabit("read", "Read", createdOn = LocalDate(2026, 9, 1))
        weeklyHabit("gym", createdOn = LocalDate(2026, 9, 1))
    }

    private data class Fixture(val viewModel: ManageHabitsViewModel, val repository: InMemoryHabitRepository, val store: FailingHabitStore)

    private fun TestScope.manage(initial: HabitSnapshot = snapshot, failing: Array<String> = emptyArray()): Fixture {
        val repository = InMemoryHabitRepository(initial)
        val store = FailingHabitStore(repository, *failing)
        val viewModel = ManageHabitsViewModel(store, DefaultIdGenerator(), dates, backgroundScope, logger)
        return Fixture(viewModel, repository, store)
    }

    private fun ManageHabitsViewModel.message() = English.render(assertNotNull(error.value))

    private val run = snapshot.dailyHabits.first { it.id == "run" }
    private val gym = snapshot.weeklyHabits.first()

    @Test
    fun everyFailingHabitOperationHasItsOwnMessageAndTheDataIsUntouched() = runTest {
        val cases = listOf<Triple<String, String, (ManageHabitsViewModel) -> Unit>>(
            Triple("saveCategory", "Couldn't save category.") { it.addCategory("Study") },
            Triple("deleteCategory", "Couldn't delete category.") { it.deleteCategory("c1") },
            Triple("saveDailyHabit", "Couldn't create habit.") { it.addDailyHabit("Yoga", null, 8) },
            Triple("saveDailyHabit", "Couldn't update habit.") { it.updateDailyHabit(run.copy(name = "Jog")) },
            Triple("archiveDailyHabit", "Couldn't archive habit.") { it.archiveDailyHabit("run") },
            Triple("restoreDailyHabit", "Couldn't restore habit.") { it.restoreDailyHabit("run") },
            Triple("deleteDailyHabit", "Couldn't delete habit.") { it.deleteDailyHabit("run") },
            Triple("updateDailyHabitOrders", "Couldn't reorder habits.") { it.moveDailyHabit(0, 1) },
            Triple("saveWeeklyHabit", "Couldn't create weekly habit.") { it.addWeeklyHabit("Long walk", null) },
            Triple("saveWeeklyHabit", "Couldn't update weekly habit.") { it.updateWeeklyHabit(gym.copy(name = "Lift")) },
            Triple("archiveWeeklyHabit", "Couldn't archive weekly habit.") { it.archiveWeeklyHabit("gym") },
            Triple("restoreWeeklyHabit", "Couldn't restore weekly habit.") { it.restoreWeeklyHabit("gym") },
            Triple("deleteWeeklyHabit", "Couldn't delete weekly habit.") { it.deleteWeeklyHabit("gym") },
        )
        for ((operation, message, action) in cases) {
            val fixture = manage(failing = arrayOf(operation))
            action(fixture.viewModel)
            runCurrent()
            assertEquals(message, fixture.viewModel.message(), operation)
            assertEquals(snapshot, fixture.repository.snapshot.value, "$operation changed data although it failed")
            fixture.viewModel.clearError()
            assertNull(fixture.viewModel.error.value)
        }
        // Names of habits and categories never reach the log.
        val printed = logger.printed()
        listOf("Run", "Jog", "Yoga", "Fitness", "Study", "Lift", "Long walk").forEach { assertFalse(it in printed, "log mentions $it") }
        assertTrue(logger.at(LogLevel.ERROR).size == cases.size)
    }

    @Test
    fun theTwentyFirstHabitIsRefusedWithTheLimitAndNothingIsSaved() = runTest {
        val full = HabitSnapshot(
            dailyHabits = List(ManageHabitsViewModel.MAX_DAILY_HABITS) { DailyHabit("d$it", "Habit $it", null, 10, it, true, today, null, 1, 1) },
            weeklyHabits = List(ManageHabitsViewModel.MAX_WEEKLY_HABITS) { WeeklyHabit("w$it", "Weekly $it", null, it, true, today, null, 1, 1) },
        )
        val fixture = manage(full)

        fixture.viewModel.addDailyHabit("One too many", null, 10)
        runCurrent()
        assertEquals("Maximum 20 daily habits allowed.", fixture.viewModel.message())
        assertEquals(20, fixture.repository.snapshot.value.dailyHabits.size)

        fixture.viewModel.clearError()
        fixture.viewModel.addWeeklyHabit("One too many", null)
        runCurrent()
        assertEquals("Maximum 20 weekly habits allowed.", fixture.viewModel.message())
        assertEquals(20, fixture.repository.snapshot.value.weeklyHabits.size)

        // Archived habits do not count towards the limit.
        fixture.repository.archiveDailyHabit("d0", today, 10)
        fixture.viewModel.clearError()
        fixture.viewModel.addDailyHabit("Fits now", null, 10)
        runCurrent()
        assertNull(fixture.viewModel.error.value)
        assertEquals(21, fixture.repository.snapshot.value.dailyHabits.size)
    }

    @Test
    fun invalidInputIsIgnoredQuietlyAndOutOfRangeMovesDoNothing() = runTest {
        val fixture = manage()
        val viewModel = fixture.viewModel

        viewModel.addDailyHabit("   ", null, 10)
        viewModel.addDailyHabit("Negative", null, -1)
        viewModel.addWeeklyHabit("", null)
        viewModel.addCategory("  ")
        viewModel.updateDailyHabit(run.copy(name = " "))
        viewModel.moveDailyHabit(0, 5)
        viewModel.moveDailyHabit(-1, 0)
        viewModel.moveWeeklyHabit(0, 3)
        runCurrent()

        assertNull(viewModel.error.value)
        assertEquals(snapshot, fixture.repository.snapshot.value)
        assertTrue(logger.entries.isEmpty())
    }

    @Test
    fun movingReordersOnlyActiveHabitsAndStampsTheMoveWithTheClock() = runTest {
        val fixture = manage(
            buildSnapshot(stamp = 1) {
                dailyHabit("a", order = 0, createdOn = LocalDate(2026, 9, 1))
                dailyHabit("b", order = 1, createdOn = LocalDate(2026, 9, 1))
                dailyHabit("c", order = 2, createdOn = LocalDate(2026, 9, 1))
                dailyHabit("old", order = 3, createdOn = LocalDate(2026, 9, 1), active = false, archivedOn = LocalDate(2026, 9, 30))
            },
        )

        fixture.viewModel.moveDailyHabit(0, 2)
        runCurrent()

        val byId = fixture.repository.snapshot.value.dailyHabits.associateBy { it.id }
        assertEquals(listOf("b", "c", "a"), byId.values.filter { it.active }.sortedBy { it.displayOrder }.map { it.id })
        assertEquals(3, byId.getValue("old").displayOrder, "an archived habit keeps its place")
        assertEquals(5_000, byId.getValue("a").updatedAtEpochMillis)
    }

    @Test
    fun archivingUsesTodayAndANewHabitStartsToday() = runTest {
        val fixture = manage()

        fixture.viewModel.archiveDailyHabit("run")
        fixture.viewModel.addDailyHabit("  Yoga  ", "c1", 8)
        runCurrent()

        val habits = fixture.repository.snapshot.value.dailyHabits
        assertEquals(today, habits.single { it.id == "run" }.archivedOn)
        val yoga = habits.single { it.name == "Yoga" }
        assertEquals(today, yoga.createdOn)
        assertEquals("c1", yoga.categoryId)
        assertEquals(2, yoga.displayOrder, "after the highest existing order")
    }

    // ---- settings ----

    private class FailingSettings(private val inner: InMemoryHabitRepository, var failSetUrl: Boolean = true) : SettingsStore by inner {
        override suspend fun setSheetUrl(url: String) {
            if (failSetUrl) error("storage unavailable")
            inner.setSheetUrl(url)
        }
    }

    @Test
    fun savingOrDisconnectingASheetLinkThatCannotBeStoredSaysSoAndKeepsTheOldLink() = runTest {
        val inner = InMemoryHabitRepository().also { it.setSheetUrl("https://docs.google.com/spreadsheets/d/old/edit") }
        val settings = FailingSettings(inner)
        val viewModel = SettingsViewModel(settings, backgroundScope, UnconfinedTestDispatcher(testScheduler), logger = logger)
        runCurrent()
        assertEquals("https://docs.google.com/spreadsheets/d/old/edit", viewModel.sheetUrl.value)

        viewModel.saveSheetUrl("https://docs.google.com/spreadsheets/d/new/edit")
        assertEquals("Could not save the sheet link.", English.render(assertNotNull(viewModel.sheetMessage.value)))
        assertEquals("https://docs.google.com/spreadsheets/d/old/edit", viewModel.sheetUrl.value)

        viewModel.connect("https://docs.google.com/spreadsheets/d/new/edit")
        assertEquals("Could not save the sheet link.", English.render(assertNotNull(viewModel.sheetMessage.value)))

        viewModel.disconnect()
        assertEquals("Could not disconnect. Please try again.", English.render(assertNotNull(viewModel.sheetMessage.value)))
        assertEquals("https://docs.google.com/spreadsheets/d/old/edit", viewModel.sheetUrl.value, "still linked after a failed disconnect")

        settings.failSetUrl = false
        viewModel.disconnect()
        assertEquals("Disconnected. Your habits and check-offs stay on this device.", English.render(assertNotNull(viewModel.sheetMessage.value)))
        assertEquals("", viewModel.sheetUrl.value)
        assertEquals(3, logger.at(LogLevel.ERROR).size)
        assertFalse("docs.google.com" in logger.printed(), "the link never reaches the log")
    }

    @Test
    fun aThemeThatCannotBeStoredStillAppliesForThisSession() = runTest {
        val inner = InMemoryHabitRepository()
        val settings = object : SettingsStore by inner {
            override suspend fun setThemeMode(mode: Int) = error("storage unavailable")
        }
        val viewModel = SettingsViewModel(settings, backgroundScope, UnconfinedTestDispatcher(testScheduler), logger = logger)

        viewModel.setThemeMode(ThemeMode.Dark)
        runCurrent()

        assertEquals(ThemeMode.Dark, viewModel.themeMode.value)
        assertEquals("setThemeMode failed", logger.at(LogLevel.ERROR).single().message)
    }
}
