package com.habitsheet.app.widget

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.platform.LogLevel
import com.habitsheet.platform.RecordingLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The widget's data access and rules, without RemoteViews or a launcher (P1-2 step 9). */
class HabitWidgetLogicTest {
    private val today = LocalDate(2026, 10, 5) // a Monday; the month's second week starts on the 8th
    private val start = LocalDate(2026, 9, 1)
    private val read = DailyHabit("read", "Read", null, 20, 0, true, start, null, 1, 1)
    private val run = DailyHabit("run", "Run", null, 12, 1, true, start, null, 1, 1, datedOnly = true)
    private val call = WeeklyHabit("call", "Call parents", null, 0, true, start, null, 1, 1)
    private val laterWeekly = WeeklyHabit("later", "Starts next month", null, 1, true, LocalDate(2026, 11, 1), null, 1, 1)

    private fun snapshot() = HabitSnapshot(
        dailyHabits = listOf(read, run),
        dayPlans = listOf(
            DayPlan("run", today, "Long", false, 1, "run-am"),
            DayPlan("run", today, "Strides", false, 1, "run-pm"),
        ),
        dailyCompletions = listOf(DailyHabitCompletion("run", today, true, 1, planId = "run-am")),
        weeklyHabits = listOf(call, laterWeekly),
        weeklyCompletions = listOf(WeeklyHabitCompletion("call", LocalDate(2026, 10, 1), true, 1)),
    )

    @Test
    fun itemsAreTodaysSessionsAndActiveWeeklyHabitsWithOpenOnesFirst() {
        val items = HabitWidgetLogic.items(snapshot(), today)
        assertEquals(
            listOf(
                WidgetHabit("read", "read|$today", "Read", weekly = false, completed = false),
                WidgetHabit("run", "run-pm", "Run · Strides", weekly = false, completed = false),
                WidgetHabit("run", "run-am", "Run · Long", weekly = false, completed = true),
                WidgetHabit("call", null, "Call parents", weekly = true, completed = true),
            ),
            items,
        )
        assertEquals(2, items.count { it.completed }, "progress: 2 of 4")
    }

    @Test
    fun anOldWidgetButtonIsIgnored() {
        assertTrue(HabitWidgetLogic.isForToday(today.toString(), "read", today))
        assertFalse(HabitWidgetLogic.isForToday("2026-10-04", "read", today), "rendered yesterday, tapped after midnight")
        assertFalse(HabitWidgetLogic.isForToday(null, "read", today))
        assertFalse(HabitWidgetLogic.isForToday(today.toString(), " ", today))
    }

    @Test
    fun toggleDailyFlipsExactlyTheTappedSession() = runTest {
        val store = InMemoryHabitRepository(snapshot())
        HabitWidgetLogic.toggleDaily(store, "run-pm", "run", today, nowMillis = 10)
        assertEquals(setOf("run-am", "run-pm"), store.snapshot.value.dailyCompletions.filter { it.completed }.map { it.planId }.toSet())
        HabitWidgetLogic.toggleDaily(store, "run-am", "run", today, nowMillis = 11)
        assertEquals(setOf("run-pm"), store.snapshot.value.dailyCompletions.filter { it.completed }.map { it.planId }.toSet())
    }

    @Test
    fun aButtonWithoutPlanIdTogglesTheHabitsFirstSessionAndUnknownOnesNothing() = runTest {
        val store = InMemoryHabitRepository(snapshot())
        HabitWidgetLogic.toggleDaily(store, null, "read", today, nowMillis = 10)
        assertTrue(store.snapshot.value.dailyCompletions.any { it.planId == "read|$today" && it.completed })

        val before = store.snapshot.value
        HabitWidgetLogic.toggleDaily(store, "nope", "read", today, nowMillis = 11)
        HabitWidgetLogic.toggleDaily(store, null, "deleted", today, nowMillis = 12)
        assertEquals(before, store.snapshot.value)
    }

    @Test
    fun toggleWeeklyFlipsThisWeekOnlyAndIgnoresInactiveHabits() = runTest {
        val store = InMemoryHabitRepository(snapshot())
        HabitWidgetLogic.toggleWeekly(store, "call", today, nowMillis = 10)
        assertEquals(
            listOf(LocalDate(2026, 10, 1) to false),
            store.snapshot.value.weeklyCompletions.map { it.weekStartDate to it.completed },
            "Oct 5 falls in the week that starts on Oct 1",
        )
        val before = store.snapshot.value
        HabitWidgetLogic.toggleWeekly(store, "later", today, nowMillis = 11)
        assertEquals(before, store.snapshot.value)
    }

    @Test
    fun aWidgetCheckOffOnTheRealDatabaseWaitsForUploadLikeOneMadeInTheApp() = runTest {
        val file = Files.createTempFile("habit-sheet-widget", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        try {
            LocalHabitRepository({ JdbcSqliteDriver(url) }).also { it.restoreFromSnapshot(snapshot()) }.close()
            val opener = WidgetStoreOpener {
                LocalHabitRepository({ JdbcSqliteDriver(url) }).let { repo -> WidgetStore(repo) { repo.close() } }
            }
            val result = runWidgetWork(opener, RecordingLogger()) { store -> HabitWidgetLogic.toggleDaily(store, "run-pm", "run", today, nowMillis = 10) }
            assertTrue(result)

            val reopened = LocalHabitRepository({ JdbcSqliteDriver(url) })
            try {
                assertTrue(CompletionKey("run-pm", today) in reopened.snapshot.value.pendingCompletions)
                assertTrue(reopened.snapshot.value.dailyCompletions.single { it.planId == "run-pm" }.completed)
            } finally {
                reopened.close()
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun twoQuickTapsOnOneRowCheckThenUncheck() = runTest {
        // Each tap opens its own store; the lock must cover opening it, or the second tap would read the data from
        // before the first one wrote and check the row a second time instead of unchecking it.
        val file = Files.createTempFile("habit-sheet-widget-taps", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        try {
            LocalHabitRepository({ JdbcSqliteDriver(url) }).also { it.restoreFromSnapshot(snapshot()) }.close()
            val opener = WidgetStoreOpener {
                LocalHabitRepository({ JdbcSqliteDriver(url) }).let { repo -> WidgetStore(repo) { repo.close() } }
            }
            val lock = Mutex()
            val taps = List(2) { tap ->
                async {
                    runWidgetWork(opener, RecordingLogger(), lock = lock) { store ->
                        yield() // the other tap runs now if nothing stops it
                        HabitWidgetLogic.toggleDaily(store, "run-pm", "run", today, nowMillis = 10L + tap)
                    }
                }
            }
            assertTrue(taps.awaitAll().all { it })
            val reopened = LocalHabitRepository({ JdbcSqliteDriver(url) })
            try {
                assertFalse(reopened.snapshot.value.dailyCompletions.single { it.planId == "run-pm" }.completed)
            } finally {
                reopened.close()
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun aDatabaseThatCannotBeOpenedIsLoggedNotThrown() = runTest {
        val logger = RecordingLogger()
        val result = runWidgetWork(WidgetStoreOpener { error("file is not a database") }, logger) { }
        assertFalse(result)
        assertEquals(LogLevel.ERROR, logger.entries.single().level)
        assertFalse("not a database" in logger.printed(), "only the class name is logged")
    }

    @Test
    fun workIsBoundedAndTheStoreIsAlwaysClosed() = runTest {
        val logger = RecordingLogger()
        var closed = 0
        val opener = WidgetStoreOpener { WidgetStore(InMemoryHabitRepository()) { closed++ } }

        assertTrue(runWidgetWork(opener, logger) { })
        assertFalse(runWidgetWork(opener, logger) { error("boom") })
        assertFalse(runWidgetWork(opener, logger, timeout = 8.seconds) { awaitCancellation() }, "a stuck write gives up before the broadcast deadline")
        assertEquals(3, closed)
        assertNull(logger.entries.firstOrNull { it.level != LogLevel.ERROR })
    }
}
