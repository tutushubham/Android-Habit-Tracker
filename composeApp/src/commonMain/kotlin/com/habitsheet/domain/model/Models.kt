package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate

data class Category(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val active: Boolean = true,
    val updatedAtEpochMillis: Long,
)

data class DailyHabit(
    val id: String,
    val name: String,
    val categoryId: String?,
    val monthlyGoal: Int,
    val displayOrder: Int,
    val active: Boolean,
    val createdOn: LocalDate,
    val archivedOn: LocalDate? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        date >= createdOn &&
            (archivedOn == null || date < archivedOn) &&
            (active || archivedOn != null)
}

data class DailyHabitCompletion(
    val habitId: String,
    val date: LocalDate,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)

data class WeeklyHabit(
    val id: String,
    val name: String,
    val categoryId: String?,
    val displayOrder: Int,
    val active: Boolean,
    val createdOn: LocalDate,
    val archivedOn: LocalDate? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        date >= createdOn &&
            (archivedOn == null || date < archivedOn) &&
            (active || archivedOn != null)
}

data class WeeklyHabitCompletion(
    val weeklyHabitId: String,
    val weekStartDate: LocalDate,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)

data class HabitSnapshot(
    val categories: List<Category> = emptyList(),
    val dailyHabits: List<DailyHabit> = emptyList(),
    val dailyCompletions: List<DailyHabitCompletion> = emptyList(),
    val weeklyHabits: List<WeeklyHabit> = emptyList(),
    val weeklyCompletions: List<WeeklyHabitCompletion> = emptyList(),
)
