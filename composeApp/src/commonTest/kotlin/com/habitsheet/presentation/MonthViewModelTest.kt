package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.MonthKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun scrollToTodayTriggerUpdatesEvenIfAlreadyInCurrentMonth() = runTest {
        val viewModel = MonthViewModel(repository, dateProvider, backgroundScope)
        val initialTrigger = viewModel.state.first().scrollToTodayTrigger
        
        viewModel.currentMonth()
        val nextState = viewModel.state.first { it.scrollToTodayTrigger > initialTrigger }
        
        assertTrue(nextState.scrollToTodayTrigger > initialTrigger)
    }
}
