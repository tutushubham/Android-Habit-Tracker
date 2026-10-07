package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.testing.English
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * `MonthViewModel.state` is built from eight inputs (data snapshot, selected month, selected day, today, day/month
 * mode, tutorial, scroll-to-today trigger, error). Each input must reach its own field of [MonthUiState] and nothing
 * else, whatever shape the flow combination takes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthStateWiringTest {
    private var today = LocalDate(2026, 10, 5)
    private var now = 1_000L
    private val dates = object : DateProvider {
        override fun today() = today
        override fun nowEpochMillis() = now++
    }
    private val habit = DailyHabit("read", "Read", null, 10, 0, true, LocalDate(2026, 9, 1), null, 1, 1)

    @Test
    fun everyInputReachesItsOwnField() = runTest {
        val repository = InMemoryHabitRepository()
        val viewModel = MonthViewModel(repository, repository, dates, backgroundScope)
        runCurrent()
        val initial = viewModel.state.value
        assertEquals(MonthKey(2026, 10), initial.selectedMonth)
        assertEquals(today, initial.today)
        assertEquals(today, initial.selectedDay)
        assertTrue(initial.todayMode)
        assertTrue(initial.onboardingVisible, "a new install with no habits shows the tutorial")
        assertEquals(0L, initial.scrollToTodayTrigger)
        assertNull(initial.error)
        assertTrue(initial.allDailyHabits.isEmpty())

        // data snapshot
        repository.saveDailyHabit(habit)
        runCurrent()
        assertEquals(listOf("read"), viewModel.state.value.allDailyHabits.map { it.id })
        assertEquals(initial.selectedMonth, viewModel.state.value.selectedMonth)

        // tutorial
        viewModel.completeOnboarding()
        runCurrent()
        assertFalse(viewModel.state.value.onboardingVisible)

        // day/month mode
        viewModel.setTodayMode(false)
        runCurrent()
        assertFalse(viewModel.state.value.todayMode)
        assertEquals(today, viewModel.state.value.selectedDay)

        // selected month only
        viewModel.nextMonth()
        runCurrent()
        assertEquals(MonthKey(2026, 11), viewModel.state.value.selectedMonth)
        assertEquals(today, viewModel.state.value.selectedDay)
        assertEquals(today, viewModel.state.value.today)

        // scroll-to-today trigger (and back to the current month)
        viewModel.currentMonth()
        runCurrent()
        assertEquals(MonthKey(2026, 10), viewModel.state.value.selectedMonth)
        assertTrue(viewModel.state.value.scrollToTodayTrigger > 0)

        // selected day (and its month) without touching today
        viewModel.nextDay()
        runCurrent()
        assertEquals(LocalDate(2026, 10, 6), viewModel.state.value.selectedDay)
        assertEquals(LocalDate(2026, 10, 5), viewModel.state.value.today)

        // today, without moving a day the person picked
        today = LocalDate(2026, 10, 7)
        viewModel.refreshToday()
        runCurrent()
        assertEquals(LocalDate(2026, 10, 7), viewModel.state.value.today)
        assertEquals(LocalDate(2026, 10, 6), viewModel.state.value.selectedDay)
    }

    @Test
    fun errorsReachTheStateAndCanBeCleared() = runTest {
        val inner = InMemoryHabitRepository(HabitSnapshot(dailyHabits = listOf(habit)))
        val failingSettings = object : SettingsStore by inner {
            override suspend fun isOnboardingCompleted(): Boolean = error("storage unavailable")
        }
        val viewModel = MonthViewModel(inner, failingSettings, dates, backgroundScope)
        runCurrent()
        assertEquals("Couldn't load app settings.", English.render(assertNotNull(viewModel.state.value.error)))
        viewModel.clearError()
        runCurrent()
        assertNull(viewModel.state.value.error)
    }
}
