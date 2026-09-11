package com.habitsheet.domain.calculation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import kotlinx.datetime.LocalDate

data class ProgressSummary(
    val completed: Int,
    val goal: Int,
) {
    val percentage: Double get() = if (goal <= 0) 0.0 else completed.toDouble() / goal
    val hasGoal: Boolean get() = goal > 0
}

data class DailySummary(
    val date: LocalDate,
    val completed: Int,
    val notCompleted: Int,
    val activeHabits: Int,
) {
    val percentage: Double get() = if (activeHabits == 0) 0.0 else completed.toDouble() / activeHabits
}

data class HabitSummary(
    val habit: DailyHabit,
    val completed: Int,
) {
    val goal: Int get() = habit.monthlyGoal
    val percentage: Double get() = if (goal <= 0) 0.0 else completed.toDouble() / goal
}

data class CategorySummary(
    val category: Category,
    val goal: Int,
    val completed: Int,
) {
    // The workbook clamps this value at zero even when completions exceed the goal.
    val remaining: Int get() = maxOf(goal - completed, 0)
    val percentage: Double get() = if (goal <= 0) 0.0 else completed.toDouble() / goal
}

data class DailyWeekSummary(
    val index: Int,
    val dates: List<LocalDate>,
    val completed: Int,
    val goal: Int,
) {
    val percentage: Double get() = if (goal <= 0) 0.0 else completed.toDouble() / goal
}

data class WeeklyBlockSummary(
    val index: Int,
    val weekStartDate: LocalDate,
    val completed: Int,
    val goal: Int,
) {
    val percentage: Double get() = if (goal <= 0) 0.0 else completed.toDouble() / goal
    val notCompleted: Int get() = goal - completed
}

data class DailyShareHabit(
    val id: String,
    val name: String,
    val categoryName: String?,
    val completed: Boolean
)

data class DailyShareSummary(
    val date: LocalDate,
    val completedCount: Int,
    val totalCount: Int,
    val percentage: Double,
    val doneHabits: List<DailyShareHabit>,
    val leftHabits: List<DailyShareHabit>
)

object HabitCalculations {
    fun dailyShareSummary(
        date: LocalDate,
        habits: List<DailyHabit>,
        categories: List<Category>,
        completions: List<DailyHabitCompletion>
    ): DailyShareSummary {
        val completedIds = completions
            .filter { it.date == date && it.completed }
            .map { it.habitId }
            .toSet()

        val activeHabits = habits.filter { it.isActiveOn(date) }
        val shareHabits = activeHabits.map { habit ->
            DailyShareHabit(
                id = habit.id,
                name = habit.name,
                categoryName = categories.find { it.id == habit.categoryId }?.name,
                completed = habit.id in completedIds
            )
        }

        val done = shareHabits.filter { it.completed }
        val left = shareHabits.filter { !it.completed }

        return DailyShareSummary(
            date = date,
            completedCount = done.size,
            totalCount = shareHabits.size,
            percentage = if (shareHabits.isEmpty()) 0.0 else done.size.toDouble() / shareHabits.size,
            doneHabits = done,
            leftHabits = left
        )
    }

    fun dailySummaries(
        month: MonthKey,
        habits: List<DailyHabit>,
        completions: List<DailyHabitCompletion>,
    ): List<DailySummary> {
        val completedKeys = completions
            .asSequence()
            .filter { it.completed }
            .map { it.habitId to it.date }
            .toSet()

        return month.dates().map { date ->
            val active = habits.filter { it.isActiveOn(date) }
            val completed = active.count { habit -> habit.id to date in completedKeys }
                .coerceAtMost(active.size)
            DailySummary(
                date = date,
                completed = completed,
                notCompleted = active.size - completed,
                activeHabits = active.size,
            )
        }
    }

    fun habitSummaries(
        month: MonthKey,
        habits: List<DailyHabit>,
        completions: List<DailyHabitCompletion>,
    ): List<HabitSummary> {
        val monthDates = month.dates().toSet()
        val completedByHabit = completions
            .asSequence()
            .filter { it.completed && it.date in monthDates }
            .groupingBy { it.habitId }
            .eachCount()

        return habits
            .filter { habit -> month.dates().any(habit::isActiveOn) }
            .sortedBy { it.displayOrder }
            .map { habit -> HabitSummary(habit, completedByHabit[habit.id] ?: 0) }
    }

    fun categorySummaries(
        categories: List<Category>,
        habitSummaries: List<HabitSummary>,
    ): List<CategorySummary> {
        val byCategory = habitSummaries.groupBy { it.habit.categoryId }
        return categories
            .filter { category -> byCategory[category.id].orEmpty().isNotEmpty() }
            .sortedBy { it.displayOrder }
            .map { category ->
                val habits = byCategory[category.id].orEmpty()
                CategorySummary(
                    category = category,
                    goal = habits.sumOf { it.goal },
                    completed = habits.sumOf { it.completed },
                )
            }
    }

    fun dailyWeekSummaries(
        month: MonthKey,
        dailySummaries: List<DailySummary>,
    ): List<DailyWeekSummary> = (0..4).mapNotNull { index ->
        val dates = month.datesForWeek(index)
        if (dates.isEmpty()) return@mapNotNull null
        val summaries = dailySummaries.filter { it.date in dates }
        DailyWeekSummary(
            index = index,
            dates = dates,
            completed = summaries.sumOf { it.completed },
            goal = summaries.sumOf { it.activeHabits },
        )
    }

    fun weeklyBlockSummaries(
        month: MonthKey,
        habits: List<WeeklyHabit>,
        completions: List<WeeklyHabitCompletion>,
    ): List<WeeklyBlockSummary> {
        val completionKeys = completions
            .asSequence()
            .filter { it.completed }
            .map { it.weeklyHabitId to it.weekStartDate }
            .toSet()

        return month.weekStarts().mapIndexed { index, weekStart ->
            val weekDates = month.datesForWeek(index)
            val active = habits.filter { habit -> weekDates.any(habit::isActiveOn) }
            val completed = active.count { habit -> habit.id to weekStart in completionKeys }
                .coerceAtMost(active.size)
            WeeklyBlockSummary(index, weekStart, completed, active.size)
        }
    }

    fun monthlyProgress(habitSummaries: List<HabitSummary>): ProgressSummary = ProgressSummary(
        completed = habitSummaries.sumOf { it.completed },
        goal = habitSummaries.sumOf { it.goal },
    )

    fun weeklyOverall(blocks: List<WeeklyBlockSummary>): ProgressSummary = ProgressSummary(
        completed = blocks.sumOf { it.completed },
        goal = blocks.sumOf { it.goal },
    )
}
