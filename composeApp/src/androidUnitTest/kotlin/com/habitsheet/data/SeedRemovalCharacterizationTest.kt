package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins repository start-up behaviour that must survive removal of the OND seed (plan P0-A). */
class SeedRemovalCharacterizationTest {
    private fun withDatabase(block: suspend (url: String) -> Unit) = runTest {
        val file = Files.createTempFile("habit-sheet-seed-removal", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also {
            HabitsDatabase.Schema.create(it)
            it.close()
        }
        try {
            block(url)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun freshDatabaseWithoutSeedingHasOnlyDefaultCategories() = withDatabase { url ->
        val repository = LocalHabitRepository({ JdbcSqliteDriver(url) })
        val snapshot = repository.snapshot.value
        assertEquals(DefaultData.categories(0).map { it.id to it.name }, snapshot.categories.map { it.id to it.name })
        assertTrue(snapshot.dailyHabits.isEmpty())
        assertTrue(snapshot.weeklyHabits.isEmpty())
        assertTrue(snapshot.weeklyPlans.isEmpty())
        assertTrue(snapshot.dayPlans.isEmpty())
        assertTrue(snapshot.dailyCompletions.isEmpty())
        assertTrue(snapshot.weeklyCompletions.isEmpty())
        repository.close()
    }

    @Test
    fun alreadySeededDatabaseIsUntouchedByRepositoryInit() = withDatabase { url ->
        // Build an install that looks like one which already ran the OND seed.
        val first = LocalHabitRepository({ JdbcSqliteDriver(url) })
        // "Run" is a seed-era name deliberately left datedOnly=false to prove it is not rewritten.
        val run = DailyHabit(
            id = "ond-2026-run", name = "Run", categoryId = "category-4", monthlyGoal = 12,
            displayOrder = 0, active = true, createdOn = LocalDate(2026, 10, 1),
            createdAtEpochMillis = 1, updatedAtEpochMillis = 1, datedOnly = false,
        )
        val custom = DailyHabit(
            id = "mine", name = "Read", categoryId = null, monthlyGoal = 20, displayOrder = 1,
            active = true, createdOn = LocalDate(2026, 9, 1), createdAtEpochMillis = 2, updatedAtEpochMillis = 2,
        )
        first.saveDailyHabit(run)
        first.saveDailyHabit(custom)
        first.saveWeeklyPlan(WeeklyPlan(run.id, 2, "My own detail", 3))
        first.saveDayPlan(DayPlan(run.id, LocalDate(2026, 10, 18), "Edited session", false, 4, "plan-1"))
        first.setDailyCompletion(DailyHabitCompletion(run.id, LocalDate(2026, 10, 18), true, 5, "plan-1"))
        first.setDailyCompletion(DailyHabitCompletion(custom.id, LocalDate(2026, 9, 2), true, 6))
        first.close()

        JdbcSqliteDriver(url).also { driver ->
            val queries = HabitsDatabase(driver).habitsQueries
            listOf("ond_2026_seeded", "winter_arc_routines_seeded", "winter_arc_dated_only")
                .forEach { queries.setSetting(it, 1L) }
            driver.close()
        }

        val before = LocalHabitRepository({ JdbcSqliteDriver(url) }).run {
            snapshot.value.also { close() }
        }
        val after = LocalHabitRepository({ JdbcSqliteDriver(url) }).run {
            snapshot.value.also { close() }
        }
        assertEquals(before, after)
        assertEquals(2, after.dailyHabits.size)
        assertEquals(false, after.dailyHabits.first { it.id == run.id }.datedOnly)
    }
}
