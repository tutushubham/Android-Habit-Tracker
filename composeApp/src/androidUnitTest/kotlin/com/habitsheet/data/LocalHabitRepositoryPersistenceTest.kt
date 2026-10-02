package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupValidationException
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalHabitRepositoryPersistenceTest {
    @Test
    fun ondSeedReusesWorkoutAndPreservesCompletion() = runTest {
        val databaseFile = Files.createTempFile("habit-sheet-ond-seed", ".db")
        val url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
        val bootstrap = JdbcSqliteDriver(url)
        HabitsDatabase.Schema.create(bootstrap)
        bootstrap.close()
        try {
            val first = LocalHabitRepository({ JdbcSqliteDriver(url) }, seedOndPlan = false)
            first.saveDailyHabit(DailyHabit(
                id = "existing-workout", name = "Workout", categoryId = "category-4",
                monthlyGoal = 20, displayOrder = 0, active = true,
                createdOn = LocalDate(2026, 9, 1), createdAtEpochMillis = 1, updatedAtEpochMillis = 1,
            ))
            val completion = DailyHabitCompletion("existing-workout", LocalDate(2026, 9, 30), true, 2)
            first.setDailyCompletion(completion)
            first.close()

            val seeded = LocalHabitRepository({ JdbcSqliteDriver(url) })
            val snapshot = seeded.snapshot.value
            assertEquals(1, snapshot.dailyHabits.count { it.name == "Workout" })
            assertEquals(completion, snapshot.dailyCompletions.single())
            assertTrue(snapshot.dailyHabits.any { it.name == "Run" })
            assertTrue(snapshot.dailyHabits.any { it.name == "Android" })
            assertTrue(snapshot.dayPlans.any { it.date == LocalDate(2026, 10, 18) && it.detail.contains("Half marathon") })
            assertTrue(snapshot.dayPlans.any { it.date == LocalDate(2026, 10, 15) && it.skipped && it.habitId == "ond-2026-run" })
            val planCount = snapshot.dayPlans.size
            seeded.close()

            val reopened = LocalHabitRepository({ JdbcSqliteDriver(url) })
            assertEquals(planCount, reopened.snapshot.value.dayPlans.size)
            reopened.close()
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun completionSurvivesRepositoryRestart() = runTest {
        val databaseFile = Files.createTempFile("habit-sheet-persistence", ".db")
        val url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
        val bootstrap = JdbcSqliteDriver(url)
        HabitsDatabase.Schema.create(bootstrap)
        bootstrap.close()

        try {
            val first = LocalHabitRepository({ JdbcSqliteDriver(url) }, seedOndPlan = false)
            val habit = DailyHabit(
                id = "read",
                name = "Read",
                categoryId = null,
                monthlyGoal = 20,
                displayOrder = 0,
                active = true,
                createdOn = LocalDate(2026, 8, 1),
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            )
            val completion = DailyHabitCompletion(
                habitId = habit.id,
                date = LocalDate(2026, 8, 5),
                completed = true,
                updatedAtEpochMillis = 2,
            )
            first.saveDailyHabit(habit)
            first.setDailyCompletion(completion)
            val weeklyPlan = WeeklyPlan(habit.id, 3, "Tempo · 5 km", 3)
            val dayPlan = DayPlan(habit.id, LocalDate(2026, 8, 5), "Easy · 6 km", false, 4)
            first.saveWeeklyPlan(weeklyPlan)
            first.saveDayPlan(dayPlan)
            first.close()

            val reopened = LocalHabitRepository({ JdbcSqliteDriver(url) }, seedOndPlan = false)
            assertEquals(listOf(completion), reopened.snapshot.value.dailyCompletions)
            assertEquals(listOf(weeklyPlan), reopened.snapshot.value.weeklyPlans)
            assertEquals(listOf(dayPlan), reopened.snapshot.value.dayPlans)
            reopened.close()
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun restoredBackupSurvivesRepositoryRestart() = runTest {
        val databaseFile = Files.createTempFile("habit-sheet-restore", ".db")
        val url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
        val bootstrap = JdbcSqliteDriver(url)
        HabitsDatabase.Schema.create(bootstrap)
        bootstrap.close()

        val category = Category("fitness", "Fitness", 0, true, 1000)
        val dailyHabit = DailyHabit(
            id = "run",
            name = "Run",
            categoryId = category.id,
            monthlyGoal = 12,
            displayOrder = 0,
            active = true,
            createdOn = LocalDate(2026, 8, 1),
            createdAtEpochMillis = 1000,
            updatedAtEpochMillis = 1000,
        )
        val weeklyHabit = WeeklyHabit(
            id = "gym",
            name = "Gym",
            categoryId = category.id,
            displayOrder = 0,
            active = true,
            createdOn = LocalDate(2026, 8, 1),
            createdAtEpochMillis = 1000,
            updatedAtEpochMillis = 1000,
        )
        val backup = HabitSnapshot(
            categories = listOf(category),
            dailyHabits = listOf(dailyHabit),
            dailyCompletions = listOf(
                DailyHabitCompletion("run", LocalDate(2026, 8, 5), true, 2000),
            ),
            weeklyHabits = listOf(weeklyHabit),
            weeklyCompletions = listOf(
                WeeklyHabitCompletion("gym", LocalDate(2026, 8, 1), true, 2000),
            ),
        )

        try {
            val repository = LocalHabitRepository({ JdbcSqliteDriver(url) }, seedOndPlan = false)
            repository.restoreFromSnapshot(backup)
            assertEquals(backup, repository.snapshot.value)
            repository.close()

            val reopened = LocalHabitRepository({ JdbcSqliteDriver(url) }, seedOndPlan = false)
            assertEquals(backup, reopened.snapshot.value)
            reopened.close()
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun invalidRestoreDoesNotClearExistingDatabase() = runTest {
        val databaseFile = Files.createTempFile("habit-sheet-invalid-restore", ".db")
        val url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
        val bootstrap = JdbcSqliteDriver(url)
        HabitsDatabase.Schema.create(bootstrap)
        bootstrap.close()

        try {
            val repository = LocalHabitRepository({ JdbcSqliteDriver(url) }, seedOndPlan = false)
            val existing = DailyHabit(
                id = "read",
                name = "Read",
                categoryId = null,
                monthlyGoal = 20,
                displayOrder = 0,
                active = true,
                createdOn = LocalDate(2026, 8, 1),
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            )
            repository.saveDailyHabit(existing)
            val beforeRestore = repository.snapshot.value
            val invalidBackup = HabitSnapshot(
                dailyCompletions = listOf(
                    DailyHabitCompletion("missing", LocalDate(2026, 8, 5), true, 2),
                ),
            )

            assertFailsWith<BackupValidationException> {
                repository.restoreFromSnapshot(invalidBackup)
            }
            assertEquals(beforeRestore, repository.snapshot.value)
            repository.close()
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }
}
