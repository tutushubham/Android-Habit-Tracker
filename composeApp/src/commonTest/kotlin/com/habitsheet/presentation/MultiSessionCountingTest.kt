package com.habitsheet.presentation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.TodaySession
import com.habitsheet.domain.model.plannedHabitsOn
import com.habitsheet.domain.model.sessionToToggle
import com.habitsheet.domain.model.todaySessions
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The multi-session rule (see WORKBOOK_MAPPING.md): a day can hold several sessions of one habit
 * (different `planId`); each is one unit to do and to count. Every view must agree with
 * `plannedHabitsOn`: day, habit, category and month summaries, the Today list and the widget.
 */
class MultiSessionCountingTest {
    private val date = LocalDate(2026, 8, 26)
    private val month = MonthKey(2026, 8)

    private val run = DailyHabit(
        "run", "Run", "c-fit", 12, 0, true, LocalDate(2020, 1, 1), null, 0, 0, datedOnly = true,
    )

    /** One habit, two sessions on [date] ("run-a" done, "run-b" open) and one more session the day after. */
    private val snapshot = HabitSnapshot(
        categories = listOf(Category("c-fit", "Fitness", 0, true, 0)),
        dailyHabits = listOf(run),
        dayPlans = listOf(
            DayPlan("run", date, "Easy run", false, 1, "run-a"),
            DayPlan("run", date, "Mobility", false, 1, "run-b"),
            DayPlan("run", LocalDate(2026, 8, 27), "Long run", false, 1, "run-c"),
        ),
        dailyCompletions = listOf(DailyHabitCompletion("run", date, true, 2, planId = "run-a")),
    )
    private val state = snapshot.toUiState(month, date, date, false, 0, true, null)

    @Test
    fun resolverReturnsBothSessions() {
        assertEquals(listOf("run-a", "run-b"), snapshot.plannedHabitsOn(date).map { it.id })
    }

    @Test
    fun daySummaryCountsEachSession() {
        val day = state.daily.first { it.date == date }
        assertEquals(2, day.activeHabits)
        assertEquals(1, day.completed)
        assertEquals(1, day.notCompleted)
        assertEquals(0.5, day.percentage)
    }

    @Test
    fun habitSummaryCountsEachSession() {
        val summary = state.habits.single()
        assertEquals(3, summary.goal) // run-a, run-b, run-c
        assertEquals(1, summary.completed)
    }

    @Test
    fun categorySummaryCountsEachSession() {
        val category = state.categorySummaries.single()
        assertEquals(3, category.goal)
        assertEquals(1, category.completed)
        assertEquals(2, category.remaining)
    }

    @Test
    fun monthAndWeekProgressCountEachSession() {
        assertEquals(1, state.monthlyProgress.completed)
        assertEquals(3, state.monthlyProgress.goal)
        val weekWithBoth = state.dailyWeeks.first { date in it.dates }
        assertEquals(3, weekWithBoth.goal)
        assertEquals(1, weekWithBoth.completed)
    }

    @Test
    fun todayListHasOneRowPerSessionAndOnlyTheDoneOneIsDone() {
        assertEquals(1, state.todaySummary.completedCount)
        assertEquals(2, state.todaySummary.totalCount)
        assertEquals(listOf("run-a"), state.todaySummary.doneHabits.map { it.id })
        assertEquals(listOf("run-b"), state.todaySummary.leftHabits.map { it.id })
        assertEquals(listOf("run", "run"), state.todaySummary.doneHabits.map { it.habitId } + state.todaySummary.leftHabits.map { it.habitId })
    }

    @Test
    fun completingTheSecondSessionCompletesTheDay() {
        val both = snapshot.copy(
            dailyCompletions = snapshot.dailyCompletions + DailyHabitCompletion("run", date, true, 3, planId = "run-b"),
        ).toUiState(month, date, date, false, 0, true, null)
        val day = both.daily.first { it.date == date }
        assertEquals(2, day.completed)
        assertEquals(0, day.notCompleted)
        assertEquals(1.0, day.percentage)
        assertEquals(2, both.habits.single().completed)
    }

    // The widget shows today's sessions through the same resolver (shared code, tested here because the
    // widget itself lives in androidMain and cannot be unit-tested before P1-2 step 9).

    @Test
    fun widgetListHasOneRowPerSessionWithItsOwnDoneState() {
        assertEquals(
            listOf(
                TodaySession("run-a", "run", "Run · Easy run", completed = true),
                TodaySession("run-b", "run", "Run · Mobility", completed = false),
            ),
            snapshot.todaySessions(date),
        )
    }

    @Test
    fun widgetToggleTargetsTheTappedSession() {
        assertEquals("run-b", snapshot.sessionToToggle(date, planId = "run-b", habitId = "run")?.id)
        assertEquals("run-a", snapshot.sessionToToggle(date, planId = "run-a", habitId = "run")?.id)
    }

    @Test
    fun widgetToggleWithoutPlanIdFallsBackToTheHabitsFirstSession() {
        // Buttons placed by an older build only carry the habit id.
        assertEquals("run-a", snapshot.sessionToToggle(date, planId = null, habitId = "run")?.id)
    }

    @Test
    fun widgetToggleIgnoresUnknownSkippedAndNotDueSessions() {
        val withRest = snapshot.copy(dayPlans = snapshot.dayPlans + DayPlan("run", date, "Rest", true, 1, "run-rest"))
        assertNull(withRest.sessionToToggle(date, planId = "run-rest", habitId = "run"))
        assertNull(snapshot.sessionToToggle(date, planId = "nope", habitId = "run"))
        assertNull(snapshot.sessionToToggle(LocalDate(2026, 8, 28), planId = "run-a", habitId = "run"))
    }

    @Test
    fun singleSessionHabitsKeepTheirDefaultIds() {
        val simple = HabitSnapshot(
            dailyHabits = listOf(run.copy(datedOnly = false)),
            dailyCompletions = listOf(DailyHabitCompletion("run", date, true, 1)),
        )
        assertEquals(listOf(TodaySession("run|$date", "run", "Run", completed = true)), simple.todaySessions(date))
        assertEquals("run|$date", simple.sessionToToggle(date, null, "run")?.id)
    }
}
