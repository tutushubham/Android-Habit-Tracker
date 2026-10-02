package com.habitsheet.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSchedulerTest {
    @Test
    fun burstOfTapsProducesOneSyncAfterTheQuietPeriod() = runTest {
        val runs = mutableListOf<Boolean>()
        val scheduler = SyncScheduler(backgroundScope, debounceMillis = 2_500) { runs += it }

        repeat(20) {
            scheduler.markDirty()
            advanceTimeBy(300) // 20 taps over 6 s, never 2.5 s apart
        }
        assertEquals(0, runs.size, "no sync while the user is still tapping")

        advanceTimeBy(2_199); runCurrent() // 300 ms already elapsed since the last tap
        assertEquals(0, runs.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(false), runs)

        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, runs.size, "nothing else was dirtied")
    }

    @Test
    fun syncNowIsImmediateAbsorbsPendingDebounceAndKeepsInteractiveFlag() = runTest {
        val runs = mutableListOf<Boolean>()
        val scheduler = SyncScheduler(backgroundScope, debounceMillis = 2_500) { runs += it }

        scheduler.markDirty()
        advanceTimeBy(1_000)
        scheduler.syncNow(interactive = true)
        runCurrent()
        assertEquals(listOf(true), runs)

        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(true), runs, "the earlier dirty mark was absorbed by the immediate run")
    }

    @Test
    fun triggersDuringARunCollapseIntoExactlyOneFollowUp() = runTest {
        val runs = mutableListOf<Long>()
        var started = 0
        val scheduler = SyncScheduler(backgroundScope, debounceMillis = 1_000) {
            started++
            runs += testScheduler.currentTime
            delay(5_000) // a slow sync
        }

        scheduler.syncNow()
        runCurrent()
        assertEquals(1, started)

        // 10 taps while the first sync is still running.
        repeat(10) { scheduler.markDirty(); advanceTimeBy(100) }
        assertEquals(1, started, "never two syncs at once")

        advanceTimeBy(10_000); runCurrent()
        assertEquals(2, started, "exactly one follow-up")
        // The follow-up starts only after the first run ended (t=5000) plus its own debounce.
        assertEquals(listOf(0L, 6_000L), runs)
    }

    @Test
    fun syncNowDuringARunRunsRightAfterItWithoutDebounce() = runTest {
        val runs = mutableListOf<Long>()
        val scheduler = SyncScheduler(backgroundScope, debounceMillis = 2_500) {
            runs += testScheduler.currentTime
            delay(1_000)
        }
        scheduler.syncNow()
        runCurrent()
        advanceTimeBy(100)
        scheduler.syncNow()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(0L, 1_000L), runs)
    }

    @Test
    fun offlineRunsRetryByThemselvesWithDoublingDelayUntilShouldRetryTurnsFalse() = runTest {
        val runs = mutableListOf<Long>()
        var offline = true
        val scheduler = SyncScheduler(
            backgroundScope, debounceMillis = 100, retryDelayMillis = 1_000, maxRetryDelayMillis = 3_000,
            shouldRetry = { offline },
        ) {
            runs += testScheduler.currentTime
            if (runs.size == 4) offline = false // connection is back on the 4th try
        }
        scheduler.syncNow()
        advanceTimeBy(60_000); runCurrent()
        // first run at 0, then +1000, +2000, +3000 (capped), then it stops retrying.
        assertEquals(listOf(0L, 1_000L, 3_000L, 6_000L), runs)
    }

    @Test
    fun aTriggerWhileWaitingToRetryIsServedImmediatelyAndRetriesContinue() = runTest {
        val runs = mutableListOf<Long>()
        var offline = true
        val scheduler = SyncScheduler(backgroundScope, debounceMillis = 100, retryDelayMillis = 5_000, shouldRetry = { offline }) {
            runs += testScheduler.currentTime
            if (runs.size == 2) offline = false
        }
        scheduler.syncNow(); runCurrent()
        advanceTimeBy(1_000)
        scheduler.syncNow() // e.g. the app came to the foreground
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(0L, 1_000L), runs, "served at once, and no further retry once back online")
    }

    @Test
    fun noRetryWhenShouldRetryIsFalse() = runTest {
        var runs = 0
        val scheduler = SyncScheduler(backgroundScope, retryDelayMillis = 1_000) { runs++ }
        scheduler.syncNow()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, runs)
    }

    @Test
    fun aFailingSyncDoesNotKillTheScheduler() = runTest {
        var calls = 0
        val scheduler = SyncScheduler(backgroundScope, debounceMillis = 100) {
            calls++
            if (calls == 1) error("boom")
        }
        scheduler.syncNow(); runCurrent()
        scheduler.syncNow(); runCurrent()
        assertEquals(2, calls)
    }
}
