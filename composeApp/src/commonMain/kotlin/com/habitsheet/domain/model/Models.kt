package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class HabitKind { ACTION, AVOIDANCE }

@Serializable
data class Category(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val active: Boolean = true,
    val updatedAtEpochMillis: Long,
)

@Serializable
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
    val kind: HabitKind = HabitKind.ACTION,
    val datedOnly: Boolean = false,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        date >= createdOn &&
            (archivedOn == null || date < archivedOn) &&
            (active || archivedOn != null)
}

@Serializable
data class DailyHabitCompletion(
    val habitId: String,
    val date: LocalDate,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
    val planId: String = "$habitId|$date",
)

/** A recurring prescription for one weekday (Monday = 1). */
@Serializable
data class WeeklyPlan(
    val habitId: String,
    val weekday: Int,
    val detail: String,
    val updatedAtEpochMillis: Long,
)

/** A dated prescription overrides a weekly plan for this habit and day. */
@Serializable
data class DayPlan(
    val habitId: String,
    val date: LocalDate,
    val detail: String,
    val skipped: Boolean = false,
    val updatedAtEpochMillis: Long,
    val id: String = "$habitId|$date",
)

@Serializable
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

@Serializable
data class WeeklyHabitCompletion(
    val weeklyHabitId: String,
    val weekStartDate: LocalDate,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)

/** Identifies one daily completion: the plan it belongs to and the date. */
data class CompletionKey(val planId: String, val date: LocalDate)

@Serializable
data class HabitSnapshot(
    val categories: List<Category> = emptyList(),
    val dailyHabits: List<DailyHabit> = emptyList(),
    val dailyCompletions: List<DailyHabitCompletion> = emptyList(),
    val weeklyHabits: List<WeeklyHabit> = emptyList(),
    val weeklyCompletions: List<WeeklyHabitCompletion> = emptyList(),
    val weeklyPlans: List<WeeklyPlan> = emptyList(),
    val dayPlans: List<DayPlan> = emptyList(),
    @Transient val sheetManagedHabitIds: Set<String> = emptySet(),
    /** Completions changed on this device and not yet written to the sheet. Not part of backups. */
    @Transient val pendingCompletions: Set<CompletionKey> = emptySet(),
)
