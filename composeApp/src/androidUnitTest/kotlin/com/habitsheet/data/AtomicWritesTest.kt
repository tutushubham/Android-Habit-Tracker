package com.habitsheet.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.model.CompletionAck
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A [SqlDriver] that throws on the n-th statement executed INSIDE a transaction while [armed]. Statements outside a
 * transaction (the snapshot reload after a commit, plain reads and settings) are counted nowhere and never fail.
 */
private class FaultInjectingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
    var armed = false
    var failAt: Int? = null
    var statementsInTransaction = 0
        private set

    private fun tick() {
        if (!armed || delegate.currentTransaction() == null) return
        statementsInTransaction++
        if (statementsInTransaction == failAt) throw SQLException("injected failure at statement $statementsInTransaction")
    }

    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
        tick()
        return delegate.execute(identifier, sql, parameters, binders)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        tick()
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }
}

/**
 * Every write that touches several rows must be all-or-nothing. For each operation this test first counts the
 * statements it runs inside its transaction, then re-runs it once per statement with a failure injected at exactly
 * that statement, and requires that nothing changed: not the cached snapshot, not the settings, and not the file
 * (checked by opening it again).
 */
class AtomicWritesTest {
    private val day = LocalDate(2026, 10, 5)
    private val link = "https://docs.google.com/spreadsheets/d/mine/edit"

    private data class State(
        val snapshot: HabitSnapshot,
        val theme: Int,
        val onboarding: Boolean,
        val url: String,
        val lastSync: Long,
        val syncedKeys: Set<String>,
    )

    private suspend fun state(repo: LocalHabitRepository) = State(
        snapshot = repo.snapshot.value,
        theme = repo.getThemeMode(),
        onboarding = repo.isOnboardingCompleted(),
        url = repo.getSheetUrl(),
        lastSync = repo.getSheetLastSync(),
        syncedKeys = repo.getSheetSyncedKeys(),
    )

    /** Two habits with plans and check-offs, a linked and synced sheet, and one check-off waiting for upload. */
    private suspend fun seed(repo: LocalHabitRepository) {
        repo.restoreFromSnapshot(
            buildSnapshot(stamp = 100) {
                category("c1", "Fitness")
                category("c2", "Study")
                dailyHabit("run", "Run", category = "c1", createdOn = LocalDate(2026, 9, 1), datedOnly = true)
                dailyHabit("read", "Read", category = "c2", createdOn = LocalDate(2026, 9, 1))
                weeklyHabit("gym", category = "c1", createdOn = LocalDate(2026, 9, 1))
                weeklyPlan("read", 1, "20 pages")
                dayPlan("run", day, "Easy", id = "run-1")
                dayPlan("run", day, "Mobility", id = "run-2")
                done("run", day, planId = "run-1")
                done("read", day)
                weeklyDone("gym", day)
            },
            BackupSettings(themeMode = 1, onboardingCompleted = true, sheetUrl = null),
        )
        repo.setSheetUrl(link)
        repo.setSheetLastSync(50)
        repo.setSheetSyncedKeys(setOf("run-1", "run-2"))
        repo.setSheetManagedHabitIds(setOf("run"))
        repo.setDailyCompletion(DailyHabitCompletion("run", day, false, 200, "run-2"))
    }

    private suspend fun <T> withDatabase(failAt: Int?, block: suspend (repo: LocalHabitRepository, driver: FaultInjectingDriver, reopen: () -> LocalHabitRepository) -> T): T {
        val file = Files.createTempFile("habit-sheet-atomic", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val drivers = mutableListOf<FaultInjectingDriver>()
        val repositories = mutableListOf<LocalHabitRepository>()
        val repo = LocalHabitRepository(
            {
                FaultInjectingDriver(JdbcSqliteDriver(url)).also {
                    it.failAt = failAt
                    drivers += it
                }
            },
        ).also { repositories += it }
        try {
            seed(repo)
            return block(repo, drivers.single()) { LocalHabitRepository({ JdbcSqliteDriver(url) }).also { repositories += it } }
        } finally {
            repositories.forEach { runCatching { it.close() } }
            Files.deleteIfExists(file)
        }
    }

    private suspend fun assertAtomic(name: String, operation: suspend LocalHabitRepository.() -> Unit) {
        // Dry run: it succeeds, changes something, and tells us how many statements the transaction runs.
        val total = withDatabase(failAt = null) { repo, driver, _ ->
            val before = state(repo)
            driver.armed = true
            repo.operation()
            driver.armed = false
            assertNotEquals(before, state(repo), "$name: the operation must change something for this test to mean anything")
            driver.statementsInTransaction
        }
        assertTrue(total > 0, "$name ran no statement inside a transaction")

        for (statement in 1..total) {
            withDatabase(failAt = statement) { repo, driver, reopen ->
                val before = state(repo)
                driver.armed = true
                val failure = runCatching { repo.operation() }.exceptionOrNull()
                driver.armed = false
                assertNotNull(failure, "$name: a failure at statement $statement of $total must surface")
                assertEquals(before, state(repo), "$name: cached state changed after a failure at statement $statement of $total")
                repo.refresh()
                assertEquals(before, state(repo), "$name: database changed after a failure at statement $statement of $total")
                repo.close()
                assertEquals(before, state(reopen()), "$name: file changed after a failure at statement $statement of $total")
            }
        }
    }

    @Test
    fun applySheetSyncIsAllOrNothingAtEveryStatement() = runTest {
        assertAtomic("applySheetSync") {
            applySheetSync(
                SheetSyncChanges(
                    planIdsToDelete = listOf("run-2"),
                    habitsToSave = listOf(
                        DailyHabit("run", "Jog", "c1", 10, 0, true, LocalDate(2026, 9, 1), null, 100, 300, datedOnly = true),
                        DailyHabit("yoga", "Yoga", null, 0, 2, true, day, null, 300, 300, datedOnly = true),
                    ),
                    plansToSave = listOf(
                        DayPlan("yoga", day, "Flow", false, 300, "yoga-1"),
                        DayPlan("run", day, "Tempo", false, 300, "run-3"),
                    ),
                    completionsToSave = listOf(
                        DailyHabitCompletion("yoga", day, true, 300, "yoga-1"),
                        DailyHabitCompletion("run", day, true, 300, "run-1"),
                    ),
                    completionsToAcknowledge = listOf(CompletionAck("run-2", day, 200)),
                    managedHabitIds = setOf("run", "yoga"),
                ),
                newKeys = setOf("run-1", "run-3", "yoga-1"),
                lastSync = 999,
            )
        }
    }

    @Test
    fun restoringABackupIsAllOrNothingAtEveryStatement() = runTest {
        assertAtomic("restoreFromSnapshot") {
            restoreFromSnapshot(
                buildSnapshot(stamp = 500) {
                    category("k1", "Other")
                    dailyHabit("swim", "Swim", category = "k1", createdOn = LocalDate(2026, 9, 1))
                    weeklyHabit("yoga", createdOn = LocalDate(2026, 9, 1))
                    weeklyPlan("swim", 3, "Laps")
                    dayPlan("swim", day, "Drills", id = "swim-1")
                    done("swim", day, planId = "swim-1")
                    weeklyDone("yoga", day)
                },
                BackupSettings(themeMode = 2, onboardingCompleted = false, sheetUrl = "https://docs.google.com/spreadsheets/d/other/edit"),
                restoreSheetLink = true,
            )
        }
    }

    @Test
    fun resettingAllDataIsAllOrNothingAtEveryStatement() = runTest {
        assertAtomic("clearAllData") { clearAllData() }
    }

    @Test
    fun deletesAreAllOrNothingAtEveryStatement() = runTest {
        assertAtomic("deleteDailyHabit") { deleteDailyHabit("run") }
        assertAtomic("deleteWeeklyHabit") { deleteWeeklyHabit("gym") }
        assertAtomic("deleteCategory") { deleteCategory("c1") }
    }

    @Test
    fun relinkingADifferentSheetIsAllOrNothingAtEveryStatement() = runTest {
        assertAtomic("setSheetUrl") { setSheetUrl("https://docs.google.com/spreadsheets/d/another/edit") }
    }

    @Test
    fun reorderingAndSavingAHabitAreAllOrNothingAtEveryStatement() = runTest {
        assertAtomic("updateDailyHabitOrders") { updateDailyHabitOrders(mapOf("run" to 1, "read" to 0), updatedAtEpochMillis = 900) }
        assertAtomic("saveDailyHabit") {
            saveDailyHabit(DailyHabit("read", "Read more", "c1", 20, 5, true, LocalDate(2026, 9, 1), null, 100, 900))
        }
    }

    @Test
    fun aFailedCheckOffLeavesTheCachedSnapshotAndTheFileAlone() = runTest {
        assertAtomic("setDailyCompletion") { setDailyCompletion(DailyHabitCompletion("read", day, false, 900)) }
    }
}
