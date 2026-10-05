package com.habitsheet.presentation

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManageHabitsViewModelTest {
    private val repository = InMemoryHabitRepository()
    private val idGenerator = DefaultIdGenerator()
    private val fixedDate = LocalDate(2026, 8, 27)
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = fixedDate
        override fun nowEpochMillis(): Long = 1000
    }

    @Test
    fun createDailyHabit() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)

        viewModel.addDailyHabit("Exercise", null, 20)

        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()
        assertEquals("Exercise", habit.name)
        assertEquals(20, habit.monthlyGoal)
        assertTrue(habit.active)
    }

    @Test
    fun editDailyHabitNameAndGoal() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addDailyHabit("Old Name", null, 10)
        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()

        viewModel.updateDailyHabit(habit.copy(name = "New Name", monthlyGoal = 15))

        val updated = repository.snapshot.first { it.dailyHabits.first().name == "New Name" }.dailyHabits.single()
        assertEquals("New Name", updated.name)
        assertEquals(15, updated.monthlyGoal)
    }

    @Test
    fun changeCategory() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addCategory("Fitness")
        val categoryId = repository.snapshot.first { it.categories.isNotEmpty() }.categories.first().id

        viewModel.addDailyHabit("Run", categoryId, 10)
        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()
        assertEquals(categoryId, habit.categoryId)

        viewModel.updateDailyHabit(habit.copy(categoryId = null))
        val updated = repository.snapshot.first { it.dailyHabits.first().categoryId == null }.dailyHabits.single()
        assertEquals(null, updated.categoryId)
    }

    @Test
    fun invalidInputDoesNotCreate() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)

        viewModel.addDailyHabit("", null, 10) // Empty name
        // Wait a bit to ensure nothing happens
        kotlinx.coroutines.delay(100)
        assertTrue(repository.snapshot.value.dailyHabits.isEmpty())

        viewModel.addDailyHabit("Valid", null, -1) // Invalid goal
        kotlinx.coroutines.delay(100)
        assertTrue(repository.snapshot.value.dailyHabits.isEmpty())
    }

    @Test
    fun archiveAndRestore() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addDailyHabit("Habit", null, 10)
        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()
        val id = habit.id

        viewModel.archiveDailyHabit(id)
        repository.snapshot.first { !it.dailyHabits.first().active }
        assertFalse(repository.snapshot.value.dailyHabits.single().active)

        viewModel.restoreDailyHabit(id)
        repository.snapshot.first { it.dailyHabits.first().active }
        assertTrue(repository.snapshot.value.dailyHabits.single().active)
    }

    @Test
    fun editingPreservesHistoricalCompletion() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addDailyHabit("Habit", null, 10)
        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()

        // Add completion
        repository.setDailyCompletion(com.habitsheet.domain.model.DailyHabitCompletion(habit.id, fixedDate, true, 0))
        assertTrue(repository.snapshot.value.dailyCompletions.isNotEmpty())

        // Edit habit
        viewModel.updateDailyHabit(habit.copy(name = "Updated Name"))

        // Wait for update
        repository.snapshot.first { it.dailyHabits.first().name == "Updated Name" }

        // Verify completion still exists
        assertTrue(repository.snapshot.value.dailyCompletions.isNotEmpty())
        assertEquals("Updated Name", repository.snapshot.value.dailyHabits.single().name)
    }
}
