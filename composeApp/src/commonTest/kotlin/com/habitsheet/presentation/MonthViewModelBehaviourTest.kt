package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.PlannedHabit
import com.habitsheet.platform.LogLevel
import com.habitsheet.testing.English
import com.habitsheet.testing.FailingHabitStore
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.FakeLogger
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Month navigation, today/tomorrow plans, toggling and the error messages of [MonthViewModel], driven through a
 * real repository and the fake clock. (Midnight, time-zone and DST behaviour is in `DateHandlingTest`.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelBehaviourTest {
    // Saturday 31 October 2026: the last day of a 31-day month, followed by Sunday 1 November.
    private val saturday = LocalDate(2026, 10, 31)
    private val sunday = LocalDate(2026, 11, 1)
    private val dates = FakeDateProvider(today = saturday, nowMillis = 1_000L, tickMillis = 1)
    private val logger = FakeLogger()
    private var localChanges = 0

    private val snapshot = buildSnapshot(stamp = 1) {
        category("health", "Health")
        dailyHabit("read", "Read", category = "health", goal = 20, createdOn = LocalDate(2026, 9, 1)) // no schedule: every day
        dailyHabit("run", "Run", goal = 12, createdOn = LocalDate(2026, 9, 1), datedOnly = true)
        dailyHabit("swim", "Swim", goal = 8, createdOn = LocalDate(2026, 9, 1))
        weeklyPlan("swim", 6, "Laps") // Saturdays only
        dayPlan("run", saturday, "Easy 5 km", id = "run-a")
        dayPlan("run", saturday, "Mobility", id = "run-b")
        dayPlan("run", sunday, "Long run", id = "run-long")
        dayPlan("run", LocalDate(2026, 11, 2), "Rest", skipped = true, id = "run-rest")
        weeklyHabit("gym", createdOn = LocalDate(2026, 10, 15))
    }

    private fun TestScope.viewModel(): MonthViewModel {
        val repository = InMemoryHabitRepository(snapshot)
        return MonthViewModel(repository, repository, dates, backgroundScope, onLocalChange = { localChanges++ }, logger = logger)
            .also { runCurrent() }
    }

    private fun idsOf(plan: List<PlannedHabit>) = plan.map { it.id }.sorted()

    // ---- month navigation ----

    @Test
    fun monthNavigationCrossesYearEndsAndKeepsTheSelectedDayAndToday() = runTest {
        val viewModel = viewModel()
        assertEquals(MonthKey(2026, 10), viewModel.state.value.selectedMonth)

        repeat(3) { viewModel.nextMonth() }
        runCurrent()
        assertEquals(MonthKey(2027, 1), viewModel.state.value.selectedMonth)
        assertEquals(31, viewModel.state.value.daily.size)
        assertEquals(saturday, viewModel.state.value.selectedDay, "browsing months does not move the selected day")
        assertEquals(saturday, viewModel.state.value.today)

        repeat(2) { viewModel.previousMonth() }
        runCurrent()
        assertEquals(MonthKey(2026, 11), viewModel.state.value.selectedMonth)
        assertEquals(30, viewModel.state.value.daily.size)
    }

    @Test
    fun aLeapFebruaryHas29DaysAndAFifthWeekOfOneDay() = runTest {
        val viewModel = viewModel()
        viewModel.selectMonth(MonthKey(2028, 2))
        runCurrent()
        var state = viewModel.state.value
        assertEquals(29, state.daily.size)
        assertEquals(5, state.dailyWeeks.size)
        assertEquals(1, state.dailyWeeks.last().dates.size)
        assertEquals(5, state.weeklyBlocks.size)

        viewModel.selectMonth(MonthKey(2027, 2))
        runCurrent()
        state = viewModel.state.value
        assertEquals(28, state.daily.size)
        assertEquals(4, state.dailyWeeks.size)
        assertEquals(4, state.weeklyBlocks.size)
    }

    @Test
    fun theCurrentMonthActionReturnsFromAnyMonthAndFiresTheScrollTrigger() = runTest {
        val viewModel = viewModel()
        viewModel.selectMonth(MonthKey(2024, 2))
        runCurrent()
        val before = viewModel.state.value.scrollToTodayTrigger

        viewModel.currentMonth()
        runCurrent()

        assertEquals(MonthKey(2026, 10), viewModel.state.value.selectedMonth)
        assertTrue(viewModel.state.value.scrollToTodayTrigger > before)
    }

    // ---- today and tomorrow ----

    @Test
    fun todayAndTomorrowListTheirOwnSessionsAcrossAMonthEnd() = runTest {
        val state = viewModel().state.value

        assertEquals(listOf("read|2026-10-31", "run-a", "run-b", "swim|2026-10-31"), idsOf(state.dayPlan))
        assertEquals(listOf("Laps"), state.dayPlan.filter { it.habit.id == "swim" }.map { it.detail }, "the Saturday plan")
        assertEquals(listOf("read|2026-11-01", "run-long"), idsOf(state.tomorrowPlan), "no swim on a Sunday")
        assertEquals(4, state.todaySummary.totalCount)
    }

    @Test
    fun whenMidnightPassesAndTodayIsFollowedTheDayAndTheMonthMoveOnAndTomorrowShifts() = runTest {
        val viewModel = viewModel()

        dates.advanceDays(1)
        viewModel.refreshToday()
        runCurrent()

        val state = viewModel.state.value
        assertEquals(sunday, state.today)
        assertEquals(sunday, state.selectedDay)
        assertEquals(MonthKey(2026, 11), state.selectedMonth)
        assertEquals(listOf("read|2026-11-01", "run-long"), idsOf(state.dayPlan))
        assertEquals(listOf("read|2026-11-02", "run-rest"), idsOf(state.tomorrowPlan))
    }

    @Test
    fun aRestDayIsListedButNeverDueCountedOrCheckable() = runTest {
        val viewModel = viewModel()
        val monday = LocalDate(2026, 11, 2)
        viewModel.openDay(monday)
        runCurrent()
        val state = viewModel.state.value

        assertTrue(state.dayPlan.single { it.id == "run-rest" }.skipped)
        assertEquals(1, state.todaySummary.totalCount, "only Read is due")
        assertFalse(("run-rest" to monday) in state.dueKeys)

        viewModel.togglePlanned("run-rest", monday)
        runCurrent()
        assertTrue(viewModel.state.value.dailyCompletionKeys.isEmpty())
        assertEquals(0, localChanges)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun steppingDaysMovesBothPlansAndStopsFollowingToday() = runTest {
        val viewModel = viewModel()

        viewModel.nextDay()
        runCurrent()
        assertEquals(sunday, viewModel.state.value.selectedDay)
        assertEquals(MonthKey(2026, 11), viewModel.state.value.selectedMonth)
        assertEquals(listOf("read|2026-11-02", "run-rest"), idsOf(viewModel.state.value.tomorrowPlan))

        dates.advanceDays(2) // the phone is used again two days later
        viewModel.refreshToday()
        runCurrent()
        assertEquals(LocalDate(2026, 11, 2), viewModel.state.value.today)
        assertEquals(sunday, viewModel.state.value.selectedDay, "a day the person stepped to is not pulled away")

        viewModel.currentDay()
        runCurrent()
        assertEquals(LocalDate(2026, 11, 2), viewModel.state.value.selectedDay)
        viewModel.previousDay()
        runCurrent()
        assertEquals(sunday, viewModel.state.value.selectedDay)
    }

    // ---- toggling ----

    @Test
    fun aSessionTogglesOnAndOffAndTheDayFiguresFollow() = runTest {
        val viewModel = viewModel()

        viewModel.togglePlanned("run-a", saturday)
        runCurrent()
        var state = viewModel.state.value
        assertTrue(("run-a" to saturday) in state.dailyCompletionKeys)
        assertFalse(("run-b" to saturday) in state.dailyCompletionKeys, "the other session of the same habit is untouched")
        assertEquals(1, state.todaySummary.completedCount)
        assertEquals(0.25, state.todaySummary.percentage)
        assertEquals(1, localChanges)

        viewModel.togglePlanned("run-a", saturday)
        runCurrent()
        state = viewModel.state.value
        assertFalse(("run-a" to saturday) in state.dailyCompletionKeys)
        assertEquals(0, state.todaySummary.completedCount)
        assertEquals(2, localChanges)
    }

    @Test
    fun togglingSomethingNotDueIsIgnoredWithoutAnErrorOrASyncTrigger() = runTest {
        val viewModel = viewModel()

        viewModel.toggleDaily("ghost", saturday) // unknown habit
        viewModel.toggleDaily("run", LocalDate(2026, 11, 3)) // dated-only habit with nothing that day
        viewModel.toggleDaily("read", LocalDate(2026, 8, 1)) // before the habit existed
        viewModel.togglePlanned("run-a", LocalDate(2026, 10, 30)) // a session id on the wrong day
        runCurrent()

        assertTrue(viewModel.state.value.dailyCompletionKeys.isEmpty())
        assertNull(viewModel.state.value.error)
        assertEquals(0, localChanges)
        assertTrue(logger.entries.isEmpty())
    }

    @Test
    fun aWeeklyHabitTogglesPerWeekBlockAndNotBeforeItExisted() = runTest {
        val viewModel = viewModel()
        val week1 = LocalDate(2026, 10, 1)
        val week3 = LocalDate(2026, 10, 15)
        val week5 = LocalDate(2026, 10, 29)

        viewModel.toggleWeekly("gym", week1) // created on the 15th: not active in the first week
        runCurrent()
        assertTrue(viewModel.state.value.weeklyCompletionKeys.isEmpty())

        viewModel.toggleWeekly("gym", week3)
        viewModel.toggleWeekly("gym", week5)
        runCurrent()
        assertEquals(setOf("gym" to week3, "gym" to week5), viewModel.state.value.weeklyCompletionKeys)
        assertEquals(2, viewModel.state.value.weeklyProgress.completed)

        viewModel.toggleWeekly("gym", week3)
        runCurrent()
        assertEquals(setOf("gym" to week5), viewModel.state.value.weeklyCompletionKeys)
    }

    // ---- error surfaces ----

    private fun TestScope.failingViewModel(vararg failing: String): Pair<MonthViewModel, FailingHabitStore> {
        val inner = InMemoryHabitRepository(snapshot)
        val store = FailingHabitStore(inner, *failing)
        val viewModel = MonthViewModel(store, inner, dates, backgroundScope, onLocalChange = { localChanges++ }, logger = logger)
        runCurrent()
        return viewModel to store
    }

    private fun MonthViewModel.errorText(): String = English.render(assertNotNull(state.value.error))

    @Test
    fun aFailedCheckOffShowsAFriendlyMessageLogsItWithoutUserTextAndTheNextTapStillWorks() = runTest {
        val (viewModel, store) = failingViewModel("setDailyCompletion")

        viewModel.togglePlanned("run-a", saturday)
        runCurrent()

        assertEquals("Couldn't update habit. Please try again.", viewModel.errorText())
        assertEquals(0, localChanges, "nothing changed, so there is nothing to upload")
        assertTrue(viewModel.state.value.dailyCompletionKeys.isEmpty())
        assertEquals("togglePlanned failed", logger.at(LogLevel.ERROR).single().message)
        assertFalse("Easy" in logger.printed() || "Run" in logger.printed(), "no habit or session text in logs")

        viewModel.clearError()
        store.failing.clear()
        viewModel.togglePlanned("run-a", saturday)
        runCurrent()
        assertNull(viewModel.state.value.error)
        assertTrue(("run-a" to saturday) in viewModel.state.value.dailyCompletionKeys)
        assertEquals(1, localChanges)
    }

    @Test
    fun everyFailingOperationHasItsOwnMessage() = runTest {
        val expectations = listOf<Triple<String, String, (MonthViewModel) -> Unit>>(
            Triple("setWeeklyCompletion", "Couldn't update weekly habit. Please try again.") { it.toggleWeekly("gym", LocalDate(2026, 10, 15)) },
            Triple("saveDayPlan", "Couldn't save the day plan.") { it.saveDayPlan("run", saturday, "Tempo") },
            Triple("deleteDayPlan", "Couldn't remove the day plan.") { it.deleteDayPlan("run", saturday) },
            Triple("deleteDayPlanById", "Couldn't remove the day plan.") { it.deleteDayPlanById("run-a") },
            Triple("saveWeeklyPlan", "Couldn't save the weekly plan.") { it.saveWeeklyPlan("swim", 3, "Laps") },
            Triple("deleteWeeklyPlan", "Couldn't remove the weekly plan.") { it.deleteWeeklyPlan("swim", 6) },
        )
        for ((operation, message, action) in expectations) {
            val (viewModel, _) = failingViewModel(operation)
            action(viewModel)
            runCurrent()
            assertEquals(message, viewModel.errorText(), operation)
        }
        val failures = logger.at(LogLevel.ERROR).mapNotNull { it.throwable?.message }
        assertEquals(expectations.map { "injected failure in ${it.first}" }, failures)
    }

    @Test
    fun blankPlanTextIsIgnoredQuietlyAndValidTextIsTrimmed() = runTest {
        val viewModel = viewModel()
        viewModel.saveDayPlan("run", saturday, "   ", id = "blank")
        viewModel.saveWeeklyPlan("swim", 2, "")
        runCurrent()
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.dayPlans.none { it.id == "blank" })

        viewModel.saveDayPlan("run", saturday, "  Tempo  ", id = "run-c")
        viewModel.saveWeeklyPlan("swim", 2, "  Easy  ")
        runCurrent()
        assertEquals("Tempo", viewModel.state.value.dayPlans.single { it.id == "run-c" }.detail)
        assertEquals("Easy", viewModel.state.value.weeklyPlans.single { it.habitId == "swim" && it.weekday == 2 }.detail)
    }
}
