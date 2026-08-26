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
}
