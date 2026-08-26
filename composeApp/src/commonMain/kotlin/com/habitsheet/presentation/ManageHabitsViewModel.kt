package com.habitsheet.presentation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.domain.repository.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ManageHabitsViewModel(
    private val repository: HabitRepository,
    private val idGenerator: IdGenerator,
    private val dateProvider: DateProvider = SystemDateProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val state: StateFlow<com.habitsheet.domain.model.HabitSnapshot> = repository.snapshot

    fun saveCategory(id: String, name: String, displayOrder: Int, active: Boolean = true) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val now = dateProvider.nowEpochMillis()
        scope.launch {
            repository.saveCategory(Category(id, trimmed, displayOrder, active, now))
        }
    }

    fun addCategory(name: String) {
        val active = state.value.categories.filter { it.active }
        if (active.size >= MaxCategories) return
        saveCategory(
            id = idGenerator.newId(),
            name = name,
            displayOrder = (state.value.categories.maxOfOrNull { it.displayOrder } ?: -1) + 1,
        )
    }

    fun archiveCategory(id: String) {
        val category = state.value.categories.firstOrNull { it.id == id } ?: return
        saveCategory(id, category.name, category.displayOrder, active = false)
    }

    fun addDailyHabit(name: String, categoryId: String?, monthlyGoal: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || monthlyGoal < 0) return
        if (state.value.dailyHabits.count { it.active } >= MaxDailyHabits) return
        val now = dateProvider.nowEpochMillis()
        scope.launch {
            repository.saveDailyHabit(
                DailyHabit(
                    id = idGenerator.newId(),
                    name = trimmed,
                    categoryId = categoryId,
                    monthlyGoal = monthlyGoal,
                    displayOrder = (state.value.dailyHabits.maxOfOrNull { it.displayOrder } ?: -1) + 1,
                    active = true,
                    createdOn = dateProvider.today(),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    fun updateDailyHabit(habit: DailyHabit) {
        if (habit.name.isBlank() || habit.monthlyGoal < 0) return
        scope.launch {
            repository.saveDailyHabit(habit.copy(updatedAtEpochMillis = dateProvider.nowEpochMillis()))
        }
    }

    fun archiveDailyHabit(id: String) {
        scope.launch {
            repository.archiveDailyHabit(id, dateProvider.today(), dateProvider.nowEpochMillis())
        }
    }

    fun addWeeklyHabit(name: String, categoryId: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (state.value.weeklyHabits.count { it.active } >= MaxWeeklyHabits) return
        val now = dateProvider.nowEpochMillis()
        scope.launch {
            repository.saveWeeklyHabit(
                WeeklyHabit(
                    id = idGenerator.newId(),
                    name = trimmed,
                    categoryId = categoryId,
                    displayOrder = (state.value.weeklyHabits.maxOfOrNull { it.displayOrder } ?: -1) + 1,
                    active = true,
                    createdOn = dateProvider.today(),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    fun updateWeeklyHabit(habit: WeeklyHabit) {
        if (habit.name.isBlank()) return
        scope.launch {
            repository.saveWeeklyHabit(habit.copy(updatedAtEpochMillis = dateProvider.nowEpochMillis()))
        }
    }

    fun archiveWeeklyHabit(id: String) {
        scope.launch {
            repository.archiveWeeklyHabit(id, dateProvider.today(), dateProvider.nowEpochMillis())
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        const val MaxCategories = 10
        const val MaxDailyHabits = 20
        const val MaxWeeklyHabits = 20
    }
}
