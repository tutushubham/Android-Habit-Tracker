package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.MonthKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
        val viewModel = MonthViewModel(repository, dateProvider, backgroundScope)
        val state = viewModel.state.first()
        assertEquals(MonthKey(2026, 8), state.selectedMonth)
    }

    @Test
    fun navigateToPreviousAndNextMonth() = runTest {
        val viewModel = MonthViewModel(repository, dateProvider, backgroundScope)
        
        viewModel.previousMonth()
        val statePrev = viewModel.state.first { it.selectedMonth.month == 7 }
        assertEquals(MonthKey(2026, 7), statePrev.selectedMonth)
        
        viewModel.nextMonth()
        val stateNext = viewModel.state.first { it.selectedMonth.month == 8 }
        assertEquals(MonthKey(2026, 8), stateNext.selectedMonth)
    }

    @Test
    fun todayActionReturnsToCurrentMonth() = runTest {
        val viewModel = MonthViewModel(repository, dateProvider, backgroundScope)
        
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
        val viewModel = MonthViewModel(repository, dateProvider, backgroundScope)

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
        val viewModel = MonthViewModel(repository, dateProvider, backgroundScope)
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
        val viewModel = MonthViewModel(repo, dateProvider, backgroundScope)

        viewModel.toggleDaily("habit", fixedDate)
        viewModel.toggleDaily("habit", fixedDate)
        runCurrent()
        runCurrent()

        assertFalse(repo.snapshot.value.dailyCompletions.any { it.completed })
    }
}
