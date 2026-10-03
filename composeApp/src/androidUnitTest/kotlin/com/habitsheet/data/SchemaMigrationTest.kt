package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opens a database that has the exact schema of each released version (the committed snapshots in
 * `sqldelight/databases/N.db`), fills it with rows the way that version's app stored them, migrates it to the
 * current schema and checks that every row, setting and relationship survived.
 *
 * Schema N stands for what `N.sqm` migrates away from: 1 = the first release (before any .sqm), 2 = + plans,
 * 3 = + text settings, 4 = + habit kind / ids on plans and completions, 5 = current.
 */
class SchemaMigrationTest {
    private val latest = HabitsDatabase.Schema.version.toInt()
    private val aug5 = LocalDate(2026, 8, 5)
    private val aug6 = LocalDate(2026, 8, 6)

    private val snapshotDir = listOf(
        File("src/commonMain/sqldelight/databases"),
        File("composeApp/src/commonMain/sqldelight/databases"),
    ).first { it.isDirectory }

    private fun copyOfSnapshot(version: Int): String {
        val file = Files.createTempFile("habit-sheet-schema-v$version", ".db")
        Files.copy(File(snapshotDir, "$version.db").toPath(), file, StandardCopyOption.REPLACE_EXISTING)
        file.toFile().deleteOnExit()
        return "jdbc:sqlite:${file.toAbsolutePath()}"
    }

    /** Rows as the app of [version] wrote them. The same habits, check-offs and settings in every version. */
    private fun fill(driver: JdbcSqliteDriver, version: Int) {
        fun sql(statement: String) { driver.execute(null, statement, 0) }
        sql("INSERT INTO categoryEntity VALUES ('c1', 'Fitness', 0, 1, 100)")
        val habitColumns = if (version >= 4) "(id, name, category_id, monthly_goal, display_order, active, created_on, archived_on, created_at, updated_at, kind, dated_only)" else "(id, name, category_id, monthly_goal, display_order, active, created_on, archived_on, created_at, updated_at)"
        val habitValues = if (version >= 4) "('run', 'Run', 'c1', 12, 0, 1, '2026-08-01', NULL, 100, 200, 'ACTION', 0)" else "('run', 'Run', 'c1', 12, 0, 1, '2026-08-01', NULL, 100, 200)"
        sql("INSERT INTO dailyHabitEntity $habitColumns VALUES $habitValues")
        if (version >= 4) {
            sql("INSERT INTO dailyCompletionEntity (plan_id, habit_id, date, completed, updated_at) VALUES ('run|2026-08-05', 'run', '2026-08-05', 1, 300)")
            sql("INSERT INTO dailyCompletionEntity (plan_id, habit_id, date, completed, updated_at) VALUES ('run|2026-08-06', 'run', '2026-08-06', 0, 310)")
        } else {
            sql("INSERT INTO dailyCompletionEntity (habit_id, date, completed, updated_at) VALUES ('run', '2026-08-05', 1, 300)")
            sql("INSERT INTO dailyCompletionEntity (habit_id, date, completed, updated_at) VALUES ('run', '2026-08-06', 0, 310)")
        }
        sql("INSERT INTO weeklyHabitEntity VALUES ('gym', 'Gym', NULL, 0, 1, '2026-08-01', NULL, 100, 200)")
        sql("INSERT INTO weeklyCompletionEntity VALUES ('gym', '2026-08-03', 1, 300)")
        sql("INSERT INTO settingsEntity VALUES ('onboarding_completed', 1)")
        sql("INSERT INTO settingsEntity VALUES ('theme_mode', 2)")
        sql("INSERT INTO settingsEntity VALUES ('defaults_seeded', 1)")
        if (version >= 2) {
            sql("INSERT INTO weeklyPlanEntity VALUES ('run', 2, 'Intervals', 250)")
            if (version >= 4) sql("INSERT INTO dayPlanEntity (id, habit_id, date, detail, skipped, updated_at) VALUES ('run|2026-08-06', 'run', '2026-08-06', 'Easy 6 km', 0, 260)")
            else sql("INSERT INTO dayPlanEntity (habit_id, date, detail, skipped, updated_at) VALUES ('run', '2026-08-06', 'Easy 6 km', 0, 260)")
        }
        if (version >= 3) {
            sql("INSERT INTO textSettingsEntity VALUES ('sheet_url', 'https://docs.google.com/spreadsheets/d/abc123/edit')")
            sql("INSERT INTO textSettingsEntity VALUES ('sheet_last_sync', '305')")
            sql("INSERT INTO textSettingsEntity VALUES ('sheet_synced_keys', 'run|2026-08-06')")
            sql("INSERT INTO textSettingsEntity VALUES ('sheet_managed_habits', 'run')")
        }
    }

    private suspend fun migrated(fromVersion: Int, check: suspend (LocalHabitRepository) -> Unit) {
        val url = copyOfSnapshot(fromVersion)
        val driver = JdbcSqliteDriver(url)
        fill(driver, fromVersion)
        HabitsDatabase.Schema.migrate(driver, fromVersion.toLong(), latest.toLong())
        driver.close()
        val repo = LocalHabitRepository({ JdbcSqliteDriver(url) })
        try {
            check(repo)
        } finally {
            repo.close()
        }
    }

    @Test
    fun theSnapshotsAreTheReleasedSchemasAndCoverEveryVersion() {
        assertEquals((1..latest).toList(), (1..latest).filter { File(snapshotDir, "$it.db").isFile })
        assertEquals(5, latest, "A new .sqm needs a new snapshot (generateCommonMainHabitsDatabaseSchema) and a new case below")
    }

    @Test
    fun everyReleasedVersionMigratesToTheCurrentSchemaWithAllItsData() = runTest {
        for (version in 1..latest - 1) {
            migrated(version) { repo ->
                val snapshot = repo.snapshot.value
                val label = "from v$version"

                assertEquals(listOf(Category("c1", "Fitness", 0, true, 100)), snapshot.categories, label)
                assertEquals(
                    listOf(DailyHabit("run", "Run", "c1", 12, 0, true, LocalDate(2026, 8, 1), null, 100, 200, HabitKind.ACTION, datedOnly = false)),
                    snapshot.dailyHabits, label,
                )
                assertEquals(
                    listOf(
                        DailyHabitCompletion("run", aug5, true, 300, "run|2026-08-05"),
                        DailyHabitCompletion("run", aug6, false, 310, "run|2026-08-06"),
                    ),
                    snapshot.dailyCompletions, label,
                )
                assertEquals(listOf(WeeklyHabit("gym", "Gym", null, 0, true, LocalDate(2026, 8, 1), null, 100, 200)), snapshot.weeklyHabits, label)
                assertEquals(listOf(WeeklyHabitCompletion("gym", LocalDate(2026, 8, 3), true, 300)), snapshot.weeklyCompletions, label)

                if (version >= 2) {
                    assertEquals(listOf(WeeklyPlan("run", 2, "Intervals", 250)), snapshot.weeklyPlans, label)
                    assertEquals(listOf(DayPlan("run", aug6, "Easy 6 km", false, 260, "run|2026-08-06")), snapshot.dayPlans, label)
                } else {
                    assertTrue(snapshot.weeklyPlans.isEmpty() && snapshot.dayPlans.isEmpty(), label)
                }

                assertTrue(repo.isOnboardingCompleted(), label)
                assertEquals(2, repo.getThemeMode(), label)
                if (version >= 3) {
                    assertEquals("https://docs.google.com/spreadsheets/d/abc123/edit", repo.getSheetUrl(), label)
                    assertEquals(305L, repo.getSheetLastSync(), label)
                    assertEquals(setOf("run|2026-08-06"), repo.getSheetSyncedKeys(), label)
                    assertEquals(setOf("run"), snapshot.sheetManagedHabitIds, label)
                    // Changed after the last sync (310 > 305) and therefore not yet uploaded: the 4 -> 5 backfill.
                    assertEquals(setOf(CompletionKey("run|2026-08-06", aug6)), snapshot.pendingCompletions, label)
                } else {
                    assertEquals("", repo.getSheetUrl(), label)
                    assertTrue(snapshot.pendingCompletions.isEmpty(), label)
                }
            }
        }
    }

    @Test
    fun migratedDatabasesStayFullyUsableAndKeepTheirForeignKeys() = runTest {
        for (version in 1..latest) {
            migrated(version) { repo ->
                // Cascades survive the table rebuilds in 3.sqm: deleting the habit removes its rows, nothing else.
                repo.deleteDailyHabit("run")
                val snapshot = repo.snapshot.value
                assertTrue(
                    snapshot.dailyHabits.isEmpty() && snapshot.dailyCompletions.isEmpty() && snapshot.dayPlans.isEmpty() && snapshot.weeklyPlans.isEmpty(),
                    "from v$version: ${snapshot.dailyHabits} ${snapshot.dailyCompletions} ${snapshot.dayPlans} ${snapshot.weeklyPlans}",
                )
                assertEquals(1, snapshot.weeklyHabits.size, "from v$version")
                assertEquals(1, snapshot.categories.size, "from v$version")
            }
        }
    }

    @Test
    fun aV1DatabaseCanBeWrittenToAfterMigrating() = runTest {
        migrated(1) { repo ->
            repo.setDailyCompletion(DailyHabitCompletion("run", LocalDate(2026, 8, 7), true, 400))
            repo.saveDayPlan(DayPlan("run", LocalDate(2026, 8, 7), "Long run", false, 400, "p-new"))
            val snapshot = repo.snapshot.value
            assertEquals(3, snapshot.dailyCompletions.size)
            assertEquals(1, snapshot.dayPlans.size)
            assertTrue(CompletionKey("run|2026-08-07", LocalDate(2026, 8, 7)) in snapshot.pendingCompletions)
        }
    }
}
