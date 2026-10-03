package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupFixtures
import com.habitsheet.domain.backup.BackupSerializer
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deleting a habit or category must remove or detach everything that belonged to it, even when the SQLite driver
 * does not enforce foreign keys. (The JDBC driver used in these tests does not keep `PRAGMA foreign_keys = ON`;
 * whether the Android and iOS drivers do is unverified, so deletes no longer depend on it. An orphaned
 * completion would also make the next backup fail validation on restore.)
 */
class DeleteCascadeSqliteTest {
    private suspend fun withSeededRepository(block: suspend (LocalHabitRepository) -> Unit) {
        val file = Files.createTempFile("habit-sheet-cascade", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val repo = LocalHabitRepository({ JdbcSqliteDriver(url) })
        try {
            repo.restoreFromSnapshot(BackupSerializer.deserialize(BackupFixtures.V3))
            block(repo)
        } finally {
            repo.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun deletingADailyHabitRemovesItsCheckOffsAndSessionsAndLeavesABackupThatRestores() = runTest {
        withSeededRepository { repo ->
            assertTrue(repo.snapshot.value.dailyCompletions.isNotEmpty() && repo.snapshot.value.dayPlans.isNotEmpty() && repo.snapshot.value.weeklyPlans.isNotEmpty())

            repo.deleteDailyHabit("run")

            val snapshot = repo.snapshot.value
            assertTrue(snapshot.dailyHabits.isEmpty())
            assertTrue(snapshot.dailyCompletions.isEmpty(), "orphaned completions: ${snapshot.dailyCompletions}")
            assertTrue(snapshot.dayPlans.isEmpty() && snapshot.weeklyPlans.isEmpty())
            assertEquals(1, snapshot.weeklyHabits.size)
            // The next export of this data must be restorable.
            BackupSerializer.deserialize(BackupSerializer.serialize(snapshot, 1))
        }
    }

    @Test
    fun deletingAWeeklyHabitRemovesItsCheckOffs() = runTest {
        withSeededRepository { repo ->
            assertEquals(1, repo.snapshot.value.weeklyCompletions.size)
            repo.deleteWeeklyHabit("gym")
            assertTrue(repo.snapshot.value.weeklyHabits.isEmpty())
            assertTrue(repo.snapshot.value.weeklyCompletions.isEmpty())
            BackupSerializer.deserialize(BackupSerializer.serialize(repo.snapshot.value, 1))
        }
    }

    @Test
    fun deletingACategoryKeepsItsHabitsWithoutACategory() = runTest {
        withSeededRepository { repo ->
            assertEquals("cat1", repo.snapshot.value.dailyHabits.single().categoryId)
            repo.deleteCategory("cat1")
            assertTrue(repo.snapshot.value.categories.isEmpty())
            assertEquals(null, repo.snapshot.value.dailyHabits.single().categoryId)
            BackupSerializer.deserialize(BackupSerializer.serialize(repo.snapshot.value, 1))
        }
    }
}
