package com.habitsheet.presentation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden (characterization) tests for [toUiState]: one fixed snapshot, every number worked out by hand.
 * If one of these changes, the month screen shows different numbers: that is a behaviour change.
 *
 * August 2026: 1 Aug is a Saturday; Mondays are 3, 10, 17, 24, 31; Wednesdays 5, 12, 19, 26.
 */
class MonthUiStateGoldenTest {
    private val august = MonthKey(2026, 8)
    private val today = LocalDate(2026, 8, 3)
    private fun d(day: Int) = LocalDate(2026, 8, day)

    private fun habit(
        id: String,
        order: Int,
        categoryId: String?,
        createdOn: LocalDate = LocalDate(2020, 1, 1),
        archivedOn: LocalDate? = null,
        datedOnly: Boolean = false,
    ) = DailyHabit(
        id = id, name = id.replaceFirstChar { it.uppercase() }, categoryId = categoryId, monthlyGoal = 20,
        displayOrder = order, active = archivedOn == null, createdOn = createdOn, archivedOn = archivedOn,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0, datedOnly = datedOnly,
    )

    private val snapshot = HabitSnapshot(
        categories = listOf(
            Category("c-study", "Study", 1, true, 0),
            Category("c-fit", "Fitness", 0, true, 0),
            Category("c-empty", "Empty", 2, true, 0),
        ),
        dailyHabits = listOf(
            habit("read", 0, "c-study"), // no schedule: due every day (31)
            habit("run", 1, "c-fit"), // Mon + Wed plans, 3 Aug overridden by dated rows
            habit("study", 2, "c-study", datedOnly = true), // dated sessions only
            habit("old", 3, "c-fit", archivedOn = d(11)), // due 1..10 (10)
            habit("later", 4, null, createdOn = d(20)), // due 20..31 (12)
            habit("sheet", 5, null), // sheet managed, no rows: active, nothing due
        ),
        weeklyPlans = listOf(
            WeeklyPlan("run", 1, "5k", 0),
            WeeklyPlan("run", 3, "intervals", 0),
        ),
        dayPlans = listOf(
            DayPlan("run", d(3), "Easy", false, 0, "run-a"),
            DayPlan("run", d(3), "Rest note", true, 0, "run-b"),
            DayPlan("study", d(5), "Chapter 1", false, 0, "study-1"),
            DayPlan("study", d(5), "Chapter 2", false, 0, "study-2"),
            DayPlan("study", d(6), "Revision", false, 0),
        ),
        dailyCompletions = listOf(
            DailyHabitCompletion("read", d(1), true, 1),
            DailyHabitCompletion("read", d(2), true, 1),
            DailyHabitCompletion("read", d(3), true, 1),
            DailyHabitCompletion("read", d(4), false, 1),
            DailyHabitCompletion("run", d(3), true, 1, planId = "run-a"),
            DailyHabitCompletion("run", d(10), true, 1),
            DailyHabitCompletion("study", d(5), true, 1, planId = "study-1"),
            DailyHabitCompletion("old", d(1), true, 1),
            DailyHabitCompletion("run", d(4), true, 1), // not due that day: counts nowhere except the raw key set
        ),
        weeklyHabits = listOf(
            WeeklyHabit("w1", "Long run", "c-fit", 0, true, LocalDate(2020, 1, 1), null, 0, 0),
        ),
        weeklyCompletions = listOf(WeeklyHabitCompletion("w1", d(1), true, 1)),
        sheetManagedHabitIds = setOf("sheet"),
    )

    private val state = snapshot.toUiState(august, today, today, false, 0, true, null)

    @Test
    fun habitSummariesCountSessionsDueAndDone() {
        assertEquals(
            listOf(
                Triple("read", 3, 31),
                Triple("run", 2, 9),
                Triple("study", 1, 3),
                Triple("old", 1, 10),
                Triple("later", 0, 12),
                Triple("sheet", 0, 0),
            ),
            state.habits.map { Triple(it.habit.id, it.completed, it.goal) },
        )
    }

    @Test
    fun monthlyProgressIsSumOfPlannedSessions() {
        assertEquals(7, state.monthlyProgress.completed)
        assertEquals(65, state.monthlyProgress.goal)
        assertEquals(65, state.dueKeys.size)
    }

    @Test
    fun categorySummariesAreOrderedAndSkipUncategorisedAndEmptyCategories() {
        assertEquals(
            listOf(Triple("c-fit", 19, 3), Triple("c-study", 34, 4)),
            state.categorySummaries.map { Triple(it.category.id, it.goal, it.completed) },
        )
        assertEquals(listOf("c-fit", "c-study", "c-empty"), state.categories.map { it.id })
    }

    @Test
    fun dailySummariesFollowThePlannedSessionsOfEachDay() {
        fun day(n: Int) = state.daily.first { it.date == d(n) }.let { Triple(it.activeHabits, it.completed, it.notCompleted) }
        assertEquals(31, state.daily.size)
        assertEquals(Triple(2, 2, 0), day(1)) // read, old
        assertEquals(Triple(3, 2, 1), day(3)) // read, run-a (run-b is a skipped note), old
        assertEquals(Triple(2, 0, 2), day(4)) // the completion of "run" on 4 Aug is not due
        assertEquals(Triple(5, 1, 4), day(5)) // read, run (Wed), study-1, study-2, old
        assertEquals(Triple(3, 0, 3), day(6)) // read, study default session, old
        assertEquals(Triple(3, 0, 3), day(31)) // read, later, run (Mon)
    }

    @Test
    fun dailyWeeksSumTheDailySummaries() {
        val first = state.dailyWeeks.first()
        assertEquals(0, first.index)
        assertEquals(19, first.goal)
        assertEquals(6, first.completed)
        assertEquals(listOf(7, 7, 7, 7, 3), state.dailyWeeks.map { it.dates.size })
    }

    @Test
    fun rawCompletionKeysAndDueKeysUseSessionIds() {
        assertEquals(8, state.dailyCompletionKeys.size)
        assertTrue("run-a" to d(3) in state.dailyCompletionKeys)
        assertTrue("run-a" to d(3) in state.dueKeys)
        assertTrue("run-b" to d(3) !in state.dueKeys)
        assertTrue("run|2026-08-04" in state.dailyCompletionKeys.map { it.first })
        assertTrue("run|2026-08-04" to d(4) !in state.dueKeys)
    }

    @Test
    fun todaySummaryListsDoneAndLeftSessionsWithoutSkippedNotes() {
        val summary = state.todaySummary
        assertEquals(d(3), summary.date)
        assertEquals(2, summary.completedCount)
        assertEquals(3, summary.totalCount)
        assertEquals(listOf("read|2026-08-03", "run-a"), summary.doneHabits.map { it.id })
        assertEquals(listOf("old|2026-08-03"), summary.leftHabits.map { it.id })
        assertEquals("Fitness", summary.doneHabits.last().categoryName)
    }

    @Test
    fun dayAndTomorrowPlansAndMonthPlan() {
        assertEquals(listOf("read|2026-08-03", "run-a", "run-b", "old|2026-08-03"), state.dayPlan.map { it.id })
        assertEquals(2, state.tomorrowPlan.size)
        assertEquals(31, state.monthPlan.size)
        assertEquals(5, state.monthPlan.getValue(d(5)).size)
    }

    @Test
    fun weeklyBlocksAndKeys() {
        assertEquals(listOf(1, 0, 0, 0, 0), state.weeklyBlocks.map { it.completed })
        assertEquals(listOf(1, 1, 1, 1, 1), state.weeklyBlocks.map { it.goal })
        assertEquals(1, state.weeklyProgress.completed)
        assertEquals(5, state.weeklyProgress.goal)
        assertEquals(setOf("w1" to d(1)), state.weeklyCompletionKeys)
    }

    @Test
    fun passThroughFields() {
        assertEquals(august, state.selectedMonth)
        assertEquals(today, state.today)
        assertEquals(today, state.selectedDay)
        assertEquals(snapshot.dayPlans, state.dayPlans)
        assertEquals(snapshot.weeklyPlans, state.weeklyPlans)
        assertEquals(6, state.allDailyHabits.size)
        assertEquals(false, state.isEmpty)
    }

    @Test
    fun otherMonthDoesNotLeakCompletionsOrPlans() {
        val september = snapshot.toUiState(MonthKey(2026, 9), LocalDate(2026, 9, 1), LocalDate(2026, 9, 1), false, 0, true, null)
        assertEquals(0, september.dailyCompletionKeys.size)
        assertEquals(0, september.weeklyCompletionKeys.size)
        assertEquals(0, september.monthlyProgress.completed)
        // "old" was archived on 11 Aug, "later"/"read" are due every day, "run" on Mondays and Wednesdays.
        assertEquals(listOf("read", "run", "study", "later", "sheet"), september.habits.map { it.habit.id })
    }
}
