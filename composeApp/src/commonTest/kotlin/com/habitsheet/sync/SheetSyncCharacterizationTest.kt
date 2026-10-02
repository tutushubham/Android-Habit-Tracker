package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.presentation.DateProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins what [SheetSync] does TODAY against a [FakeSheetsServer]. These are characterization tests: where
 * current behaviour is questionable (marked "CURRENT:") the test documents it so later P0-B steps change it
 * deliberately instead of by accident.
 */
class SheetSyncCharacterizationTest {
    private val day = LocalDate(2026, 10, 1)
    private val tomorrow = LocalDate(2026, 10, 2)
    private val url = "https://docs.google.com/spreadsheets/d/test-sheet/edit"
    private val dates = object : DateProvider {
        override fun today() = day
        override fun nowEpochMillis() = 0L
    }

    private class Token(val value: String?) : SheetTokenProvider {
        var requests = 0
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
            requests++
            completion(value, null)
        }
    }

    private fun habit(id: String, name: String) =
        DailyHabit(id, name, null, 12, 0, true, day, null, 1, 1, datedOnly = true)

    private fun plan(habitId: String, date: LocalDate, detail: String, id: String, skipped: Boolean = false) =
        DayPlan(habitId, date, detail, skipped, 1, id)

    private suspend fun repo(snapshot: HabitSnapshot, lastSync: Long = 0, syncedKeys: Set<String> = emptySet()) =
        InMemoryHabitRepository(snapshot).also {
            it.setSheetUrl(url)
            it.setSheetLastSync(lastSync)
            it.setSheetSyncedKeys(syncedKeys)
        }

    private fun sync(repo: InMemoryHabitRepository, server: FakeSheetsServer, token: SheetTokenProvider = Token("t")) =
        SheetSync(repo, token, server.client(), dates)

    private val runSnapshot get() = HabitSnapshot(
        dailyHabits = listOf(habit("run", "Run")),
        dayPlans = listOf(plan("run", day, "Easy 6 km", "run-1")),
    )

    // ---- new tab ---------------------------------------------------------------------------------

    @Test
    fun missingPlanTabIsCreatedFormattedAndFilledWithoutTouchingOtherTabs() = runTest {
        val server = FakeSheetsServer().also { it.otherTabs += listOf("Food", "Workout") }
        val repo = repo(runSnapshot)
        val sync = sync(repo, server)

        sync.sync(interactive = true)

        assertEquals(listOf("GET", "POST :batchUpdate", "PUT /values/Plan!A1:F2", "POST :batchUpdate"), server.calls.map { it.toString().trim() })
        assertTrue(server.calls[1].isAddSheet)
        assertTrue(server.calls[3].isFormat, "formatting happens after the data is uploaded")
        assertEquals(
            listOf(FakeSheetsServer.SIX_COLUMNS, listOf("run-1", "2026-10-01", "Run", "Easy 6 km", "false", "false")),
            server.rowsAsText(),
        )
        assertEquals(listOf("Food", "Workout"), server.otherTabs)
        assertTrue(server.calls.none { "Food" in it.path || "Workout" in it.path }, "other tabs must never be addressed")
        assertEquals("Plan tab created · 1 sessions uploaded", sync.state.value.message)
        assertEquals(setOf("run-1"), repo.getSheetSyncedKeys())
        assertEquals(setOf("run"), repo.snapshot.value.sheetManagedHabitIds)
        assertTrue(repo.getSheetLastSync() > 0)
    }

    @Test
    fun formattingFailureAfterTabCreationDoesNotFailTheSync() = runTest {
        val server = FakeSheetsServer()
        server.fail(FaultAction.Status(500)) { it.isFormat }
        val repo = repo(runSnapshot)
        val sync = sync(repo, server)

        sync.sync()

        assertEquals("Plan tab created · 1 sessions uploaded", sync.state.value.message)
        assertEquals(2, server.planValues.size)
    }

    @Test
    fun uploadsBooleanDoneAndSkipFromLocalCompletions() = runTest {
        val repo = repo(HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run")),
            dayPlans = listOf(plan("run", day, "Easy", "run-1"), plan("run", tomorrow, "Rest", "run-2", skipped = true)),
            dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 5, "run-1")),
        ))
        val server = FakeSheetsServer()
        sync(repo, server).sync()

        assertEquals(JsonPrimitive(true), server.cell(1, 4))
        assertEquals(JsonPrimitive(false), server.cell(1, 5))
        assertEquals(JsonPrimitive(false), server.cell(2, 4))
        assertEquals(JsonPrimitive(true), server.cell(2, 5))
    }

    // ---- existing tab: sheet wins --------------------------------------------------------------

    @Test
    fun existingTabIsAuthoritativeForSessionsNewHabitsAndDone() = runTest {
        val server = FakeSheetsServer().withPlanRows(
            listOf("run-1", "2026-10-01", "Run", "Tempo 8 km", true, false),
            listOf("yoga-1", "2026-10-02", "Yoga", "Evening flow", false, true),
        )
        val repo = repo(runSnapshot)
        val sync = sync(repo, server)

        sync.sync()

        val snapshot = repo.snapshot.value
        assertEquals("Tempo 8 km", snapshot.dayPlans.single { it.id == "run-1" }.detail)
        val yoga = snapshot.dailyHabits.single { it.name == "Yoga" }
        assertTrue(yoga.datedOnly, "habits created from the sheet are dated-only")
        val yogaPlan = snapshot.dayPlans.single { it.id == "yoga-1" }
        assertEquals(yoga.id, yogaPlan.habitId)
        assertEquals(tomorrow, yogaPlan.date)
        assertTrue(yogaPlan.skipped)
        assertTrue(snapshot.dailyCompletions.single { it.planId == "run-1" }.completed)
        assertEquals(setOf("run", yoga.id), snapshot.sheetManagedHabitIds)
        assertEquals(setOf("run-1", "yoga-1"), repo.getSheetSyncedKeys())
        assertEquals("Synced 2 sessions", sync.state.value.message)
        assertTrue(server.writes.isEmpty(), "first sync with no lastSync never writes to an existing tab")
    }

    @Test
    fun sheetDoneFalseClearsLocalDoneWhenLocalIsNotNewerThanLastSync() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 50, "run-1"))),
            lastSync = 100,
        )
        sync(repo, server).sync()

        assertFalse(repo.snapshot.value.dailyCompletions.single().completed)
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun lastSyncOfZeroMeansSheetWinsEvenOverNewerLocalCheck() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 9_999, "run-1"))),
            lastSync = 0,
        )
        sync(repo, server).sync()

        assertFalse(repo.snapshot.value.dailyCompletions.single().completed)
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun parsesSerialDatesAndEightColumnLayout() = runTest {
        val serial = day.toEpochDays() - LocalDate(1899, 12, 30).toEpochDays()
        val server = FakeSheetsServer().withPlanTab(
            FakeSheetsServer.EIGHT_COLUMNS,
            listOf("run-1", serial, "Fitness", "Run", "Easy 6 km", false, false, "app"),
        )
        val repo = repo(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 150, "run-1"))),
            lastSync = 100,
        )
        sync(repo, server).sync()

        // Eight-column sheets keep Done in column F, so the upload targets Plan!F2.
        assertEquals(JsonPrimitive(true), server.cell(1, 5))
        assertEquals(1, server.writes.size)
        assertTrue(server.writes.single().body.contains("Plan!F2"))
    }

    // ---- uploading pending Done ----------------------------------------------------------------

    @Test
    fun localCheckNewerThanLastSyncIsUploadedAndNotOverwritten() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 150, "run-1"))),
            lastSync = 100,
        )
        val sync = sync(repo, server)

        sync.sync()

        assertTrue(server.cell(1, 4)!!.jsonPrimitive.boolean)
        assertTrue(repo.snapshot.value.dailyCompletions.single().completed)
        assertEquals("Synced 1 sessions · 1 checks uploaded", sync.state.value.message)
        assertTrue(repo.getSheetLastSync() > 100)
    }

    @Test
    fun localUncheckNewerThanLastSyncIsUploadedAsFalse() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", true, false))
        val repo = repo(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, false, 150, "run-1"))),
            lastSync = 100,
        )
        sync(repo, server).sync()

        assertFalse(server.cell(1, 4)!!.jsonPrimitive.boolean)
        assertFalse(repo.snapshot.value.dailyCompletions.single().completed)
    }

    @Test
    fun allPendingChecksGoInOneValuesBatchUpdate() = runTest {
        val server = FakeSheetsServer().withPlanRows(
            listOf("run-1", "2026-10-01", "Run", "A", false, false),
            listOf("run-2", "2026-10-02", "Run", "B", false, false),
        )
        val repo = repo(
            HabitSnapshot(
                dailyHabits = listOf(habit("run", "Run")),
                dayPlans = listOf(plan("run", day, "A", "run-1"), plan("run", tomorrow, "B", "run-2")),
                dailyCompletions = listOf(
                    DailyHabitCompletion("run", day, true, 150, "run-1"),
                    DailyHabitCompletion("run", tomorrow, true, 160, "run-2"),
                ),
            ),
            lastSync = 100,
        )
        sync(repo, server).sync()

        assertEquals(1, server.writes.size)
        assertTrue(server.cell(1, 4)!!.jsonPrimitive.boolean && server.cell(2, 4)!!.jsonPrimitive.boolean)
    }

    // ---- row deletion ----------------------------------------------------------------------------

    @Test
    fun rowRemovedFromSheetDeletesLocalPlanThatWasPreviouslySynced() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(
            HabitSnapshot(
                dailyHabits = listOf(habit("run", "Run")),
                dayPlans = listOf(plan("run", day, "Easy 6 km", "run-1"), plan("run", tomorrow, "Long", "run-2")),
                dailyCompletions = listOf(DailyHabitCompletion("run", tomorrow, true, 5, "run-2")),
            ),
            syncedKeys = setOf("run-1", "run-2"),
        )
        sync(repo, server).sync()

        assertEquals(listOf("run-1"), repo.snapshot.value.dayPlans.map { it.id })
        assertEquals(setOf("run-1"), repo.getSheetSyncedKeys())
        // CURRENT: the orphaned completion for the deleted plan is left behind locally.
        assertEquals(1, repo.snapshot.value.dailyCompletions.count { it.planId == "run-2" })
    }

    @Test
    fun localPlanNeverSyncedIsNotDeletedJustBecauseSheetLacksIt() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(
            HabitSnapshot(
                dailyHabits = listOf(habit("run", "Run")),
                dayPlans = listOf(plan("run", day, "Easy 6 km", "run-1"), plan("run", tomorrow, "Long", "run-2")),
            ),
            syncedKeys = setOf("run-1"),
        )
        sync(repo, server).sync()

        assertEquals(setOf("run-1", "run-2"), repo.snapshot.value.dayPlans.map { it.id }.toSet())
    }

    // ---- local-only habits -----------------------------------------------------------------------

    @Test
    fun localOnlyHabitRowsAreAppendedAndThenBecomeSheetManaged() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run"), habit("journal", "Journal")),
            dayPlans = listOf(plan("run", day, "Easy 6 km", "run-1"), plan("journal", tomorrow, "Evening page", "journal-1")),
        ))
        val sync = sync(repo, server)

        sync.sync()

        assertEquals(1, server.calls.count { it.isAppend })
        assertEquals(listOf("journal-1", "2026-10-02", "Journal", "Evening page", "false", "false"), server.rowsAsText().last())
        assertEquals(3, server.planValues.size)
        assertEquals(setOf("run-1", "journal-1"), repo.getSheetSyncedKeys())
        assertEquals(setOf("run", "journal"), repo.snapshot.value.sheetManagedHabitIds)
        assertEquals("Synced 2 sessions", sync.state.value.message)
    }

    @Test
    fun alreadyManagedHabitMissingFromSheetIsNotReappended() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy 6 km", false, false))
        val repo = repo(HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run"), habit("journal", "Journal")),
            dayPlans = listOf(plan("run", day, "Easy 6 km", "run-1"), plan("journal", tomorrow, "Evening page", "journal-1")),
        ))
        repo.setSheetManagedHabitIds(setOf("run", "journal"))
        sync(repo, server).sync()

        assertTrue(server.calls.none { it.isAppend })
        assertEquals(2, server.planValues.size)
    }

    // ---- errors and partial failure ---------------------------------------------------------------

    @Test
    fun httpStatusesMapToTodaysMessagesAndLeaveLocalStateUntouched() = runTest {
        val cases = listOf(
            401 to "Google session expired. Sign in again.",
            403 to "Sheet access denied. Check Sheets API, OAuth test user, and edit access.",
            404 to "Spreadsheet not found. Check the link and Google account.",
            429 to "Google Sheets error 429.",
            503 to "Google Sheets error 503.",
        )
        for ((status, message) in cases) {
            val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", true, false))
            server.fail(FaultAction.Status(status))
            val repo = repo(runSnapshot, lastSync = 100)
            val sync = sync(repo, server)

            sync.sync()

            assertEquals(message, sync.state.value.message, "status $status")
            assertFalse(sync.state.value.busy)
            assertEquals(1, server.calls.size, "CURRENT: no retry on $status")
            assertEquals(100, repo.getSheetLastSync())
            assertEquals(runSnapshot.dayPlans, repo.snapshot.value.dayPlans)
        }
    }

    @Test
    fun timeoutSurfacesAsRawExceptionTextAndNothingIsAdvanced() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", false, false))
        server.fail(FaultAction.Timeout)
        val repo = repo(runSnapshot, lastSync = 100)
        val sync = sync(repo, server)

        sync.sync()

        // CURRENT: the user sees whatever the exception says (no typed Offline/Timeout error).
        assertTrue(sync.state.value.message.isNotBlank())
        assertFalse(sync.state.value.message.startsWith("Synced"))
        assertFalse(sync.state.value.busy)
        assertEquals(100, repo.getSheetLastSync())
    }

    @Test
    fun failedDoneUploadChangesNothingLocally() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Changed in sheet", false, false))
        server.fail(FaultAction.Status(500)) { it.isDoneWrite }
        val repo = repo(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 150, "run-1"))),
            lastSync = 100,
            syncedKeys = setOf("run-1"),
        )
        val sync = sync(repo, server)

        sync.sync()

        assertEquals("Google Sheets error 500.", sync.state.value.message)
        assertEquals(100, repo.getSheetLastSync())
        // Upload happens before the single local apply, so the sheet's session edit is not applied yet.
        assertEquals("Easy 6 km", repo.snapshot.value.dayPlans.single().detail)
        assertTrue(repo.snapshot.value.dailyCompletions.single().completed)
        assertFalse(server.cell(1, 4)!!.jsonPrimitive.boolean)
    }

    @Test
    fun failureAfterTabCreationLeavesEmptyTabAndNextSyncRecovers() = runTest {
        val server = FakeSheetsServer()
        server.fail(FaultAction.Status(500)) { it.isPut }
        val repo = repo(runSnapshot)
        val sync = sync(repo, server)

        sync.sync()
        assertTrue(server.hasPlanTab)
        assertEquals(0, server.planValues.size)
        assertEquals(0, repo.getSheetLastSync())

        sync.sync()
        assertEquals(2, server.planValues.size)
        assertEquals("Plan tab created · 1 sessions uploaded", sync.state.value.message)
        assertEquals(1, server.calls.count { it.isAddSheet }, "second sync reuses the empty tab")
    }

    @Test
    fun duplicateLocalHabitNamesDoNotAbortTheSync() = runTest {
        val server = FakeSheetsServer().withPlanRows(
            listOf("run-1", "2026-10-01", "Run", "Edited", false, false),
            listOf("run-9", "2026-10-02", "Run", "Unknown id", false, false),
        )
        val repo = repo(HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run"), habit("run2", "run ")),
            dayPlans = listOf(plan("run", day, "Easy", "run-1")),
        ))
        val sync = sync(repo, server)

        sync.sync()

        assertEquals("Synced 1 sessions · 1 row skipped: \"Run\" matches more than one habit, rename one", sync.state.value.message)
        assertEquals("Edited", repo.snapshot.value.dayPlans.single().detail)
        assertTrue(repo.getSheetLastSync() > 0)
    }

    @Test
    fun renamingHabitInSheetRenamesLocalHabitInsteadOfDuplicating() = runTest {
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Jog", "Easy 6 km", false, false))
        val repo = repo(runSnapshot)
        val sync = sync(repo, server)

        sync.sync()

        assertEquals(listOf("Jog"), repo.snapshot.value.dailyHabits.map { it.name })
        assertEquals("run", repo.snapshot.value.dailyHabits.single().id)
        assertEquals("Synced 1 sessions · 1 renamed from sheet", sync.state.value.message)
    }

    // ---- foreign / malformed Plan tab ---------------------------------------------------------------

    @Test
    fun planTabWithForeignHeaderIsReportedAndNeverWritten() = runTest {
        val server = FakeSheetsServer().withPlanTab(listOf("Week", "Notes"), listOf("1", "hello"))
        val before = server.rowsAsText()
        val repo = repo(runSnapshot, lastSync = 100)
        val sync = sync(repo, server)

        sync.sync()

        assertTrue(sync.state.value.message.startsWith("Plan tab needs ID, Date, Habit, Session, Done, Skip"))
        assertTrue(server.writes.isEmpty())
        assertEquals(before, server.rowsAsText())
        assertEquals(100, repo.getSheetLastSync())
    }

    @Test
    fun malformedRowAbortsBeforeAnyLocalOrRemoteWrite() = runTest {
        val server = FakeSheetsServer().withPlanRows(
            listOf("run-1", "2026-10-01", "Run", "Fine", false, false),
            listOf("run-2", "next tuesday", "Run", "Bad date", false, false),
        )
        val repo = repo(runSnapshot, lastSync = 100)
        val sync = sync(repo, server)

        sync.sync()

        assertEquals("Plan row 3: use YYYY-MM-DD in Date.", sync.state.value.message)
        assertTrue(server.writes.isEmpty())
        assertEquals("Easy 6 km", repo.snapshot.value.dayPlans.single().detail)
    }

    @Test
    fun headerOnlyPlanTabCountsAsExistingSoLocalOnlyHabitsAreAppended() = runTest {
        // CURRENT: a header-only tab is "existing" (zero remote rows), so local habits are all local-only and appended.
        val server = FakeSheetsServer().withPlanTab(FakeSheetsServer.SIX_COLUMNS)
        val repo = repo(runSnapshot)
        val sync = sync(repo, server)

        sync.sync()

        assertEquals("Synced 1 sessions", sync.state.value.message)
        assertEquals(1, server.calls.count { it.isAppend })
        assertEquals(2, server.planValues.size)
        assertEquals(1, repo.snapshot.value.dayPlans.size)
    }

    @Test
    fun emptiedPlanTabKeepsPreviouslySyncedLocalPlansAndWarns() = runTest {
        val server = FakeSheetsServer().withPlanTab(FakeSheetsServer.SIX_COLUMNS)
        val repo = repo(runSnapshot, syncedKeys = setOf("run-1"))
        repo.setSheetManagedHabitIds(setOf("run"))
        val sync = sync(repo, server)

        sync.sync()

        assertEquals(1, repo.snapshot.value.dayPlans.size)
        assertEquals(setOf("run-1"), repo.getSheetSyncedKeys())
        assertEquals("Synced 0 sessions · Plan tab has no sessions; kept local sessions", sync.state.value.message)
    }

    // ---- preconditions ---------------------------------------------------------------------------

    @Test
    fun noLinkMakesNoRequests() = runTest {
        val server = FakeSheetsServer()
        val repo = InMemoryHabitRepository(runSnapshot)
        val token = Token("t")
        val sync = SheetSync(repo, token, server.client(), dates)

        sync.sync(interactive = false)
        assertEquals("Not synced yet", sync.state.value.message)
        sync.sync(interactive = true)
        assertEquals("Add a spreadsheet link first.", sync.state.value.message)
        assertTrue(server.calls.isEmpty())
        assertEquals(0, token.requests)
    }

    @Test
    fun missingTokenMakesNoRequests() = runTest {
        val server = FakeSheetsServer()
        val repo = repo(runSnapshot)
        val sync = sync(repo, server, Token(null))

        sync.sync(interactive = false)
        assertEquals("Sign in to sync", sync.state.value.message)
        sync.sync(interactive = true)
        assertTrue(sync.state.value.message.startsWith("Google sign-in was cancelled or failed."))
        assertTrue(server.calls.isEmpty())
        assertNull(server.cell(0, 0))
        assertNotNull(sync.state.value)
    }
}
