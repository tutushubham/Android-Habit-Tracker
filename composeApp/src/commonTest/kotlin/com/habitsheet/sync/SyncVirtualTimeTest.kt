package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retry, backoff and the scheduler's offline retry on the test scheduler's virtual clock, with the production
 * defaults (`delay`, not an injected sleep): the whole schedule runs in milliseconds and asserts exact virtual times.
 */
class SyncVirtualTimeTest {
    private val day = LocalDate(2026, 10, 1)
    private val dates = FakeDateProvider(today = day, nowMillis = 0L)
    private val link = "https://docs.google.com/spreadsheets/d/test-sheet/edit"
    private val okRow = listOf("run-1", "2026-10-01", "Run", "Easy", false, false)
    private val token = object : SheetTokenProvider {
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
    }

    private suspend fun repository() = InMemoryHabitRepository(
        buildSnapshot(stamp = 1) {
            dailyHabit("run", "Run", goal = 12, createdOn = day, datedOnly = true)
            dayPlan("run", day, "Easy", id = "run-1")
        },
    ).also { it.setSheetUrl(link) }

    /** random = 1.0 makes the jittered backoff its documented maximum: 500, 1000, 2000 ms. */
    private val policy get() = RetryPolicy(random = { 1.0 })

    private fun sync(repo: InMemoryHabitRepository, server: FakeSheetsServer, retry: RetryPolicy = policy) =
        SheetSync(repo, repo, token, server.client(), dates, retry)

    private fun FakeSheetsServer.failing(action: FaultAction, times: Int, matches: (RecordedCall) -> Boolean = { it.isMetadata }) = apply {
        fail(action, times = times, matches = matches)
    }

    @Test
    fun backoffWaitsDoubleOnTheVirtualClockAndTheSyncThenSucceeds() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow).failing(FaultAction.Status(503), times = 3)
        val sync = sync(repository(), server)
        val start = testScheduler.currentTime

        sync.sync()

        assertNull(sync.state.value.error)
        assertEquals(500L + 1_000 + 2_000, testScheduler.currentTime - start)
        assertEquals(4, server.calls.count { it.isMetadata }, "three failures and the call that worked")
    }

    @Test
    fun theBackoffIsBoundedAndTheErrorIsReportedAfterTheLastAttempt() = runTest {
        val server = FakeSheetsServer().withPlanRows(okRow).failing(FaultAction.Status(503), times = 100)
        val sync = sync(repository(), server)
        val start = testScheduler.currentTime

        sync.sync()

        assertEquals(503, assertIs<SyncError.Unknown>(sync.state.value.error).status)
        assertEquals(4, server.calls.size, "maxAttempts requests, no more")
        assertEquals(500L + 1_000 + 2_000, testScheduler.currentTime - start, "no wait after the last attempt")
    }

    @Test
    fun retryAfterReplacesTheBackoffAndALongOneIsNotWaitedFor() = runTest {
        val short = FakeSheetsServer().withPlanRows(okRow)
            .failing(FaultAction.Status(429, headers = mapOf("Retry-After" to "2")), times = 1)
        val start = testScheduler.currentTime
        sync(repository(), short).sync()
        assertEquals(2_000L, testScheduler.currentTime - start, "waits exactly what Google asked for")

        val long = FakeSheetsServer().withPlanRows(okRow)
            .failing(FaultAction.Status(429, headers = mapOf("Retry-After" to "31")), times = 1)
        val beforeLong = testScheduler.currentTime
        val sync = sync(repository(), long)
        sync.sync()
        assertEquals(31L, assertIs<SyncError.RateLimited>(sync.state.value.error).retryAfterSeconds)
        assertEquals(0L, testScheduler.currentTime - beforeLong, "longer than the limit: fail now, do not sit and wait")
        assertEquals(1, long.calls.size)
    }

    @Test
    fun timeoutsAreRetriedOnReadsButNeverOnAnAppend() = runTest {
        val read = FakeSheetsServer().withPlanRows(okRow).failing(FaultAction.Timeout, times = 2)
        val start = testScheduler.currentTime
        val readSync = sync(repository(), read)
        readSync.sync()
        assertNull(readSync.state.value.error)
        assertEquals(500L + 1_000, testScheduler.currentTime - start)

        // A habit only this device has: the sync appends it. A lost reply must not be answered with a second append.
        val repo = InMemoryHabitRepository(
            buildSnapshot(stamp = 1) {
                dailyHabit("run", "Run", goal = 12, createdOn = day, datedOnly = true)
                dailyHabit("journal", "Journal", goal = 0, createdOn = day, datedOnly = true)
                dayPlan("run", day, "Easy", id = "run-1")
                dayPlan("journal", day, "Page", id = "j-1")
            },
        ).also { it.setSheetUrl(link) }
        val append = FakeSheetsServer().withPlanRows(okRow).failing(FaultAction.LostResponse, times = 1) { it.isAppend }
        val beforeAppend = testScheduler.currentTime
        val appendSync = sync(repo, append)
        appendSync.sync()

        assertIs<SyncError.Offline>(appendSync.state.value.error)
        assertEquals(1, append.calls.count { it.isAppend })
        assertEquals(0L, testScheduler.currentTime - beforeAppend)
        appendSync.sync() // later: the row is there, so it is not appended again
        assertEquals(1, append.calls.count { it.isAppend })
        assertEquals(listOf("run-1", "j-1"), append.rowsAsText().drop(1).map { it[0] })
    }

    @Test
    fun offlineWithAWaitingCheckOffRetriesByItselfWithDoublingDelaysThenSettles() = runTest {
        val repo = repository()
        repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 10, "run-1"))
        val server = FakeSheetsServer().withPlanRows(okRow).failing(FaultAction.ConnectionFailure, times = 3) { true }
        val sync = sync(repo, server, RetryPolicy.NONE)
        val attempts = mutableListOf<Long>()
        // The mock engine answers on a real thread, so wait for the outcome instead of racing the virtual clock.
        val settled = CompletableDeferred<Unit>()
        val scheduler = SyncScheduler(
            backgroundScope,
            shouldRetry = { sync.state.value.error is SyncError.Offline && repo.snapshot.value.pendingCompletions.isNotEmpty() },
        ) { interactive ->
            attempts += testScheduler.currentTime
            sync.sync(interactive)
            if (sync.state.value.error == null) settled.complete(Unit)
        }

        scheduler.markDirty()
        settled.await()

        // 2.5 s debounce, then 30 s, 60 s and 120 s after each failure; the fourth attempt reaches the sheet.
        val debounce = SyncScheduler.DEFAULT_DEBOUNCE_MILLIS
        assertEquals(listOf(debounce, debounce + 30_000, debounce + 90_000, debounce + 210_000), attempts)
        assertNull(sync.state.value.error)
        assertTrue(repo.snapshot.value.pendingCompletions.isEmpty(), "uploaded and acknowledged")
        assertEquals("true", server.rowsAsText().drop(1).single()[4])
    }

    @Test
    fun aPlatformThatNeverAnswersTheTokenRequestFailsTheBackgroundSyncAfterTheTimeoutAndTheNextSyncWorks() = runTest {
        var asked = 0
        val silentThenAnswering = object : SheetTokenProvider {
            override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) {
                asked++
                if (asked > 1) completion("t", null) // the first request is never answered
            }
        }
        val tokens = SerializingTokenProvider(silentThenAnswering, scope = backgroundScope)
        val repo = repository()
        val server = FakeSheetsServer().withPlanRows(okRow)
        val sync = SheetSync(repo, repo, tokens, server.client(), dates, policy)
        val start = testScheduler.currentTime

        sync.sync(interactive = false)

        val error = assertIs<SyncError.AuthExpired>(sync.state.value.error)
        assertEquals("Google authorization timed out.", error.detail)
        assertEquals(30_000L, testScheduler.currentTime - start, "background requests give up after 30 s")
        assertTrue(server.calls.isEmpty(), "no request is made without a token")

        sync.sync(interactive = false)
        assertNull(sync.state.value.error)
        assertEquals(2, asked)
    }
}
