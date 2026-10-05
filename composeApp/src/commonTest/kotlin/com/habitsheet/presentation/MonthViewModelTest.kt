package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelTest {
    private val repository = InMemoryHabitRepository()

    private var currentTime = 1000L
    private val fixedDate = LocalDate(2026, 8, 26)
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = fixedDate
        override fun nowEpochMillis(): Long = currentTime++
    }

    @Test
    fun initialMonthIsCurrentMonth() = runTest {
        val viewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)
        val state = viewModel.state.first()
        assertEquals(MonthKey(2026, 8), state.selectedMonth)
    }

    @Test
    fun navigateToPreviousAndNextMonth() = runTest {
        val viewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)

        viewModel.previousMonth()
        val statePrev = viewModel.state.first { it.selectedMonth.month == 7 }
        assertEquals(MonthKey(2026, 7), statePrev.selectedMonth)

        viewModel.nextMonth()
        val stateNext = viewModel.state.first { it.selectedMonth.month == 8 }
        assertEquals(MonthKey(2026, 8), stateNext.selectedMonth)
    }

    @Test
    fun todayActionReturnsToCurrentMonth() = runTest {
        val viewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)

        viewModel.selectMonth(MonthKey(2025, 12))
        val stateHist = viewModel.state.first { it.selectedMonth.year == 2025 }
        assertEquals(MonthKey(2025, 12), stateHist.selectedMonth)

        viewModel.currentMonth()
        val stateToday = viewModel.state.first { it.selectedMonth.year == 2026 }
        assertEquals(MonthKey(2026, 8), stateToday.selectedMonth)
        assertTrue(stateToday.scrollToTodayTrigger > 0)
    }

    @Test
    fun todayModeNavigatesDaysAcrossMonthBoundaries() = runTest {
        val viewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)

        viewModel.setTodayMode(true)
        viewModel.previousDay()
        val previous = viewModel.state.first { it.selectedDay == LocalDate(2026, 8, 25) }
        assertTrue(previous.todayMode)
        assertEquals(MonthKey(2026, 8), previous.selectedMonth)

        repeat(26) { viewModel.previousDay() }
        val july = viewModel.state.first { it.selectedDay == LocalDate(2026, 7, 30) }
        assertEquals(MonthKey(2026, 7), july.selectedMonth)

        viewModel.nextDay()
        assertEquals(LocalDate(2026, 7, 31), viewModel.state.first { it.selectedDay.day == 31 }.selectedDay)

        viewModel.currentDay()
        val current = viewModel.state.first { it.selectedDay == fixedDate }
        assertEquals(MonthKey(2026, 8), current.selectedMonth)
    }

    @Test
    fun scrollToTodayTriggerUpdatesEvenIfAlreadyInCurrentMonth() = runTest {
        val viewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)
        val initialTrigger = viewModel.state.first().scrollToTodayTrigger

        viewModel.currentMonth()
        val nextState = viewModel.state.first { it.scrollToTodayTrigger > initialTrigger }

        assertTrue(nextState.scrollToTodayTrigger > initialTrigger)
    }

    @Test
    fun rapidDoubleToggleUsesLatestPersistedState() = runTest {
        val repo = InMemoryHabitRepository()
        repo.saveDailyHabit(
            DailyHabit(
                id = "habit",
                name = "Habit",
                categoryId = null,
                monthlyGoal = 10,
                displayOrder = 0,
                active = true,
                createdOn = fixedDate,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
            ),
        )
        val viewModel = MonthViewModel(repo, repo, dateProvider, backgroundScope)

        viewModel.toggleDaily("habit", fixedDate)
        viewModel.toggleDaily("habit", fixedDate)
        runCurrent()
        runCurrent()

        assertFalse(repo.snapshot.value.dailyCompletions.any { it.completed })
    }

    @Test
    fun separateSessionsCompleteIndependentlyAndRemovalKeepsHistory() = runTest {
        val habit = DailyHabit("run", "Run", null, 12, 0, true, fixedDate, null, 0, 0, datedOnly = true)
        val repo = InMemoryHabitRepository(
            HabitSnapshot(
                dailyHabits = listOf(habit),
                dayPlans = listOf(
                    DayPlan("run", fixedDate, "Easy run", false, 1, "run-a"),
                    DayPlan("run", fixedDate, "Mobility", false, 1, "run-b"),
                ),
            ),
        )
        val viewModel = MonthViewModel(repo, repo, dateProvider, backgroundScope)
        viewModel.togglePlanned("run-a", fixedDate)
        runCurrent()
        assertEquals(1, repo.snapshot.value.dailyCompletions.count { it.completed })
        assertEquals("run-a", repo.snapshot.value.dailyCompletions.single().planId)
        assertEquals(2, viewModel.state.value.daily.first { it.date == fixedDate }.activeHabits)

        repo.deleteDayPlanById("run-a")
        runCurrent()
        assertEquals(1, viewModel.state.value.daily.first { it.date == fixedDate }.activeHabits)
        assertEquals(0, viewModel.state.value.daily.first { it.date == fixedDate }.completed)
        assertTrue(repo.snapshot.value.dailyCompletions.single().completed)

        repo.saveDayPlan(DayPlan("run", LocalDate(2026, 8, 27), "Easy run", false, 2, "run-a"))
        runCurrent()
        assertEquals(0, viewModel.state.value.daily.first { it.date == LocalDate(2026, 8, 27) }.completed)
        assertEquals(fixedDate, repo.snapshot.value.dailyCompletions.single().date)
    }

    @Test
    fun tutorialShowsOnFreshInstallAndStaysDismissedAfterCompletion() = runTest {
        val viewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)
        runCurrent()
        assertTrue(viewModel.state.first { it.onboardingVisible }.onboardingVisible)

        viewModel.completeOnboarding()
        runCurrent()
        assertFalse(viewModel.state.value.onboardingVisible)
        assertTrue(repository.isOnboardingCompleted())

        val reopened = MonthViewModel(repository, repository, dateProvider, backgroundScope)
        runCurrent()
        assertFalse(reopened.state.value.onboardingVisible)
    }

    @Test
    fun tutorialIsNotShownToInstallsThatAlreadyHaveHabits() = runTest {
        val existing = InMemoryHabitRepository(
            HabitSnapshot(
                dailyHabits = listOf(DailyHabit("run", "Run", null, 12, 0, true, fixedDate, null, 1, 1)),
            ),
        )
        val viewModel = MonthViewModel(existing, existing, dateProvider, backgroundScope)
        runCurrent()
        assertFalse(viewModel.state.value.onboardingVisible)
    }
}
