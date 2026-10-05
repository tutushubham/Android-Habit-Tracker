package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.CompletionAck
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** `applySheetSync` against real SQLite: one transaction, one snapshot reload, nothing partial on failure. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplySheetSyncTest {
    private val day = LocalDate(2026, 10, 1)
    private fun habit(id: String, name: String) = DailyHabit(id, name, null, 0, 0, true, day, null, 1, 1, datedOnly = true)

    private suspend fun withRepository(block: suspend (LocalHabitRepository, () -> LocalHabitRepository) -> Unit) {
        val file = Files.createTempFile("habit-sheet-apply", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val repositories = mutableListOf<LocalHabitRepository>()
        fun open() = LocalHabitRepository({ JdbcSqliteDriver(url) }).also { repositories += it }
        try {
            block(open(), ::open)
        } finally {
            repositories.forEach { runCatching { it.close() } }
            Files.deleteIfExists(file)
        }
    }

    private suspend fun seed(repo: LocalHabitRepository) {
        repo.saveDailyHabit(habit("run", "Run"))
        repo.saveDayPlan(DayPlan("run", day, "Easy", false, 1, "run-1"))
        repo.setDailyCompletion(DailyHabitCompletion("run", day, false, 1, "run-1"))
        repo.setSheetManagedHabitIds(setOf("run"))
        repo.setSheetSyncedKeys(setOf("run-1"))
        repo.setSheetLastSync(100)
    }

    @Test
    fun appliesEverythingInOneStepAndReloadsSnapshotOnce() = runTest {
        withRepository { repo, reopen ->
            seed(repo)
            val emissions = mutableListOf<HabitSnapshot>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.snapshot.collect { emissions += it } }
            val before = emissions.size

            repo.applySheetSync(
                SheetSyncChanges(
                    planIdsToDelete = listOf("run-1"),
                    habitsToSave = listOf(habit("run", "Jog"), habit("yoga", "Yoga")),
                    plansToSave = listOf(DayPlan("yoga", day, "Flow", false, 5, "yoga-1"), DayPlan("run", day, "Tempo", false, 5, "run-2")),
                    completionsToSave = listOf(DailyHabitCompletion("yoga", day, true, 5, "yoga-1")),
                    managedHabitIds = setOf("run", "yoga"),
                ),
                newKeys = setOf("run-2", "yoga-1"),
                lastSync = 777,
            )

            assertEquals(before + 1, emissions.size, "exactly one snapshot reload")
            val snapshot = repo.snapshot.value
            assertEquals(setOf("Jog", "Yoga"), snapshot.dailyHabits.map { it.name }.toSet())
            assertEquals(setOf("yoga-1", "run-2"), snapshot.dayPlans.map { it.id }.toSet())
            assertEquals(setOf("run", "yoga"), snapshot.sheetManagedHabitIds)
            assertEquals(setOf("run-2", "yoga-1"), repo.getSheetSyncedKeys())
            assertEquals(777, repo.getSheetLastSync())
            repo.close()
            val persisted = reopen()
            assertEquals(snapshot.dayPlans.toSet(), persisted.snapshot.value.dayPlans.toSet())
            assertEquals(777, persisted.getSheetLastSync())
        }
    }

    @Test
    fun failureMidApplyRollsEverythingBack() = runTest {
        withRepository { repo, reopen ->
            seed(repo)
            val before = repo.snapshot.value

            // The last plan has a blank session, which the transaction rejects AFTER the earlier writes already ran.
            val error = assertFails {
                repo.applySheetSync(
                    SheetSyncChanges(
                        planIdsToDelete = listOf("run-1"),
                        habitsToSave = listOf(habit("run", "Jog"), habit("yoga", "Yoga")),
                        plansToSave = listOf(
                            DayPlan("yoga", day, "Flow", false, 5, "yoga-1"),
                            DayPlan("run", day, " ", false, 5, "bad-1"),
                        ),
                        completionsToSave = listOf(DailyHabitCompletion("yoga", day, true, 5, "yoga-1")),
                        managedHabitIds = setOf("run", "yoga"),
                    ),
                    newKeys = setOf("yoga-1"),
                    lastSync = 777,
                )
            }
            assertTrue(error.message != null)

            assertEquals(before, repo.snapshot.value, "cached snapshot untouched")
            assertEquals(setOf("run-1"), repo.getSheetSyncedKeys())
            assertEquals(100, repo.getSheetLastSync())
            repo.refresh()
            assertEquals(before, repo.snapshot.value, "database untouched")
            repo.close()
            val persisted = reopen()
            assertEquals(before, persisted.snapshot.value, "nothing partially written to disk")
            assertEquals(setOf("run-1"), persisted.getSheetSyncedKeys())
            assertEquals(100, persisted.getSheetLastSync())
        }
    }

    @Test
    fun userCheckIsPendingUntilAcknowledgedWithTheSameValueAndSurvivesRestart() = runTest {
        withRepository { repo, reopen ->
            seed(repo)
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/x/edit") // new link clears seed-time flags
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 10, "run-1"))
            assertEquals(setOf(CompletionKey("run-1", day)), repo.snapshot.value.pendingCompletions)
            repo.close()

            val second = reopen()
            assertEquals(setOf(CompletionKey("run-1", day)), second.snapshot.value.pendingCompletions)

            // A newer toggle after the sync read its snapshot: the ack for the OLD value must not clear the flag.
            second.setDailyCompletion(DailyHabitCompletion("run", day, false, 20, "run-1"))
            second.applySheetSync(SheetSyncChanges(completionsToAcknowledge = listOf(CompletionAck("run-1", day, 10)), managedHabitIds = setOf("run")), setOf("run-1"), 5)
            assertEquals(1, second.snapshot.value.pendingCompletions.size)

            second.applySheetSync(SheetSyncChanges(completionsToAcknowledge = listOf(CompletionAck("run-1", day, 20)), managedHabitIds = setOf("run")), setOf("run-1"), 6)
            assertTrue(second.snapshot.value.pendingCompletions.isEmpty())
            assertEquals(false, second.snapshot.value.dailyCompletions.single().completed, "ack never changes the value")
        }
    }

    @Test
    fun valueFromSheetNeverOverwritesACheckToggledDuringTheSync() = runTest {
        withRepository { repo, _ ->
            seed(repo)
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/x/edit")
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 30, "run-1")) // user toggles mid-sync

            repo.applySheetSync(
                SheetSyncChanges(
                    completionsToSave = listOf(DailyHabitCompletion("run", day, false, 25, "run-1")), // sheet value read earlier
                    managedHabitIds = setOf("run"),
                ),
                setOf("run-1"),
                9,
            )

            assertTrue(repo.snapshot.value.dailyCompletions.single().completed)
            assertEquals(1, repo.snapshot.value.pendingCompletions.size)
        }
    }

    @Test
    fun valueFromSheetIsStoredAsNotPending() = runTest {
        withRepository { repo, _ ->
            seed(repo)
            repo.applySheetSync(SheetSyncChanges(completionsToAcknowledge = listOf(CompletionAck("run-1", day, 1)), managedHabitIds = setOf("run")), setOf("run-1"), 8)
            repo.applySheetSync(
                SheetSyncChanges(completionsToSave = listOf(DailyHabitCompletion("run", day, true, 25, "run-1")), managedHabitIds = setOf("run")),
                setOf("run-1"),
                9,
            )
            assertTrue(repo.snapshot.value.dailyCompletions.single().completed)
            assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
        }
    }

    @Test
    fun linkingADifferentSheetDropsPendingFlags() = runTest {
        withRepository { repo, _ ->
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/a/edit")
            seed(repo)
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 10, "run-1"))
            assertEquals(1, repo.snapshot.value.pendingCompletions.size)
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/b/edit")
            assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
            assertTrue(repo.snapshot.value.dailyCompletions.single().completed, "the check itself is kept")
        }
    }
}
