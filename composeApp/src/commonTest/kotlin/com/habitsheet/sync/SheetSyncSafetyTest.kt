package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.presentation.DateProvider
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The sync may only ever write to a valid `Plan` tab it created or recognised; nothing else in the spreadsheet. */
class SheetSyncSafetyTest {
    private val day = LocalDate(2026, 10, 1)
    private val dates = object : DateProvider {
        override fun today() = day
        override fun nowEpochMillis() = 0L
    }
    private val otherTabs = listOf("Food", "Workout", "Marathon Plan")

    private fun habit(id: String, name: String) = DailyHabit(id, name, null, 12, 0, true, day, null, 1, 1, datedOnly = true)

    private suspend fun repo(): InMemoryHabitRepository = InMemoryHabitRepository(HabitSnapshot(
        dailyHabits = listOf(habit("run", "Run"), habit("journal", "Journal")),
        dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "run-1"), DayPlan("journal", day, "Page", false, 1, "j-1")),
    )).also { it.setSheetUrl("https://docs.google.com/spreadsheets/d/test-sheet/edit") }

    private fun sync(repo: InMemoryHabitRepository, server: FakeSheetsServer) = SheetSync(
        repo,
        object : SheetTokenProvider {
            override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
        },
        server.client(), dates, RetryPolicy(sleep = { }),
    )

    private fun assertOnlyPlanTabTouched(server: FakeSheetsServer) {
        assertEquals(emptyList(), server.violations, "writes outside the Plan tab")
        assertEquals(otherTabs, server.otherTabs)
        assertTrue(server.calls.none { call -> otherTabs.any { it in call.path || it in call.body } }, "other tab addressed: ${server.calls}")
    }

    // ---- a Plan tab that does not match the schema is never modified -----------------------------------

    @Test
    fun foreignPlanTabIsNeverWrittenAndLocalOnlyHabitsAreNotAppendedToIt() = runTest {
        val server = FakeSheetsServer().withPlanTab(listOf("Week", "Notes", "Budget"), listOf("1", "pay rent", "800"))
        server.otherTabs += otherTabs
        val before = server.rowsAsText()
        val sync = sync(repo(), server)

        sync.sync()
        sync.sync(interactive = true)

        assertIs<SyncError.MalformedPlanTab>(sync.state.value.error)
        assertTrue(server.writes.isEmpty(), "no writes at all: ${server.writes}")
        assertEquals(before, server.rowsAsText())
        assertOnlyPlanTabTouched(server)
    }

    @Test
    fun headerMissingASchemaColumnIsTreatedAsForeign() = runTest {
        for (header in listOf(
            listOf("ID", "Date", "Habit", "Session", "Done"),          // no Skip
            listOf("Date", "ID", "Habit", "Session", "Done", "Skip"),  // wrong order
            listOf("id", "date", "habit", "session", "done", "skip"),  // exact names are required
        )) {
            val server = FakeSheetsServer().withPlanTab(header, listOf("run-1", "2026-10-01", "Run", "Easy", false, false))
            val before = server.rowsAsText()
            val sync = sync(repo(), server)
            sync.sync()
            assertIs<SyncError.MalformedPlanTab>(sync.state.value.error, "header $header")
            assertTrue(server.writes.isEmpty(), "header $header")
            assertEquals(before, server.rowsAsText())
        }
    }

    @Test
    fun malformedRowsLeaveTheTabUntouchedEvenWithPendingChecks() = runTest {
        val server = FakeSheetsServer().withPlanRows(
            listOf("run-1", "2026-10-01", "Run", "Easy", false, false),
            listOf("run-2", "oops", "Run", "Easy", false, false),
        )
        val repo = repo()
        repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 5, "run-1"))
        val before = server.rowsAsText()
        sync(repo, server).sync()
        assertTrue(server.writes.isEmpty())
        assertEquals(before, server.rowsAsText())
        assertEquals(1, repo.snapshot.value.pendingCompletions.size, "the check stays pending")
    }

    // ---- other tabs are never touched -------------------------------------------------------------------

    @Test
    fun newPlanTabFlowOnlyWritesToPlan() = runTest {
        val server = FakeSheetsServer().also { it.otherTabs += otherTabs }
        sync(repo(), server).sync()
        assertTrue(server.hasPlanTab)
        assertTrue(server.writes.isNotEmpty())
        assertOnlyPlanTabTouched(server)
    }

    @Test
    fun existingPlanTabFlowWithAppendAndDoneUploadOnlyWritesToPlan() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", false, false))
        server.otherTabs += otherTabs
        val repo = repo() // "Journal" is local-only -> appended
        repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 5, "run-1"))
        sync(repo, server).sync()
        assertTrue(server.calls.any { it.isAppend })
        assertTrue(server.calls.any { it.isDoneWrite })
        assertOnlyPlanTabTouched(server)
    }

    @Test
    fun anotherTabNamedPlanInDifferentCaseIsNotTouchedAndSyncFailsCleanly() = runTest {
        val server = FakeSheetsServer().also { it.otherTabs += listOf("Food", "plan") }
        val repo = repo()
        val sync = sync(repo, server)

        sync.sync()

        assertEquals(emptyList(), server.violations)
        assertEquals(listOf("Food", "plan"), server.otherTabs)
        assertTrue(!server.hasPlanTab)
        assertIs<SyncError.Unknown>(sync.state.value.error)
        assertEquals(0, repo.getSheetLastSync())
    }

    @Test
    fun theFakeActuallyRejectsStrayWrites() = runTest {
        // Guards the guard: if SheetsApi ever wrote elsewhere, the fake would record it.
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", false, false))
        val client = server.client()
        val response = client.post("https://sheets.googleapis.com/v4/spreadsheets/test-sheet/values/Food!A1:B2:append") { setBody("""{"values":[["x"]]}""") }
        assertEquals(400, response.status.value)
        assertEquals(1, server.violations.size)
    }
}
