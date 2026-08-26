package com.habitsheet.data

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

class InMemoryHabitRepository(initial: HabitSnapshot = HabitSnapshot()) : HabitRepository {
    private val mutableSnapshot = MutableStateFlow(initial)
    override val snapshot: StateFlow<HabitSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun saveCategory(category: Category) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            categories = mutableSnapshot.value.categories.upsert(category) { it.id },
        )
    }

    override suspend fun saveDailyHabit(habit: DailyHabit) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.upsert(habit) { it.id },
        )
    }

    override suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.map { habit ->
                if (habit.id == id) habit.copy(
                    active = false,
                    archivedOn = archivedOn,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                ) else habit
            },
        )
    }

    override suspend fun saveWeeklyHabit(habit: WeeklyHabit) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.upsert(habit) { it.id },
        )
    }

    override suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.map { habit ->
                if (habit.id == id) habit.copy(
                    active = false,
                    archivedOn = archivedOn,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                ) else habit
            },
        )
    }

    override suspend fun setDailyCompletion(completion: DailyHabitCompletion) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyCompletions = mutableSnapshot.value.dailyCompletions.upsert(completion) {
                it.habitId to it.date
            },
        )
    }

    override suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyCompletions = mutableSnapshot.value.weeklyCompletions.upsert(completion) {
                it.weeklyHabitId to it.weekStartDate
            },
        )
    }
}

private fun <T, K> List<T>.upsert(value: T, key: (T) -> K): List<T> {
    val target = key(value)
    var replaced = false
    val updated = map { current ->
        if (key(current) == target) {
            replaced = true
            value
        } else current
    }
    return if (replaced) updated else updated + value
}
