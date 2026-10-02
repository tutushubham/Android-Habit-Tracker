package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanResolverTest {
    private val run = DailyHabit("run", "Run", null, 12, 0, true, LocalDate(2026, 10, 1), null, 1, 1)

    @Test
    fun weeklyScheduleAndDatedOverrideControlDueDays() {
        val tuesday = LocalDate(2026, 10, 6)
        val wednesday = LocalDate(2026, 10, 7)
        val base = HabitSnapshot(dailyHabits = listOf(run), weeklyPlans = listOf(WeeklyPlan("run", 2, "Easy · 5 km", 1)))
        assertEquals("Easy · 5 km", base.plannedHabitsOn(tuesday).single().detail)
        assertTrue(base.plannedHabitsOn(wednesday).isEmpty())

        val override = base.copy(dayPlans = listOf(DayPlan("run", wednesday, "Intervals · 6 km", false, 2)))
        assertEquals("Intervals · 6 km", override.plannedHabitsOn(wednesday).single().detail)
        assertFalse(override.plannedHabitsOn(wednesday).single().skipped)
        val skipped = override.copy(dayPlans = listOf(DayPlan("run", tuesday, "Easy · 5 km", true, 3)))
        assertTrue(skipped.plannedHabitsOn(tuesday).single().skipped)
    }

    @Test
    fun existingUnscheduledHabitRemainsDaily() {
        val snapshot = HabitSnapshot(dailyHabits = listOf(run))
        assertEquals("run", snapshot.plannedHabitsOn(LocalDate(2026, 10, 7)).single().habit.id)
    }

    @Test
    fun sheetManagedHabitIsDueOnlyOnDatedRows() {
        val date = LocalDate(2026, 10, 7)
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(run),
            dayPlans = listOf(DayPlan("run", date, "Intervals · 6 km", false, 2)),
            sheetManagedHabitIds = setOf("run"),
        )
        assertEquals("Intervals · 6 km", snapshot.plannedHabitsOn(date).single().detail)
        assertTrue(snapshot.plannedHabitsOn(LocalDate(2026, 10, 8)).isEmpty())
    }

    @Test
    fun multipleSessionsForOneHabitHaveSeparateIdentities() {
        val date = LocalDate(2026, 10, 7)
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(run),
            dayPlans = listOf(
                DayPlan("run", date, "Easy 5 km", false, 1, "run-a"),
                DayPlan("run", date, "Strength 20 min", false, 1, "run-b"),
            ),
        )
        assertEquals(listOf("run-a", "run-b"), snapshot.plannedHabitsOn(date).map { it.id })
    }

    @Test
    fun datedOnlyHabitIsNotDueBeforeItsFirstSession() {
        val mobility = run.copy(id = "mobility", name = "Mobility", datedOnly = true)
        val november = LocalDate(2026, 11, 3)
        val snapshot = HabitSnapshot(dailyHabits = listOf(mobility),
            dayPlans = listOf(DayPlan("mobility", november, "Ankle mobility", false, 1)))
        assertTrue(snapshot.plannedHabitsOn(LocalDate(2026, 10, 1)).isEmpty())
        assertEquals(1, snapshot.plannedHabitsOn(november).size)
    }

    @Test
    fun winterArcStyleDaySupportsWorkoutStudyAndHabitsTogether() {
        val date = LocalDate(2026, 10, 1)
        val habits = listOf(
            run.copy(datedOnly = true),
            DailyHabit("study", "Study", null, 30, 1, true, date, null, 1, 1, datedOnly = true),
            DailyHabit("wake", "Wake Early", null, 30, 2, true, date, null, 1, 1, datedOnly = true),
            DailyHabit("junk", "No Sugar", null, 30, 3, true, date, null, 1, 1, HabitKind.AVOIDANCE, datedOnly = true),
            DailyHabit("workout", "Workout", null, 12, 4, true, date, null, 1, 1, datedOnly = true),
        )
        val snapshot = HabitSnapshot(
            dailyHabits = habits,
            dayPlans = listOf(
                DayPlan("run", date, "Easy 6 km", false, 1, "run-1"),
                DayPlan("study", date, "20 min notes", false, 1, "study-1"),
                DayPlan("wake", date, "06:30", false, 1, "wake-1"),
                DayPlan("junk", date, "Keep the day clean", false, 1, "junk-1"),
            ),
        )
        assertEquals(
            listOf("run", "study", "wake", "junk"),
            snapshot.plannedHabitsOn(date).map { it.habit.id },
        )
        assertTrue(snapshot.plannedHabitsOn(LocalDate(2026, 10, 2)).isEmpty())
    }

    @Test
    fun restDayShowsSkippedSessionsWithoutMakingThemDue() {
        val date = LocalDate(2026, 10, 5)
        val workout = run.copy(id = "workout", name = "Workout", datedOnly = true)
        val study = run.copy(id = "study", name = "Study", datedOnly = true)
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(workout, study),
            dayPlans = listOf(
                DayPlan("workout", date, "Rest / recovery", true, 1, "workout-rest"),
                DayPlan("study", date, "20 min notes", false, 1, "study-1"),
            ),
        )
        val planned = snapshot.plannedHabitsOn(date)
        assertEquals(2, planned.size)
        assertTrue(planned.single { it.habit.id == "workout" }.skipped)
        assertFalse(planned.single { it.habit.id == "study" }.skipped)
    }
}
