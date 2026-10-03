package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.presentation.DateProvider
import com.habitsheet.presentation.MonthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measures the cost of `LocalHabitRepository.loadSnapshot()` (every write ends with one) with a heavy user:
 * 5 years x 20 daily habits of check-offs (about 36,000 rows) plus plans and weekly habits.
 *
 * The timings are printed (see the test report's standard output) and recorded in
 * docs/production-plan/notes/p1-1-load-snapshot-timings.md. The assertions are loose sanity bounds, not the
 * 100 ms target, so a slow CI machine cannot make the build flaky; the target is judged from the printed numbers.
 */
class LoadSnapshotPerformanceTest {
    private val start = LocalDate(2022, 1, 1)
    private val days = 5 * 365 + 1

    private fun heavySnapshot(): HabitSnapshot {
        val categories = (0 until 5).map { Category("c$it", "Category $it", it, true, 1) }
        val habits = (0 until 20).map {
            DailyHabit("h$it", "Habit $it", "c${it % 5}", 25, it, true, start, null, 1, 1)
        }
        val weekly = (0 until 5).map { WeeklyHabit("w$it", "Weekly $it", "c$it", it, true, start, null, 1, 1) }
        val dates = (0 until days).map { start.plus(DatePeriod(days = it)) }
        val completions = habits.flatMap { habit ->
            dates.mapIndexed { index, date -> DailyHabitCompletion(habit.id, date, index % 3 != 0, 1_000L + index) }
        }
        val weeklyCompletions = weekly.flatMap { habit ->
            dates.filterIndexed { index, _ -> index % 7 == 0 }.map { WeeklyHabitCompletion(habit.id, it, true, 1) }
        }
        // A sheet-managed habit has a dated session every day for the visible window; keep it realistic: 180 days per habit.
        val dayPlans = habits.take(5).flatMap { habit ->
            dates.takeLast(180).map { date -> DayPlan(habit.id, date, "Session ${habit.id}", false, 1) }
        }
        return HabitSnapshot(categories, habits, completions, weekly, weeklyCompletions, emptyList(), dayPlans)
    }

    private fun median(values: List<Long>) = values.sorted()[values.size / 2]

    @Test
    fun loadSnapshotWithFiveYearsOfTwentyHabits() = runTest {
        val file = Files.createTempFile("habit-sheet-perf", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val repo = LocalHabitRepository({ JdbcSqliteDriver(url) })
        try {
            val snapshot = heavySnapshot()
            val seedStart = System.nanoTime()
            repo.restoreFromSnapshot(snapshot)
            val seedMs = (System.nanoTime() - seedStart) / 1_000_000
            assertEquals(20 * days, repo.snapshot.value.dailyCompletions.size)

            repeat(3) { repo.refresh() } // warm up JIT, SQLite caches
            val loads = (1..15).map {
                val t = System.nanoTime()
                repo.refresh()
                (System.nanoTime() - t) / 1_000_000
            }

            // What a check-off costs the person: one write plus the reload that follows it.
            val writes = (1..15).map { index ->
                val date = start.plus(DatePeriod(days = index))
                val t = System.nanoTime()
                repo.setDailyCompletion(DailyHabitCompletion("h0", date, index % 2 == 0, 5_000_000L + index))
                (System.nanoTime() - t) / 1_000_000
            }

            // Recomputing the month screen's state from a snapshot this size (measured on the in-memory repository).
            val dates = object : DateProvider {
                override fun today() = LocalDate(2026, 9, 15)
                override fun nowEpochMillis() = 0L
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val uiState = (1..5).map {
                val t = System.nanoTime()
                val vm = MonthViewModel(InMemoryHabitRepository(snapshot), dates, scope)
                val ms = (System.nanoTime() - t) / 1_000_000
                vm.close()
                ms
            }
            scope.cancel()

            println("PERF rows=${snapshot.dailyCompletions.size} dailyCompletions, ${snapshot.weeklyCompletions.size} weeklyCompletions, ${snapshot.dayPlans.size} dayPlans")
            println("PERF restore (seed) ms=$seedMs")
            println("PERF loadSnapshot ms: median=${median(loads)} min=${loads.min()} max=${loads.max()} all=$loads")
            println("PERF check-off (write + reload) ms: median=${median(writes)} max=${writes.max()}")
            println("PERF month state recompute ms (first of 5, then warm): $uiState")

            assertTrue(median(loads) < 5_000, "loadSnapshot is absurdly slow: ${median(loads)} ms")
        } finally {
            repo.close()
            Files.deleteIfExists(file)
        }
    }
}
