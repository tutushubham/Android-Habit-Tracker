package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalHabitRepositoryPersistenceTest {
    @Test
    fun completionSurvivesRepositoryRestart() = runTest {
        val databaseFile = Files.createTempFile("habit-sheet-persistence", ".db")
        val url = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"
        val bootstrap = JdbcSqliteDriver(url)
        HabitsDatabase.Schema.create(bootstrap)
        bootstrap.close()

        try {
            val first = LocalHabitRepository { JdbcSqliteDriver(url) }
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
            first.close()

            val reopened = LocalHabitRepository { JdbcSqliteDriver(url) }
            assertEquals(listOf(completion), reopened.snapshot.value.dailyCompletions)
            reopened.close()
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }
}
