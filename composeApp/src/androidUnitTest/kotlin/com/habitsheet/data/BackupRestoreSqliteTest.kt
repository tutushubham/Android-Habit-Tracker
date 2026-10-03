package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupFixtures
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.SheetSyncChanges
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Restores every backup version into a real SQLite file that already holds synced data, then reopens it. */
class BackupRestoreSqliteTest {
    private val oldSheet = "https://docs.google.com/spreadsheets/d/current/edit"

    private suspend fun withSyncedDatabase(block: suspend (url: String, repo: LocalHabitRepository) -> Unit) {
        val file = Files.createTempFile("habit-sheet-backup-restore", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it); it.close() }
        try {
            val repo = LocalHabitRepository({ JdbcSqliteDriver(url) })
            repo.restoreFromSnapshot(BackupSerializer.deserialize(BackupFixtures.V3))
            repo.setSheetUrl(oldSheet)
            repo.applySheetSync(SheetSyncChanges(managedHabitIds = setOf("run")), setOf("run-1", "run-2"), 12345)
            repo.setDailyCompletion(DailyHabitCompletion("run", LocalDate(2026, 8, 7), true, 9))
            assertTrue(repo.snapshot.value.pendingCompletions.isNotEmpty())
            assertEquals(setOf("run-1", "run-2"), repo.getSheetSyncedKeys())
            block(url, repo)
            repo.close()
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun everyVersionRestoresAndLeavesCleanSyncStateThatSurvivesRestart() = runTest {
        listOf(BackupFixtures.V1, BackupFixtures.V2, BackupFixtures.V3, BackupFixtures.V4).forEach { fixture ->
            withSyncedDatabase { url, repo ->
                val backup = BackupSerializer.parse(fixture)
                repo.restoreFromSnapshot(backup.snapshot, backup.settings)

                assertEquals(backup.snapshot, repo.snapshot.value)
                assertEquals(emptySet(), repo.getSheetSyncedKeys())
                assertEquals(0L, repo.getSheetLastSync())
                assertTrue(repo.snapshot.value.sheetManagedHabitIds.isEmpty())
                assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
                assertEquals(oldSheet, repo.getSheetUrl())

                repo.close()
                val reopened = LocalHabitRepository({ JdbcSqliteDriver(url) })
                assertEquals(backup.snapshot, reopened.snapshot.value)
                assertEquals(emptySet(), reopened.getSheetSyncedKeys())
                assertTrue(reopened.snapshot.value.pendingCompletions.isEmpty())
                reopened.close()
            }
        }
    }

    @Test
    fun v4SettingsAreStoredAndTheSheetLinkOnlyWhenAskedFor() = runTest {
        val backup = BackupSerializer.parse(BackupFixtures.V4)
        withSyncedDatabase { _, repo ->
            repo.restoreFromSnapshot(backup.snapshot, backup.settings)
            assertEquals(2, repo.getThemeMode())
            assertTrue(repo.isOnboardingCompleted())
            assertEquals(oldSheet, repo.getSheetUrl())

            repo.restoreFromSnapshot(backup.snapshot, backup.settings, restoreSheetLink = true)
            assertEquals("https://docs.google.com/spreadsheets/d/FIXTURESHEETID/edit", repo.getSheetUrl())
            assertEquals(emptySet(), repo.getSheetSyncedKeys())
        }
    }
}
