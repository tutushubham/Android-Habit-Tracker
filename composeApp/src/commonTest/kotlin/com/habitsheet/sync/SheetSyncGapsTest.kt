package com.habitsheet.sync

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.presentation.DateProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Coverage gaps noted after P0-B step 1: concurrency, rolling window bounds, failure after N calls. */
class SheetSyncGapsTest {
    private val day = LocalDate(2026, 10, 15)
    private val dates = object : DateProvider {
        override fun today() = day
        override fun nowEpochMillis() = 0L
    }
    private val link = "https://docs.google.com/spreadsheets/d/test-sheet/edit"

    private fun sync(repo: InMemoryHabitRepository, server: FakeSheetsServer) = SheetSync(
        repo, repo,
        object : SheetTokenProvider {
            override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
        },
        server.client(), dates, RetryPolicy(sleep = { }),
    )

    @Test
    fun simultaneousSyncCallsRunOneAfterTheOtherNeverInterleaved() = runTest {
        val repo = InMemoryHabitRepository(HabitSnapshot(
            dailyHabits = listOf(DailyHabit("run", "Run", null, 12, 0, true, day, null, 1, 1, datedOnly = true)),
            dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "run-1")),
        )).also { it.setSheetUrl(link) }
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-15", "Run", "Easy", false, false))
        val sync = sync(repo, server)

        listOf(async { sync.sync() }, async { sync.sync() }, async { sync.sync() }).awaitAll()

        val shape = server.calls.map { if (it.isMetadata) "meta" else if (it.isRead) "read" else it.toString() }
        assertEquals(List(3) { listOf("meta", "read") }.flatten(), shape)
    }

    @Test
    fun rollingWindowStartsThirtyOneDaysBeforeTheMonthAndEndsOneEightyDaysAhead() {
        val window = SheetSyncWindow.rolling(day)
        assertEquals(LocalDate(2026, 8, 31), window.start) // Oct 1 minus 31 days
        assertEquals(LocalDate(2027, 4, 13), window.end)  // Oct 15 plus 180 days

        val snapshot = HabitSnapshot(dailyHabits = listOf(DailyHabit("water", "Water", null, 30, 0, true, LocalDate(2026, 1, 1), null, 1, 1)))
        val rows = planRowsFor(snapshot, window)
        assertEquals(window.start, rows.first().date)
        assertEquals(window.end, rows.last().date)
        assertEquals((window.end.toEpochDays() - window.start.toEpochDays() + 1).toInt(), rows.size)
    }

    @Test
    fun datedSessionsOutsideTheWindowAreNotUploaded() = runTest {
        val outside = LocalDate(2028, 1, 1)
        val repo = InMemoryHabitRepository(HabitSnapshot(
            dailyHabits = listOf(DailyHabit("run", "Run", null, 12, 0, true, day, null, 1, 1, datedOnly = true)),
            dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "in"), DayPlan("run", outside, "Far", false, 1, "out")),
        )).also { it.setSheetUrl(link) }
        val server = FakeSheetsServer()
        sync(repo, server).sync()
        assertEquals(listOf("in"), server.rowsAsText().drop(1).map { it[0] })
    }

    @Test
    fun aFailureOnTheThirdCallOfAnExistingTabSyncLeavesLocalStateAndLastSyncUntouched() = runTest {
        // calls: 1 metadata, 2 read, 3 append of the local-only habit -> 400 (not retried)
        val repo = InMemoryHabitRepository(HabitSnapshot(
            dailyHabits = listOf(
                DailyHabit("run", "Run", null, 12, 0, true, day, null, 1, 1, datedOnly = true),
                DailyHabit("journal", "Journal", null, 0, 1, true, day, null, 1, 1, datedOnly = true),
            ),
            dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "run-1"), DayPlan("journal", day, "Page", false, 1, "j-1")),
        )).also { it.setSheetUrl(link); it.setSheetLastSync(100) }
        val before = repo.snapshot.value
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-15", "Run", "Changed", false, false))
        server.failCallNumber(3, FaultAction.Status(400))
        val sync = sync(repo, server)

        sync.sync()

        assertEquals(3, server.calls.size)
        assertTrue(server.calls[2].isAppend)
        assertIs<SyncError.Unknown>(sync.state.value.error)
        assertEquals(before, repo.snapshot.value)
        assertEquals(100, repo.getSheetLastSync())
        assertEquals(2, server.planValues.size, "nothing appended")
    }
}
