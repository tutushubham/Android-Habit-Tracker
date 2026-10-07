package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.presentation.DateProvider
import com.habitsheet.presentation.toStatus
import com.habitsheet.testing.English
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One fake-server test per [SyncError], plus retry/backoff rules. */
class SheetSyncErrorsTest {
    private val day = LocalDate(2026, 10, 1)
    private val dates = object : DateProvider {
        override fun today() = day
        override fun nowEpochMillis() = 0L
    }
    private val sleeps = mutableListOf<Long>()

    /** random = 1.0 makes backoff deterministic: 500, 1000, 2000 ... */
    private val retry get() = RetryPolicy(random = { 1.0 }, sleep = { sleeps += it })
    private val okRow = listOf("run-1", "2026-10-01", "Run", "Easy", false, false)

    private suspend fun repo() = InMemoryHabitRepository(
        HabitSnapshot(
            dailyHabits = listOf(DailyHabit("run", "Run", null, 12, 0, true, day, null, 1, 1, datedOnly = true)),
            dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "run-1")),
        ),
    ).also {
        it.setSheetUrl("https://docs.google.com/spreadsheets/d/test-sheet/edit")
        it.setSheetLastSync(100)
    }

    private fun sync(repo: InMemoryHabitRepository, server: FakeSheetsServer, token: String? = "t") = SheetSync(
        repo,
        repo,
        object : SheetTokenProvider {
            override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion(token, null)
        },
        server.client(),
        dates,
        retry,
    )

    private suspend fun failWith(action: FaultAction, times: Int = 100, setup: FakeSheetsServer.() -> Unit = {}, matches: (RecordedCall) -> Boolean = { true }): Pair<SheetSyncState, FakeSheetsServer> {
        val server = FakeSheetsServer().withPlanRows(okRow).apply(setup)
        server.fail(action, times = times, matches = matches)
        val sync = sync(repo(), server)
        sync.sync()
        return sync.state.value to server
    }

    // ---- one test per SyncError ---------------------------------------------------------------------

    @Test
    fun offlineTimeoutsAreRetriedThenReportedAsOfflineNotAsAnError() = runTest {
        val (state, server) = failWith(FaultAction.Timeout)
        assertIs<SyncError.Offline>(state.error)
        assertEquals("Will sync when online.", English.render(state.message))
        assertEquals(4, server.calls.size)
        assertEquals(listOf(500L, 1_000L, 2_000L), sleeps)
        val status = state.toStatus()
        assertFalse(status.isError)
        assertEquals("Will sync when online.", English.render(status.text))
    }

    @Test
    fun noConnectionIsOfflineToo() = runTest {
        val (state, _) = failWith(FaultAction.ConnectionFailure)
        assertIs<SyncError.Offline>(state.error)
        assertFalse(state.toStatus().isError)
    }

    @Test
    fun authExpiredOn401IsNotRetried() = runTest {
        val (state, server) = failWith(FaultAction.Status(401))
        assertIs<SyncError.AuthExpired>(state.error)
        assertEquals("Google sign-in needed. Tap Connect & sync to sign in again.", English.render(state.message))
        assertEquals(1, server.calls.size)
        assertTrue(sleeps.isEmpty())
        assertTrue(state.toStatus().isError)
    }

    @Test
    fun accessDeniedOn403IsNotRetried() = runTest {
        val (state, server) = failWith(FaultAction.Status(403, body = """{"error":{"status":"PERMISSION_DENIED"}}"""))
        assertIs<SyncError.AccessDenied>(state.error)
        assertEquals("No access to this sheet. Use a Google account that can edit it.", English.render(state.message))
        assertEquals(1, server.calls.size)
    }

    @Test
    fun notFoundOn404IsNotRetried() = runTest {
        val (state, server) = failWith(FaultAction.Status(404))
        assertIs<SyncError.NotFound>(state.error)
        assertEquals("Spreadsheet not found. Check the link and the Google account.", English.render(state.message))
        assertEquals(1, server.calls.size)
    }

    @Test
    fun rateLimitedAfterRetriesCarriesRetryAfter() = runTest {
        val (state, server) = failWith(FaultAction.Status(429, mapOf("Retry-After" to "3")))
        val error = assertIs<SyncError.RateLimited>(state.error)
        assertEquals(3L, error.retryAfterSeconds)
        assertEquals("Google is limiting requests. Sync will retry shortly.", English.render(state.message))
        assertEquals(4, server.calls.size)
        assertEquals(listOf(3_000L, 3_000L, 3_000L), sleeps, "Retry-After replaces the computed backoff")
    }

    @Test
    fun quotaStyle403IsTreatedAsRateLimit() = runTest {
        val (state, server) = failWith(FaultAction.Status(403, body = """{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}"""))
        assertIs<SyncError.RateLimited>(state.error)
        assertEquals(4, server.calls.size)
    }

    @Test
    fun malformedBadDateReportsRowAndReason() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow, listOf("run-2", "next tuesday", "Run", "x", false, false))
        val sync = sync(repo(), server)
        sync.sync()
        val error = assertIs<SyncError.MalformedPlanTab>(sync.state.value.error)
        assertEquals(3, error.row)
        assertEquals("use YYYY-MM-DD in Date", error.reason)
        assertEquals("Plan tab, row 3: use YYYY-MM-DD in Date. Fix the sheet, then sync again.", English.render(sync.state.value.message))
        assertEquals(1, server.calls.count { it.isRead }, "malformed data is not retried")
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun malformedHeaderHasNoRow() = runTest {
        val server = FakeSheetsServer().withPlanTab(listOf("Week", "Notes"))
        val sync = sync(repo(), server)
        sync.sync()
        val error = assertIs<SyncError.MalformedPlanTab>(sync.state.value.error)
        assertNull(error.row)
        assertTrue(English.render(sync.state.value.message).endsWith("Fix the header row, then sync again."))
    }

    @Test
    fun malformedDuplicateIdAndMissingFieldsAndBadFlag() = runTest {
        val cases = mapOf(
            listOf(okRow, okRow) to "duplicate ID run-1",
            listOf(listOf("x", "2026-10-01", "", "s", false, false)) to "ID, Habit and Session are required",
            listOf(listOf("x", "2026-10-01", "Run", "s", "maybe", false)) to "Done must be a checkbox or TRUE/FALSE",
        )
        for ((rows, reason) in cases) {
            val server = FakeSheetsServer().withPlanRows(*rows.toTypedArray())
            val sync = sync(repo(), server)
            sync.sync()
            assertEquals(reason, assertIs<SyncError.MalformedPlanTab>(sync.state.value.error).reason)
        }
    }

    @Test
    fun unknownForPersistentServerErrorsAndUnexpectedFailures() = runTest {
        val (state, server) = failWith(FaultAction.Status(500))
        val error = assertIs<SyncError.Unknown>(state.error)
        assertEquals(500, error.status)
        assertEquals("Sync failed. Try again in a moment.", English.render(state.message))
        assertEquals(4, server.calls.size)
        assertTrue(state.toStatus().isError)

        assertIs<SyncError.Unknown>(IllegalStateException("boom").toSyncError())
    }

    @Test
    fun cancelledOrFailedSignInIsAuthExpiredWithDetail() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow)
        val repository = repo()
        val sync = SheetSync(
            repository,
            repository,
            object : SheetTokenProvider {
                override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion(null, "Sign-in cancelled")
            },
            server.client(),
            dates,
            retry,
        )
        sync.sync()
        val error = assertIs<SyncError.AuthExpired>(sync.state.value.error)
        assertEquals("Sign-in cancelled", error.detail)
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun platformNetworkErrorIsOfflineButOtherSignInErrorsAreAuthExpired() = runTest {
        for ((text, expectOffline) in listOf(SheetTokenProvider.NETWORK_ERROR to true, "Google access was revoked. Sign in again." to false)) {
            val server = FakeSheetsServer().withPlanRows(okRow)
            val repository = repo()
            val sync = SheetSync(
                repository,
                repository,
                object : SheetTokenProvider {
                    override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion(null, text)
                },
                server.client(),
                dates,
                retry,
            )
            sync.sync()
            if (expectOffline) assertIs<SyncError.Offline>(sync.state.value.error) else assertIs<SyncError.AuthExpired>(sync.state.value.error)
            assertTrue(server.calls.isEmpty())
        }
    }

    @Test
    fun backgroundSyncWithoutAccessIsAnAuthErrorNotASilentNoOp() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow)
        val sync = sync(repo(), server, token = null)
        sync.sync(interactive = false)
        assertIs<SyncError.AuthExpired>(sync.state.value.error)
        assertTrue(sync.state.value.toStatus().isError)
        assertTrue(server.calls.isEmpty())
    }

    // ---- retry rules --------------------------------------------------------------------------------

    @Test
    fun transientFailureIsRetriedWithExponentialBackoffAndTheSyncSucceeds() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow)
        server.fail(FaultAction.Status(503), times = 2) { it.isRead }
        val sync = sync(repo(), server)
        sync.sync()
        assertNull(sync.state.value.error)
        assertEquals("Synced 1 sessions", English.render(sync.state.value.message))
        assertEquals(listOf(500L, 1_000L), sleeps)
        assertEquals(3, server.calls.count { it.isRead })
    }

    @Test
    fun retryAfterLongerThanTheLimitFailsImmediately() = runTest {
        val (state, server) = failWith(FaultAction.Status(429, mapOf("Retry-After" to "120")))
        assertEquals(120L, assertIs<SyncError.RateLimited>(state.error).retryAfterSeconds)
        assertEquals(1, server.calls.size)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun non429ClientErrorsAreNeverRetried() = runTest {
        val (state, server) = failWith(FaultAction.Status(400))
        assertEquals(1, server.calls.size)
        assertEquals(400, assertIs<SyncError.Unknown>(state.error).status)
    }

    @Test
    fun nonIdempotentAppendIsNotRetriedOnServerErrorsOrTimeoutsButIsOn503() = runTest {
        fun serverWithLocalOnlyHabit() = FakeSheetsServer().withPlanRows(listOf("other-1", "2026-10-01", "Other", "x", false, false))
        suspend fun localOnlyRepo() = repo().also { it.setSheetManagedHabitIds(emptySet()) } // "Run" is not in the sheet

        for (action in listOf(FaultAction.Status(500), FaultAction.Timeout)) {
            val server = serverWithLocalOnlyHabit()
            server.fail(action, times = 100) { it.isAppend }
            sync(localOnlyRepo(), server).sync()
            assertEquals(1, server.calls.count { it.isAppend }, "$action must not repeat an append")
        }
        sleeps.clear()
        val server = serverWithLocalOnlyHabit()
        server.fail(FaultAction.Status(503), times = 1) { it.isAppend }
        val sync = sync(localOnlyRepo(), server)
        sync.sync()
        assertEquals(2, server.calls.count { it.isAppend })
        assertEquals(1, server.planValues.count { it[0].toString().contains("run-1") }, "no duplicate rows")
        assertNull(sync.state.value.error)
    }

    @Test
    fun idempotentDoneWriteIsRetriedOnTimeout() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow)
        server.fail(FaultAction.Timeout, times = 1) { it.isDoneWrite }
        val repo = repo()
        repo.setDailyCompletion(com.habitsheet.domain.model.DailyHabitCompletion("run", day, true, 150, "run-1"))
        val sync = sync(repo, server)
        sync.sync()
        assertEquals(2, server.calls.count { it.isDoneWrite })
        assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
        assertNull(sync.state.value.error)
    }

    @Test
    fun errorClearsOnNextSuccessfulSync() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow)
        server.fail(FaultAction.Status(401), times = 1)
        val sync = sync(repo(), server)
        sync.sync()
        assertIs<SyncError.AuthExpired>(sync.state.value.error)
        sync.sync()
        assertNull(sync.state.value.error)
        assertFalse(sync.state.value.toStatus().isError)
    }

    // ---- backoff arithmetic -------------------------------------------------------------------------

    @Test
    fun backoffDoublesCapsAndStaysWithinJitterBounds() {
        val low = RetryPolicy(random = { 0.0 })
        val high = RetryPolicy(random = { 1.0 })
        assertEquals(listOf(250L, 500L, 1_000L, 2_000L, 4_000L, 4_000L), (1..6).map { low.backoffMillis(it) })
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 8_000L), (1..6).map { high.backoffMillis(it) })
        val mid = RetryPolicy(random = { 0.5 })
        assertEquals(375L, mid.backoffMillis(1))
    }
}
