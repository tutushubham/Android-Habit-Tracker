package com.habitsheet.domain.calculation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitCalculationsTest {
    private val august = MonthKey(2026, 8)

    @Test
    fun dailyCompletionCountAndPercentageMatchWorkbook() {
        val habits = listOf(daily("a", 20), daily("b", 10), daily("c", 5))
        val date = LocalDate(2026, 8, 3)
        val completions = listOf(completion("a", date), completion("b", date))

        val summary = HabitCalculations.dailySummaries(august, habits, completions).first { it.date == date }

        assertEquals(2, summary.completed)
        assertEquals(1, summary.notCompleted)
        assertEquals(2.0 / 3.0, summary.percentage, 0.0001)
    }

    @Test
    fun completionCountCannotExceedPopulatedHabits() {
        val date = LocalDate(2026, 8, 3)
        val habits = listOf(daily("a", 20))
        val duplicates = listOf(completion("a", date), completion("a", date))

        val summary = HabitCalculations.dailySummaries(august, habits, duplicates).first { it.date == date }

        assertEquals(1, summary.completed)
        assertEquals(0, summary.notCompleted)
    }

    @Test
    fun habitPercentageMayExceedOneHundredPercent() {
        val habit = daily("a", goal = 2)
        val completions = (1..3).map { day -> completion("a", LocalDate(2026, 8, day)) }

        val summary = HabitCalculations.habitSummaries(august, listOf(habit), completions).single()

        assertEquals(3, summary.completed)
        assertEquals(1.5, summary.percentage)
    }

    @Test
    fun categoryAggregatesGoalCompletionAndClampedRemaining() {
        val category = Category("fitness", "Fitness 💪", 0, true, 0)
        val summaries = listOf(
            HabitSummary(daily("a", 2, category.id), completed = 3),
            HabitSummary(daily("b", 4, category.id), completed = 4),
        )

        val result = HabitCalculations.categorySummaries(listOf(category), summaries).single()

        assertEquals(6, result.goal)
        assertEquals(7, result.completed)
        assertEquals(0, result.remaining)
        assertEquals(7.0 / 6.0, result.percentage, 0.0001)
    }

    @Test
    fun monthlyCompletionUsesSumOfHabitGoals() {
        val progress = HabitCalculations.monthlyProgress(
            listOf(
                HabitSummary(daily("a", 20), 12),
                HabitSummary(daily("b", 10), 8),
            ),
        )

        assertEquals(20, progress.completed)
        assertEquals(30, progress.goal)
        assertEquals(2.0 / 3.0, progress.percentage, 0.0001)
    }

    @Test
    fun zeroGoalCasesAreNeutral() {
        val progress = ProgressSummary(completed = 0, goal = 0)
        val emptyDaily = HabitCalculations.dailySummaries(august, emptyList(), emptyList()).first()

        assertFalse(progress.hasGoal)
        assertEquals(0.0, progress.percentage)
        assertEquals(0.0, emptyDaily.percentage)
    }

    @Test
    fun dailyWeekBlocksUseRealDaysOnly() {
        val habit = daily("a", 31)
        val daily = HabitCalculations.dailySummaries(august, listOf(habit), emptyList())
        val blocks = HabitCalculations.dailyWeekSummaries(august, daily)

        assertEquals(listOf(7, 7, 7, 7, 3), blocks.map { it.dates.size })
        assertEquals(listOf(7, 7, 7, 7, 3), blocks.map { it.goal })
    }

    @Test
    fun weeklyCompletionAndOverallMatchWorkbookConcept() {
        val habitA = weekly("a")
        val habitB = weekly("b")
        val week1 = LocalDate(2026, 8, 1)
        val week2 = LocalDate(2026, 8, 8)
        val completions = listOf(
            WeeklyHabitCompletion("a", week1, true, 0),
            WeeklyHabitCompletion("b", week1, true, 0),
            WeeklyHabitCompletion("a", week2, true, 0),
        )

        val blocks = HabitCalculations.weeklyBlockSummaries(august, listOf(habitA, habitB), completions)
        val overall = HabitCalculations.weeklyOverall(blocks)

        assertEquals(2, blocks[0].completed)
        assertEquals(2, blocks[0].goal)
        assertEquals(3, overall.completed)
        assertEquals(10, overall.goal)
        assertEquals(0.3, overall.percentage, 0.0001)
    }

    @Test
    fun historyRespectsCreationAndArchiveDates() {
        val createdInSeptember = daily("new", 10).copy(createdOn = LocalDate(2026, 9, 1))
        val archivedAtSeptember = daily("old", 10).copy(
            archivedOn = LocalDate(2026, 9, 1),
            active = false,
        )

        val augustHabits = HabitCalculations.habitSummaries(
            MonthKey(2026, 8),
            listOf(createdInSeptember, archivedAtSeptember),
            emptyList(),
        )
        val septemberHabits = HabitCalculations.habitSummaries(
            MonthKey(2026, 9),
            listOf(createdInSeptember, archivedAtSeptember),
            emptyList(),
        )

        assertEquals(listOf("old"), augustHabits.map { it.habit.id })
        assertEquals(listOf("new"), septemberHabits.map { it.habit.id })
    }

    @Test
    fun dailyShareSummaryContainsCorrectHabitLists() {
        val cat = Category("cat1", "Category", 0, true, 0)
        val habits = listOf(
            daily("h1", 10, cat.id),
            daily("h2", 10, cat.id),
            daily("h3", 10, null)
        )
        val date = LocalDate(2026, 8, 26)
        val completions = listOf(
            completion("h1", date),
            completion("h3", date)
        )

        val result = HabitCalculations.dailyShareSummary(date, habits, listOf(cat), completions)

        assertEquals(2, result.completedCount)
        assertEquals(3, result.totalCount)
        assertEquals(2.0 / 3.0, result.percentage, 0.0001)
        
        assertEquals(2, result.doneHabits.size)
        assertTrue(result.doneHabits.any { it.name == "h1" && it.categoryName == "Category" })
        assertTrue(result.doneHabits.any { it.name == "h3" && it.categoryName == null })
        
        assertEquals(1, result.leftHabits.size)
        assertEquals("h2", result.leftHabits[0].name)
    }

    private fun daily(id: String, goal: Int, categoryId: String? = null) = DailyHabit(
        id = id,
        name = id,
        categoryId = categoryId,
        monthlyGoal = goal,
        displayOrder = id.hashCode(),
        active = true,
        createdOn = LocalDate(2020, 1, 1),
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    private fun weekly(id: String) = WeeklyHabit(
        id = id,
        name = id,
        categoryId = null,
        displayOrder = id.hashCode(),
        active = true,
        createdOn = LocalDate(2020, 1, 1),
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    private fun completion(id: String, date: LocalDate) = DailyHabitCompletion(id, date, true, 0)
}
