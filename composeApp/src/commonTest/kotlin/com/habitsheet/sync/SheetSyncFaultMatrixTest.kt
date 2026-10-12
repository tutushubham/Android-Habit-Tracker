package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every typed [SyncError] at every request of a sync, in both flows (new Plan tab, existing tab with an append and a
 * pending check-off). After a failure at request k nothing local may have changed and nothing outside the Plan tab
 * may have been written; after the fault clears, the next sync must reach exactly the state a clean sync reaches
 * (so a lost reply to an append can never duplicate rows, and a half-created tab is completed).
 */
class SheetSyncFaultMatrixTest {
    private val day = LocalDate(2026, 10, 1)
    private val dates = FakeDateProvider(today = day, nowMillis = 0L)
    private val link = "https://docs.google.com/spreadsheets/d/test-sheet/edit"
    private val token = object : SheetTokenProvider {
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
    }

    private enum class Flow { NEW_TAB, EXISTING_TAB }

    /** A habit the sheet knows, a habit only this device has, and a check-off still waiting to be uploaded. */
    private fun local() = buildSnapshot(stamp = 1) {
        dailyHabit("run", "Run", goal = 12, createdOn = day, datedOnly = true)
        dailyHabit("journal", "Journal", goal = 0, createdOn = day, datedOnly = true)
        dayPlan("run", day, "Easy", id = "run-1")
        dayPlan("journal", day, "Page", id = "j-1")
    }

    private fun server(flow: Flow) = when (flow) {
        Flow.NEW_TAB -> FakeSheetsServer()
        Flow.EXISTING_TAB -> FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", false, false))
    }

    private suspend fun repository(): InMemoryHabitRepository = InMemoryHabitRepository(local()).also {
        it.setSheetUrl(link) // linking forgets pending flags, so the check-off is made after it
        it.setSheetLastSync(100)
        it.setDailyCompletion(DailyHabitCompletion("run", day, true, 50, "run-1"))
    }

    private fun sync(repo: InMemoryHabitRepository, server: FakeSheetsServer) = SheetSync(repo, repo, token, server.client(), dates, RetryPolicy.NONE)

    /** What matters about local data, without clock-derived stamps. */
    private data class Local(
        val habits: List<Triple<String, String, Boolean>>,
        val plans: List<List<Any>>,
        val completions: List<Triple<String, LocalDate, Boolean>>,
        val pending: Set<Pair<String, LocalDate>>,
        val managed: Set<String>,
    )

    private fun HabitSnapshot.normalized() = Local(
        habits = dailyHabits.map { Triple(it.id, it.name, it.datedOnly) },
        plans = dayPlans.map { listOf(it.id, it.habitId, it.date, it.detail, it.skipped) },
        // A stored "not done" and no row at all mean the same thing: what is checked off.
        completions = dailyCompletions.filter { it.completed }.map { Triple(it.planId, it.date, it.completed) },
        pending = pendingCompletions.map { it.planId to it.date }.toSet(),
        managed = sheetManagedHabitIds,
    )

    private class Outcome(val local: Local, val sheet: List<List<String>>, val calls: Int)

    private suspend fun cleanRun(flow: Flow): Outcome {
        val repo = repository()
        val server = server(flow)
        val sync = sync(repo, server)
        sync.sync()
        assertNull(sync.state.value.error, "$flow: the clean run must succeed")
        assertTrue(server.violations.isEmpty())
        return Outcome(repo.snapshot.value.normalized(), server.rowsAsText(), server.calls.size)
    }

    private class Case(val name: String, val action: FaultAction, val expect: (SyncError) -> Unit)

    private val cases = listOf(
        Case("401", FaultAction.Status(401)) { assertIs<SyncError.AuthExpired>(it) },
        Case("403", FaultAction.Status(403)) { assertIs<SyncError.AccessDenied>(it) },
        Case("403 quota", FaultAction.Status(403, body = """{"error":{"status":"RATE_LIMIT_EXCEEDED"}}""")) { assertIs<SyncError.RateLimited>(it) },
        Case("404", FaultAction.Status(404)) { assertIs<SyncError.NotFound>(it) },
        Case("429", FaultAction.Status(429, headers = mapOf("Retry-After" to "7"))) { assertEquals(7L, assertIs<SyncError.RateLimited>(it).retryAfterSeconds) },
        Case("400", FaultAction.Status(400)) { assertEquals(400, assertIs<SyncError.Unknown>(it).status) },
        Case("500", FaultAction.Status(500)) { assertEquals(500, assertIs<SyncError.Unknown>(it).status) },
        Case("timeout", FaultAction.Timeout) { assertIs<SyncError.Offline>(it) },
        Case("no connection", FaultAction.ConnectionFailure) { assertIs<SyncError.Offline>(it) },
        Case("reply lost after the server applied it", FaultAction.LostResponse) { assertIs<SyncError.Offline>(it) },
    )

    private suspend fun sweep(flow: Flow) {
        val clean = cleanRun(flow)
        assertTrue(clean.calls >= 4, "$flow: expected a multi-request sync, saw ${clean.calls}")

        for (callNumber in 1..clean.calls) {
            for (case in cases) {
                val label = "$flow, request $callNumber of ${clean.calls}, ${case.name}"
                val repo = repository()
                val before = repo.snapshot.value
                val server = server(flow)
                val sync = sync(repo, server)
                server.failCallNumber(callNumber, case.action)

                sync.sync()

                val failedCall = server.calls[callNumber - 1]
                val optionalFormat = flow == Flow.NEW_TAB && failedCall.isFormat
                if (optionalFormat) {
                    // Formatting a freshly created tab is cosmetic: its failure must not fail the sync.
                    assertNull(sync.state.value.error, "$label: a failed format request is ignored")
                } else {
                    case.expect(sync.state.value.error ?: error("$label: expected a typed error"))
                    assertEquals(before, repo.snapshot.value, "$label: local data changed by a failed sync")
                    assertEquals(100, repo.getSheetLastSync(), "$label: last sync moved")
                    assertTrue(repo.getSheetSyncedKeys().isEmpty(), "$label: synced keys moved")
                }
                assertTrue(server.violations.isEmpty(), "$label: wrote outside the Plan tab: ${server.violations}")

                // The next sync, with the server healthy again, must land where a clean sync lands.
                server.clearFaults()
                val retry = sync(repo, server)
                retry.sync()
                assertNull(retry.state.value.error, "$label: recovery sync failed")
                assertEquals(clean.local, repo.snapshot.value.normalized(), "$label: local data after recovery")
                assertEquals(clean.sheet, server.rowsAsText(), "$label: sheet after recovery (duplicate or missing rows?)")
                assertTrue(server.violations.isEmpty(), "$label: recovery wrote outside the Plan tab")
            }
        }
    }

    @Test
    fun everyErrorAtEveryRequestOfAFirstSyncLeavesLocalDataAloneAndRecovers() = runTest { sweep(Flow.NEW_TAB) }

    @Test
    fun everyErrorAtEveryRequestOfASyncWithAnAppendAndAnUploadLeavesLocalDataAloneAndRecovers() = runTest { sweep(Flow.EXISTING_TAB) }

    @Test
    fun theCleanRunsAreWhatTheMatrixAssumes() = runTest {
        val newTab = cleanRun(Flow.NEW_TAB)
        assertEquals(listOf("ID", "Date", "Habit", "Session", "Done", "Skip"), newTab.sheet.first())
        assertEquals(setOf("run-1", "j-1"), newTab.sheet.drop(1).map { it[0] }.toSet())
        assertTrue(newTab.local.pending.isEmpty(), "the first upload carries the pending check-off")

        val existing = cleanRun(Flow.EXISTING_TAB)
        assertEquals(setOf("run-1", "j-1"), existing.sheet.drop(1).map { it[0] }.toSet(), "the local-only habit is appended once")
        assertEquals("true", existing.sheet.drop(1).first { it[0] == "run-1" }[4], "the pending check-off reached the sheet")
        assertTrue(existing.local.pending.isEmpty())
        assertEquals(setOf("run", "journal"), existing.local.managed)
    }
}
