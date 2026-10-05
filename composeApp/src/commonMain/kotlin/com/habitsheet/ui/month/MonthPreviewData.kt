package com.habitsheet.ui.month

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.presentation.toUiState
import kotlinx.datetime.LocalDate

/** Sample state for the month leaves' `@Preview`s; built through the real `toUiState`, so it is always consistent. */
internal object MonthPreviewData {
    private val today = LocalDate(2026, 10, 5)
    private val start = LocalDate(2026, 9, 1)

    private val snapshot = HabitSnapshot(
        categories = listOf(
            Category("study", "Study 📚", 0, true, 1),
            Category("health", "Health ❤️", 1, true, 1),
            Category("fitness", "Fitness 💪", 2, true, 1),
        ),
        dailyHabits = listOf(
            DailyHabit("read", "Read 20 pages", "study", 25, 0, true, start, null, 1, 1),
            DailyHabit("water", "Drink 2L water", "health", 28, 1, true, start, null, 1, 1),
            DailyHabit("sugar", "No sugar", null, 20, 2, true, start, null, 1, 1, kind = HabitKind.AVOIDANCE),
            DailyHabit("run", "Run", "fitness", 12, 3, true, start, null, 1, 1, datedOnly = true),
        ),
        dailyCompletions = (1..5).flatMap { day ->
            listOf(
                DailyHabitCompletion("read", LocalDate(2026, 10, day), day != 3, 1),
                DailyHabitCompletion("water", LocalDate(2026, 10, day), day % 2 == 1, 1),
            )
        } + DailyHabitCompletion("run", today, true, 1, planId = "run-am"),
        weeklyHabits = listOf(WeeklyHabit("call", "Call parents", null, 0, true, start, null, 1, 1)),
        weeklyCompletions = listOf(WeeklyHabitCompletion("call", LocalDate(2026, 10, 1), true, 1)),
        dayPlans = listOf(
            DayPlan("run", today, "Long · 12 km", false, 1, "run-am"),
            DayPlan("run", today, "Strides", false, 1, "run-pm"),
            DayPlan("run", LocalDate(2026, 10, 7), "Easy · 5 km", false, 1, "run-next"),
        ),
    )

    fun state(todayMode: Boolean = false, data: HabitSnapshot = snapshot): MonthUiState =
        data.toUiState(MonthKey.from(today), today, today, false, 0, todayMode, null)

    val empty: MonthUiState get() = state(data = HabitSnapshot())
}
