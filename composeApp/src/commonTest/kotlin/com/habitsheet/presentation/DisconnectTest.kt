package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.sync.FakeSheetsServer
import com.habitsheet.sync.RetryPolicy
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectTest {
    private val day = LocalDate(2026, 10, 1)
    private val link = "https://docs.google.com/spreadsheets/d/test-sheet/edit"

    @Test
    fun disconnectUnlinksAndClearsSyncStateButKeepsEveryLocalRecord() = runTest {
        val seeded = HabitSnapshot(
            dailyHabits = listOf(DailyHabit("run", "Run", null, 12, 0, true, day, null, 1, 1, datedOnly = true)),
            dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "run-1")),
            dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 5, "run-1")),
        )
        val repository = InMemoryHabitRepository(seeded)
        repository.setSheetUrl(link)
        repository.setSheetLastSync(123)
        repository.setSheetSyncedKeys(setOf("run-1"))
        repository.setSheetManagedHabitIds(setOf("run"))
        repository.setDailyCompletion(DailyHabitCompletion("run", day, true, 6, "run-1")) // pending
        val server = FakeSheetsServer().withPlanRows(listOf("run-1", "2026-10-01", "Run", "Easy", true, false))
        val sync = SheetSync(
            repository,
            object : SheetTokenProvider {
                override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
            },
            server.client(), retryPolicy = RetryPolicy(sleep = { }),
        )
        sync.sync()
        val viewModel = SettingsViewModel(repository, backgroundScope, UnconfinedTestDispatcher(testScheduler), sync)
        repository.setDailyCompletion(DailyHabitCompletion("run", day, false, 7, "run-1")) // pending again

        viewModel.disconnect()

        assertEquals("", repository.getSheetUrl())
        assertEquals("", viewModel.sheetUrl.value)
        assertEquals(0, repository.getSheetLastSync())
        assertTrue(repository.getSheetSyncedKeys().isEmpty())
        val snapshot = repository.snapshot.value
        assertTrue(snapshot.sheetManagedHabitIds.isEmpty())
        assertTrue(snapshot.pendingCompletions.isEmpty())
        // Local data is untouched.
        assertEquals(seeded.dailyHabits, snapshot.dailyHabits)
        assertEquals(seeded.dayPlans, snapshot.dayPlans)
        assertEquals(1, snapshot.dailyCompletions.size)
        assertEquals(false, snapshot.dailyCompletions.single().completed)
        // The status line forgets the old sync.
        assertEquals("Not synced yet", viewModel.sheetSyncState.value.message)
        assertNull(viewModel.sheetSyncState.value.error)
        assertEquals("Disconnected. Your habits and check-offs stay on this device.", viewModel.sheetMessage.value)
    }

    @Test
    fun disconnectedAppDoesNotTouchTheNetworkOnSync() = runTest {
        val repository = InMemoryHabitRepository()
        val server = FakeSheetsServer()
        val sync = SheetSync(
            repository,
            object : SheetTokenProvider {
                override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion("t", null)
            },
            server.client(), retryPolicy = RetryPolicy(sleep = { }),
        )
        repository.setSheetUrl(link)
        val viewModel = SettingsViewModel(repository, backgroundScope, UnconfinedTestDispatcher(testScheduler), sync)
        viewModel.disconnect()
        sync.sync()
        assertTrue(server.calls.isEmpty())
    }
}
