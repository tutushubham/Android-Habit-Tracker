package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate

/** One planned session on a date. Multiple rows may share a habit and date. */
data class PlannedHabit(
    val habit: DailyHabit,
    val detail: String?,
    val skipped: Boolean,
    val id: String,
)

/**
 * Sessions due on [date], in display order. A day may contain any number of sessions
 * across habits and categories.
 *
 * Resolution per habit:
 * 1. Dated [DayPlan] rows for that date (0..N), including skipped rest notes
 * 2. Else the [WeeklyPlan] for that weekday, if any
 * 3. Else every day only when the habit has no schedule at all (legacy simple trackers)
 *
 * Habits marked [DailyHabit.datedOnly], with any weekly plan, or sheet-managed never fall
 * through to the every-day default — they appear only on explicit plan days.
 */
fun HabitSnapshot.plannedHabitsOn(date: LocalDate): List<PlannedHabit> = dailyHabits
    .filter { it.isActiveOn(date) }
    .sortedBy { it.displayOrder }
    .flatMap { habit ->
        val dated = dayPlans.filter { it.habitId == habit.id && it.date == date }
        if (dated.isNotEmpty()) {
            return@flatMap dated.map { PlannedHabit(habit, it.detail, it.skipped, it.id) }
        }
        val weekly = weeklyPlans.firstOrNull {
            it.habitId == habit.id && it.weekday == date.dayOfWeek.ordinal + 1
        }
        val scheduled = habit.datedOnly ||
            weeklyPlans.any { it.habitId == habit.id } ||
            habit.id in sheetManagedHabitIds
        when {
            weekly != null -> listOf(PlannedHabit(habit, weekly.detail, false, "${habit.id}|$date"))
            scheduled -> emptyList()
            else -> listOf(PlannedHabit(habit, null, false, "${habit.id}|$date"))
        }
    }
