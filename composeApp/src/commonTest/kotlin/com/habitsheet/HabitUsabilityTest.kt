package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.*
import com.habitsheet.presentation.ManageHabitsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class HabitUsabilityTest {
    private val repository = InMemoryHabitRepository()
    private val idGenerator = DefaultIdGenerator()
    private val dateProvider = object : com.habitsheet.presentation.DateProvider {
        override fun today(): LocalDate = LocalDate(2026, 8, 27)
        override fun nowEpochMillis(): Long = 1000
    }

    @Test
    fun habitReorderingPersistence() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)

        // 1. Create habits
        viewModel.addDailyHabit("Habit 1", null, 10)
        viewModel.addDailyHabit("Habit 2", null, 10)

        val habits = repository.snapshot.first { it.dailyHabits.size == 2 }.dailyHabits.sortedBy { it.displayOrder }
        val id1 = habits[0].id
        val id2 = habits[1].id
        assertEquals("Habit 1", habits[0].name)
        assertEquals("Habit 2", habits[1].name)

        // 2. Reorder
        viewModel.moveDailyHabit(0, 1) // Move Habit 1 after Habit 2

        val reordered = repository.snapshot.first { it.dailyHabits.find { h -> h.id == id1 }?.displayOrder == 1 }.dailyHabits.sortedBy { it.displayOrder }
        assertEquals("Habit 2", reordered[0].name)
        assertEquals("Habit 1", reordered[1].name)

        // 3. Verify persistence (restart ViewModel)
        val nextViewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        val persisted = nextViewModel.state.value.dailyHabits.sortedBy { it.displayOrder }
        assertEquals("Habit 2", persisted[0].name)
        assertEquals("Habit 1", persisted[1].name)
    }

    @Test
    fun quickAddDailyHabit() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)

        viewModel.addDailyHabit("Quick Habit", null, 5)

        val snapshot = repository.snapshot.first { it.dailyHabits.isNotEmpty() }
        val habit = snapshot.dailyHabits.single()
        assertEquals("Quick Habit", habit.name)
        assertEquals(5, habit.monthlyGoal)
        assertEquals(0, habit.displayOrder) // First habit
    }

    @Test
    fun newHabitAppearsAtEndByDefault() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addDailyHabit("H1", null, 10)
        viewModel.addDailyHabit("H2", null, 10)

        val habits = repository.snapshot.first { it.dailyHabits.size == 2 }.dailyHabits.sortedBy { it.displayOrder }
        assertEquals("H2", habits[1].name)
        assertEquals(1, habits[1].displayOrder)
    }
}
