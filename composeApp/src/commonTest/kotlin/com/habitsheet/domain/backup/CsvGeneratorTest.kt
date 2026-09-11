package com.habitsheet.domain.backup

import com.habitsheet.domain.model.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvGeneratorTest {

    @Test
    fun generateCsvWithDailyAndWeeklyHabits() {
        val category = Category("cat1", "Fitness 💪", 0, true, 1000)
        val dailyHabit = DailyHabit(
            "d1", "Run, Forest, Run", category.id, 10, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000
        )
        val dailyCompletion = DailyHabitCompletion("d1", LocalDate(2026, 8, 26), true, 1000)
        
        val weeklyHabit = WeeklyHabit(
            "w1", "Gym \"Special\"", null, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000
        )
        val weeklyCompletion = WeeklyHabitCompletion("w1", LocalDate(2026, 8, 24), true, 1000)

        val snapshot = HabitSnapshot(
            categories = listOf(category),
            dailyHabits = listOf(dailyHabit),
            dailyCompletions = listOf(dailyCompletion),
            weeklyHabits = listOf(weeklyHabit),
            weeklyCompletions = listOf(weeklyCompletion)
        )

        val csv = CsvGenerator.generate(snapshot)
        val lines = csv.split("\n").filter { it.isNotBlank() }

        // Header + 2 data rows
        assertEquals(3, lines.size)
        assertEquals("Date,Habit,Category,Completed,Frequency", lines[0])
        
        // Verify escaping for commas
        assertTrue(lines.any { it.contains("\"Run, Forest, Run\"") && it.contains("Fitness 💪") && it.contains("Daily") })
        
        // Verify escaping for quotes
        assertTrue(lines.any { it.contains("\"Gym \"\"Special\"\"\"") && it.contains("Weekly") })
        
        // Verify daily completion
        assertTrue(lines.any { it.startsWith("2026-08-26") && it.contains("true") })
    }

    @Test
    fun handleEmptyValues() {
        val snapshot = HabitSnapshot()
        val csv = CsvGenerator.generate(snapshot)
        assertEquals("Date,Habit,Category,Completed,Frequency\n", csv)
    }

    @Test
    fun rowsHaveStableChronologicalOrder() {
        val habit = DailyHabit(
            "d1", "Read", null, 10, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000,
        )
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(habit),
            dailyCompletions = listOf(
                DailyHabitCompletion("d1", LocalDate(2026, 8, 20), true, 1000),
                DailyHabitCompletion("d1", LocalDate(2026, 8, 2), false, 1000),
            ),
        )

        val rows = CsvGenerator.generate(snapshot).lineSequence().filter { it.isNotEmpty() }.toList()

        assertTrue(rows[1].startsWith("2026-08-02"))
        assertTrue(rows[2].startsWith("2026-08-20"))
    }

    @Test
    fun userTextCannotBecomeSpreadsheetFormula() {
        val category = Category("cat1", "@malicious", 0, true, 1000)
        val habit = DailyHabit(
            "d1", "=SUM(1,1)", category.id, 10, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000,
        )
        val snapshot = HabitSnapshot(
            categories = listOf(category),
            dailyHabits = listOf(habit),
            dailyCompletions = listOf(
                DailyHabitCompletion("d1", LocalDate(2026, 8, 2), true, 1000),
            ),
        )

        val csv = CsvGenerator.generate(snapshot)

        assertTrue(csv.contains("\"'=SUM(1,1)\""))
        assertTrue(csv.contains("'@malicious"))
    }

    @Test
    fun carriageReturnsAreQuoted() {
        val habit = DailyHabit(
            "d1", "Morning\rRun", null, 10, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000,
        )
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(habit),
            dailyCompletions = listOf(
                DailyHabitCompletion("d1", LocalDate(2026, 8, 2), true, 1000),
            ),
        )

        assertTrue(CsvGenerator.generate(snapshot).contains("\"Morning\rRun\""))
    }
}
