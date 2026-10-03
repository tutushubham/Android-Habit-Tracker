package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate

/** One to-do row for a date: a single planned session, never a whole habit. */
data class TodaySession(
    val planId: String,
    val habitId: String,
    val label: String,
    val completed: Boolean,
)

/**
 * The sessions to do on [date] (skipped notes excluded), one per `planId`, each with its own done state.
 * Used by the widget; agrees with [plannedHabitsOn] and with the month screen's counting.
 */
fun HabitSnapshot.todaySessions(date: LocalDate): List<TodaySession> {
    val done = dailyCompletions.filter { it.date == date && it.completed }.map { it.planId }.toSet()
    return plannedHabitsOn(date).filterNot { it.skipped }.map { planned ->
        TodaySession(
            planId = planned.id,
            habitId = planned.habit.id,
            label = listOfNotNull(planned.habit.name, planned.detail).joinToString(" · "),
            completed = planned.id in done,
        )
    }
}

/**
 * The session a check-off on [date] refers to: the one with [planId], or, when only the habit is known
 * (buttons placed by older builds), the habit's first session. Null when it is unknown, skipped or not due.
 */
fun HabitSnapshot.sessionToToggle(date: LocalDate, planId: String?, habitId: String?): PlannedHabit? {
    val due = plannedHabitsOn(date).filterNot { it.skipped }
    return if (planId != null) due.firstOrNull { it.id == planId } else due.firstOrNull { it.habit.id == habitId }
}
