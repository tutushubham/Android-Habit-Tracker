package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.data.DefaultIdGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestructiveActionsTest {
    private val repository = InMemoryHabitRepository()
    private val idGenerator = DefaultIdGenerator()
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = LocalDate(2026, 8, 27)
        override fun nowEpochMillis(): Long = 1000
    }

    @Test
    fun deleteCategoryPermanently() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addCategory("Temporary")
        val category = repository.snapshot.first { it.categories.isNotEmpty() }.categories.single()
        
        viewModel.deleteCategory(category.id)
        
        val snapshot = repository.snapshot.first { it.categories.isEmpty() }
        assertTrue(snapshot.categories.isEmpty())
    }

    @Test
    fun deleteDailyHabitPermanently() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addDailyHabit("One-time habit", null, 1)
        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()
        
        viewModel.deleteDailyHabit(habit.id)
        
        val snapshot = repository.snapshot.first { it.dailyHabits.isEmpty() }
        assertTrue(snapshot.dailyHabits.isEmpty())
    }

    @Test
    fun deleteHabitAlsoClearsCompletions() = runTest {
        val viewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        viewModel.addDailyHabit("Habit with history", null, 10)
        val habit = repository.snapshot.first { it.dailyHabits.isNotEmpty() }.dailyHabits.single()
        
        repository.setDailyCompletion(com.habitsheet.domain.model.DailyHabitCompletion(habit.id, LocalDate(2026, 8, 1), true, 0))
        assertTrue(repository.snapshot.value.dailyCompletions.isNotEmpty())
        
        viewModel.deleteDailyHabit(habit.id)
        
        val snapshot = repository.snapshot.first { it.dailyHabits.isEmpty() }
        assertTrue(snapshot.dailyCompletions.isEmpty())
    }
}
