package com.habitsheet.domain

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.calculation.HabitCalculations
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchivingTest {
    private fun testHabit(id: String, createdOn: LocalDate) =
        buildSnapshot { dailyHabit(id, "Test Habit", goal = 10, createdOn = createdOn) }.dailyHabits.single()

    @Test
    fun archiveAndRestoreDailyHabit() = runTest {
        val repository = InMemoryHabitRepository()
        val habitId = "habit-1"
        val habit = testHabit(habitId, createdOn = LocalDate(2026, 8, 1))
        repository.saveDailyHabit(habit)

        // 1. Verify initially active
        assertTrue(repository.snapshot.value.dailyHabits.first { it.id == habitId }.active)

        // 2. Archive
        val archiveDate = LocalDate(2026, 8, 15)
        repository.archiveDailyHabit(habitId, archiveDate, 1000)

        val archived = repository.snapshot.value.dailyHabits.first { it.id == habitId }
        assertFalse(archived.active)
        assertEquals(archiveDate, archived.archivedOn)

        // 3. Verify archived habit not active on archive date or after
        assertFalse(archived.isActiveOn(archiveDate))
        assertFalse(archived.isActiveOn(LocalDate(2026, 8, 16)))

        // 4. Verify still active BEFORE archive date
        assertTrue(archived.isActiveOn(LocalDate(2026, 8, 14)))

        // 5. Restore
        repository.restoreDailyHabit(habitId, 2000)
        val restored = repository.snapshot.value.dailyHabits.first { it.id == habitId }
        assertTrue(restored.active)
        assertEquals(null, restored.archivedOn)

        // 6. Verify active on all dates after creation
        assertTrue(restored.isActiveOn(archiveDate))
    }

    @Test
    fun archivedHabitPreservesHistoricalData() = runTest {
        val repository = InMemoryHabitRepository()
        val habitId = "habit-1"
        val date1 = LocalDate(2026, 8, 1)
        val date2 = LocalDate(2026, 8, 2)

        val habit = testHabit(habitId, createdOn = date1)
        repository.saveDailyHabit(habit)
        repository.setDailyCompletion(DailyHabitCompletion(habitId, date1, true, 0))

        // Archive
        repository.archiveDailyHabit(habitId, date2, 1000)

        // Verify completion still exists
        val completions = repository.snapshot.value.dailyCompletions
        assertTrue(completions.any { it.habitId == habitId && it.date == date1 && it.completed })

        // Verify calculation for past month (when it was active) still includes it
        val august = MonthKey(2026, 8)
        val snapshot = repository.snapshot.value
        val habitSummaries = HabitCalculations.habitSummaries(august, snapshot.dailyHabits, snapshot.dailyCompletions)

        // It was active on some days in August, so it should be in summaries
        assertTrue(habitSummaries.any { it.habit.id == habitId })
        assertEquals(1, habitSummaries.first { it.habit.id == habitId }.completed)
    }

    @Test
    fun archivedHabitDoesNotAffectCurrentActiveCalculations() = runTest {
        val repository = InMemoryHabitRepository()
        val habitId = "habit-1"
        val archiveDate = LocalDate(2026, 8, 1) // Archived at start of month

        val habit = testHabit(habitId, createdOn = LocalDate(2026, 7, 1))
        repository.saveDailyHabit(habit)
        repository.archiveDailyHabit(habitId, archiveDate, 1000)

        val august = MonthKey(2026, 8)
        val snapshot = repository.snapshot.value

        // It is NOT active on any day in August
        val habitSummaries = HabitCalculations.habitSummaries(august, snapshot.dailyHabits, snapshot.dailyCompletions)
        assertFalse(habitSummaries.any { it.habit.id == habitId })

        // Daily summaries for August should not include it
        val dailySummaries = HabitCalculations.dailySummaries(august, snapshot.dailyHabits, snapshot.dailyCompletions)
        assertTrue(dailySummaries.all { it.activeHabits == 0 })
    }
}
