package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.sync.FakeSheetsServer
import com.habitsheet.sync.FaultAction
import com.habitsheet.sync.RetryPolicy
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.sync.SyncError
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A whole sync against a real SQLite file and the fake sheet, with an app restart between the steps. */
class SheetSyncSqliteTest {
    private val day = LocalDate(2026, 10, 1)
    private val dates = FakeDateProvider(today = day, nowMillis = 0L)
    private val link = "https://docs.google.com/spreadsheets/d/test-sheet/edit"
    private val token = object : SheetTokenProvider {
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
    }
    private val sheetRow = listOf("run-1", "2026-10-01", "Run", "Easy", false, false)

    private suspend fun withDatabase(block: suspend (open: () -> LocalHabitRepository) -> Unit) {
        val file = Files.createTempFile("habit-sheet-sync", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val opened = mutableListOf<LocalHabitRepository>()
        try {
            block { LocalHabitRepository({ JdbcSqliteDriver(url) }).also { opened += it } }
        } finally {
            opened.forEach { runCatching { it.close() } }
            Files.deleteIfExists(file)
        }
    }

    private fun sync(repo: LocalHabitRepository, server: FakeSheetsServer) =
        SheetSync(repo, repo, token, server.client(), dates, RetryPolicy.NONE)

    private suspend fun LocalHabitRepository.seed() {
        restoreFromSnapshot(
            buildSnapshot(stamp = 1) {
                dailyHabit("run", "Run", goal = 12, createdOn = day, datedOnly = true)
                dayPlan("run", day, "Easy", id = "run-1")
            },
        )
        setSheetUrl(link)
    }

    @Test
    fun aCheckOffMadeOfflineSurvivesARestartAndReachesTheSheetOnTheNextSync() = runTest {
        withDatabase { open ->
            val first = open()
            first.seed()
            first.setDailyCompletion(DailyHabitCompletion("run", day, true, 10, "run-1"))
            val server = FakeSheetsServer().withPlanRows(sheetRow)
            server.fail(FaultAction.ConnectionFailure, times = 1)
            val offline = sync(first, server)

            offline.sync()

            assertIs<SyncError.Offline>(offline.state.value.error)
            assertEquals(setOf(CompletionKey("run-1", day)), first.snapshot.value.pendingCompletions)
            first.close()

            val second = open()
            assertEquals(setOf(CompletionKey("run-1", day)), second.snapshot.value.pendingCompletions, "the waiting check-off survives a restart")
            assertTrue(second.snapshot.value.dailyCompletions.single().completed)
            val online = sync(second, server)

            online.sync()

            assertNull(online.state.value.error)
            assertEquals("true", server.rowsAsText().drop(1).single()[4], "uploaded to the Done column")
            assertTrue(second.snapshot.value.pendingCompletions.isEmpty())
            second.close()

            val third = open()
            assertTrue(third.snapshot.value.pendingCompletions.isEmpty(), "the acknowledgement is on disk too")
            assertTrue(third.snapshot.value.dailyCompletions.single().completed)
            assertEquals(setOf("run-1"), third.getSheetSyncedKeys())
            assertTrue(third.getSheetLastSync() > 0)
        }
    }

    @Test
    fun theSheetWinsForAnythingNotWaitingToUploadAndARowRemovedThereIsRemovedHere() = runTest {
        withDatabase { open ->
            val repo = open()
            repo.restoreFromSnapshot(
                buildSnapshot(stamp = 1) {
                    dailyHabit("run", "Run", goal = 12, createdOn = day, datedOnly = true)
                    dayPlan("run", day, "Easy", id = "run-1")
                    dayPlan("run", day, "Mobility", id = "run-2")
                    done("run", day, planId = "run-1", completed = false)
                },
            )
            repo.setSheetUrl(link)
            repo.setSheetSyncedKeys(setOf("run-1", "run-2")) // both rows were in the sheet at the last sync
            repo.setSheetManagedHabitIds(setOf("run"))
            val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", true, false))

            sync(repo, server).sync()
            repo.close()

            val reopened = open()
            val snapshot = reopened.snapshot.value
            assertEquals(listOf("run-1"), snapshot.dayPlans.map { it.id }, "run-2 was removed from the sheet")
            assertTrue(snapshot.dailyCompletions.single { it.planId == "run-1" }.completed, "done in the sheet, so done here")
            assertTrue(snapshot.pendingCompletions.isEmpty())
            assertTrue(server.violations.isEmpty())
        }
    }
}
