package com.habitsheet.domain.model

import com.habitsheet.testing.buildSnapshot
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanResolverTest {
    @Test
    fun weeklyScheduleAndDatedOverrideControlDueDays() {
        val tuesday = LocalDate(2026, 10, 6)
        val wednesday = LocalDate(2026, 10, 7)
        val base = buildSnapshot {
            dailyHabit("run", "Run", goal = 12, createdOn = LocalDate(2026, 10, 1))
            weeklyPlan("run", weekday = 2, "Easy · 5 km")
        }
        assertEquals("Easy · 5 km", base.plannedHabitsOn(tuesday).single().detail)
        assertTrue(base.plannedHabitsOn(wednesday).isEmpty())

        val override = base.copy(dayPlans = buildSnapshot { dayPlan("run", wednesday, "Intervals · 6 km") }.dayPlans)
        assertEquals("Intervals · 6 km", override.plannedHabitsOn(wednesday).single().detail)
        assertFalse(override.plannedHabitsOn(wednesday).single().skipped)
        val skipped = override.copy(dayPlans = buildSnapshot { dayPlan("run", tuesday, "Easy · 5 km", skipped = true) }.dayPlans)
        assertTrue(skipped.plannedHabitsOn(tuesday).single().skipped)
    }

    @Test
    fun existingUnscheduledHabitRemainsDaily() {
        val snapshot = buildSnapshot { dailyHabit("run", "Run", goal = 12, createdOn = LocalDate(2026, 10, 1)) }
        assertEquals("run", snapshot.plannedHabitsOn(LocalDate(2026, 10, 7)).single().habit.id)
    }

    @Test
    fun sheetManagedHabitIsDueOnlyOnDatedRows() {
        val date = LocalDate(2026, 10, 7)
        val snapshot = buildSnapshot {
            dailyHabit("run", "Run", goal = 12, createdOn = LocalDate(2026, 10, 1))
            dayPlan("run", date, "Intervals · 6 km")
            sheetManaged("run")
        }
        assertEquals("Intervals · 6 km", snapshot.plannedHabitsOn(date).single().detail)
        assertTrue(snapshot.plannedHabitsOn(LocalDate(2026, 10, 8)).isEmpty())
    }

    @Test
    fun multipleSessionsForOneHabitHaveSeparateIdentities() {
        val date = LocalDate(2026, 10, 7)
        val snapshot = buildSnapshot {
            dailyHabit("run", "Run", goal = 12, createdOn = LocalDate(2026, 10, 1))
            dayPlan("run", date, "Easy 5 km", id = "run-a")
            dayPlan("run", date, "Strength 20 min", id = "run-b")
        }
        assertEquals(listOf("run-a", "run-b"), snapshot.plannedHabitsOn(date).map { it.id })
    }

    @Test
    fun datedOnlyHabitIsNotDueBeforeItsFirstSession() {
        val november = LocalDate(2026, 11, 3)
        val snapshot = buildSnapshot {
            dailyHabit("mobility", "Mobility", goal = 12, createdOn = LocalDate(2026, 10, 1), datedOnly = true)
            dayPlan("mobility", november, "Ankle mobility")
        }
        assertTrue(snapshot.plannedHabitsOn(LocalDate(2026, 10, 1)).isEmpty())
        assertEquals(1, snapshot.plannedHabitsOn(november).size)
    }

    @Test
    fun winterArcStyleDaySupportsWorkoutStudyAndHabitsTogether() {
        val date = LocalDate(2026, 10, 1)
        val snapshot = buildSnapshot {
            dailyHabit("run", "Run", goal = 12, createdOn = date, datedOnly = true)
            dailyHabit("study", "Study", goal = 30, createdOn = date, datedOnly = true)
            dailyHabit("wake", "Wake Early", goal = 30, createdOn = date, datedOnly = true)
            dailyHabit("junk", "No Sugar", goal = 30, createdOn = date, kind = HabitKind.AVOIDANCE, datedOnly = true)
            dailyHabit("workout", "Workout", goal = 12, createdOn = date, datedOnly = true)
            dayPlan("run", date, "Easy 6 km", id = "run-1")
            dayPlan("study", date, "20 min notes", id = "study-1")
            dayPlan("wake", date, "06:30", id = "wake-1")
            dayPlan("junk", date, "Keep the day clean", id = "junk-1")
        }
        assertEquals(
            listOf("run", "study", "wake", "junk"),
            snapshot.plannedHabitsOn(date).map { it.habit.id },
        )
        assertTrue(snapshot.plannedHabitsOn(LocalDate(2026, 10, 2)).isEmpty())
    }

    @Test
    fun restDayShowsSkippedSessionsWithoutMakingThemDue() {
        val date = LocalDate(2026, 10, 5)
        val snapshot = buildSnapshot {
            dailyHabit("workout", "Workout", goal = 12, createdOn = LocalDate(2026, 10, 1), datedOnly = true)
            dailyHabit("study", "Study", goal = 12, createdOn = LocalDate(2026, 10, 1), datedOnly = true)
            dayPlan("workout", date, "Rest / recovery", skipped = true, id = "workout-rest")
            dayPlan("study", date, "20 min notes", id = "study-1")
        }
        val planned = snapshot.plannedHabitsOn(date)
        assertEquals(2, planned.size)
        assertTrue(planned.single { it.habit.id == "workout" }.skipped)
        assertFalse(planned.single { it.habit.id == "study" }.skipped)
    }
}
