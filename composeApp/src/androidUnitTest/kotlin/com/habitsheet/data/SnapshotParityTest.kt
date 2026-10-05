package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionAck
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The repository keeps an in-memory [HabitSnapshot] that the UI reads. Whatever shortcut a write takes to update it,
 * the result must be exactly what a fresh read of the database gives (same rows, same values, same order, same
 * pending and sheet-managed sets). Every kind of write is checked against a second repository opened on the same file.
 */
class SnapshotParityTest {
    private val day = LocalDate(2026, 10, 5)

    private suspend fun withRepository(block: suspend (LocalHabitRepository, assertParity: (String) -> Unit) -> Unit) {
        val file = Files.createTempFile("habit-sheet-parity", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val repo = LocalHabitRepository({ JdbcSqliteDriver(url) })
        try {
            block(repo) { step ->
                val fresh = LocalHabitRepository({ JdbcSqliteDriver(url) })
                try {
                    assertEquals(fresh.snapshot.value, repo.snapshot.value, "after: $step")
                } finally {
                    fresh.close()
                }
            }
        } finally {
            repo.close()
            Files.deleteIfExists(file)
        }
    }

    private fun habit(id: String, order: Int) = DailyHabit(id, "Habit $id", "c1", 10, order, true, day.minusDays(30), null, 1, 1)
    private fun LocalDate.minusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() - n)
    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)

    @Test
    fun everyWriteLeavesTheSnapshotEqualToAFreshRead() = runTest {
        withRepository { repo, parity ->
            repo.saveCategory(Category("c1", "Health", 0, true, 1)); parity("saveCategory")
            repo.saveDailyHabit(habit("b", 1)); parity("saveDailyHabit b")
            repo.saveDailyHabit(habit("a", 0)); parity("saveDailyHabit a")
            repo.saveWeeklyHabit(WeeklyHabit("w", "Weekly", "c1", 0, true, day.minusDays(30), null, 1, 1)); parity("saveWeeklyHabit")

            // Check-offs out of date order, two sessions of one habit on one day, and toggling an existing one.
            repo.setDailyCompletion(DailyHabitCompletion("b", day, true, 10)); parity("check b today")
            repo.setDailyCompletion(DailyHabitCompletion("a", day.minusDays(3), true, 11)); parity("check a earlier")
            repo.setDailyCompletion(DailyHabitCompletion("a", day, true, 12, planId = "a-pm")); parity("check a second session")
            repo.setDailyCompletion(DailyHabitCompletion("a", day, true, 13, planId = "a-am")); parity("check a first session")
            repo.setDailyCompletion(DailyHabitCompletion("b", day, false, 14)); parity("uncheck b today")
            repo.setDailyCompletion(DailyHabitCompletion("b", day, true, 5)); parity("check b with an older clock")
            repo.setDailyCompletion(DailyHabitCompletion("a", day.plusDays(2), false, 15)); parity("uncheck a later")

            repo.setWeeklyCompletion(WeeklyHabitCompletion("w", day, true, 20)); parity("weekly check")
            repo.setWeeklyCompletion(WeeklyHabitCompletion("w", day.minusDays(7), true, 21)); parity("weekly check earlier week")
            repo.setWeeklyCompletion(WeeklyHabitCompletion("w", day, false, 19)); parity("weekly uncheck with an older clock")

            repo.saveDayPlan(DayPlan("a", day, "Easy", false, 30, "a-am")); parity("saveDayPlan")
            repo.saveWeeklyPlan(WeeklyPlan("b", 2, "Tempo", 31)); parity("saveWeeklyPlan")
            repo.archiveDailyHabit("b", day, 32); parity("archive")
            repo.restoreDailyHabit("b", 33); parity("restore")
            repo.updateDailyHabitOrders(mapOf("a" to 5, "b" to 4), 34); parity("reorder")

            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/one/edit"); parity("link sheet")
            repo.setDailyCompletion(DailyHabitCompletion("a", day.minusDays(1), true, 40)); parity("pending check")
            val pending = repo.snapshot.value.dailyCompletions.first { it.date == day.minusDays(1) }
            repo.applySheetSync(
                SheetSyncChanges(
                    completionsToSave = listOf(DailyHabitCompletion("b", day.minusDays(2), true, 41)),
                    completionsToAcknowledge = listOf(CompletionAck(pending.planId, pending.date, pending.updatedAtEpochMillis)),
                    managedHabitIds = setOf("a"),
                ),
                setOf("a-am"), 42,
            )
            parity("applySheetSync")
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/two/edit"); parity("link another sheet")

            repo.deleteDayPlanById("a-am"); parity("deleteDayPlanById")
            repo.deleteWeeklyPlan("b", 2); parity("deleteWeeklyPlan")
            repo.deleteDailyHabit("b"); parity("deleteDailyHabit")
            repo.deleteWeeklyHabit("w"); parity("deleteWeeklyHabit")
            repo.deleteCategory("c1"); parity("deleteCategory")

            repo.restoreFromSnapshot(
                HabitSnapshot(
                    dailyHabits = listOf(habit("r", 0).copy(categoryId = null)),
                    dailyCompletions = listOf(DailyHabitCompletion("r", day, true, 50)),
                ),
                BackupSettings(themeMode = 1),
            )
            parity("restoreFromSnapshot")
            repo.setDailyCompletion(DailyHabitCompletion("r", day.minusDays(1), true, 51)); parity("check after restore")
            repo.clearAllData(); parity("clearAllData")
        }
    }

    @Test
    fun snapshotListsKeepTheirDocumentedOrder() = runTest {
        withRepository { repo, parity ->
            repo.saveCategory(Category("c2", "Second", 1, true, 1))
            repo.saveCategory(Category("c1", "First", 1, true, 1))
            repo.saveCategory(Category("c0", "Zero", 0, true, 1))
            repo.saveDailyHabit(habit("b", 0))
            repo.saveDailyHabit(habit("a", 0))
            repo.saveDailyHabit(habit("c", -1))
            repo.saveWeeklyHabit(WeeklyHabit("w2", "W2", null, 1, true, day, null, 1, 1))
            repo.saveWeeklyHabit(WeeklyHabit("w1", "W1", null, 1, true, day, null, 1, 1))
            listOf(
                DailyHabitCompletion("c", day, true, 1),
                DailyHabitCompletion("a", day, true, 2),
                DailyHabitCompletion("b", day.minusDays(1), true, 3),
                DailyHabitCompletion("a", day, false, 4, planId = "a-2"),
                DailyHabitCompletion("a", day, true, 5, planId = "a-0"),
                DailyHabitCompletion("b", day.plusDays(1), true, 6),
            ).forEach { repo.setDailyCompletion(it) }
            listOf(
                WeeklyHabitCompletion("w2", day, true, 1),
                WeeklyHabitCompletion("w1", day, true, 2),
                WeeklyHabitCompletion("w1", day.minusDays(7), true, 3),
            ).forEach { repo.setWeeklyCompletion(it) }
            repo.saveWeeklyPlan(WeeklyPlan("b", 3, "x", 1))
            repo.saveWeeklyPlan(WeeklyPlan("a", 3, "x", 1))
            repo.saveWeeklyPlan(WeeklyPlan("c", 1, "x", 1))
            repo.saveDayPlan(DayPlan("b", day, "x", false, 1, "p1"))
            repo.saveDayPlan(DayPlan("a", day, "x", false, 1, "p2"))
            repo.saveDayPlan(DayPlan("c", day.minusDays(1), "x", false, 1, "p3"))
            parity("seeding")

            val snapshot = repo.snapshot.value
            val ours = setOf("c0", "c1", "c2") // a new database also holds the default categories
            assertEquals(listOf("c0", "c1", "c2"), snapshot.categories.map { it.id }.filter { it in ours }, "categories: display order, id")
            assertEquals(listOf("c", "a", "b"), snapshot.dailyHabits.map { it.id }, "daily habits: display order, id")
            assertEquals(listOf("w1", "w2"), snapshot.weeklyHabits.map { it.id }, "weekly habits: display order, id")
            assertEquals(
                listOf(
                    "b|${day.minusDays(1)}",
                    "a-0", "a-2", "a|$day", // same date and habit: by plan id
                    "c|$day",
                    "b|${day.plusDays(1)}",
                ),
                snapshot.dailyCompletions.map { it.planId },
                "daily completions: date, habit id, plan id",
            )
            assertEquals(
                listOf("w1" to day.minusDays(7), "w1" to day, "w2" to day),
                snapshot.weeklyCompletions.map { it.weeklyHabitId to it.weekStartDate },
                "weekly completions: week start, habit id",
            )
            assertEquals(listOf("c" to 1, "a" to 3, "b" to 3), snapshot.weeklyPlans.map { it.habitId to it.weekday }, "weekly plans: weekday, habit id")
            assertEquals(listOf("p3", "p2", "p1"), snapshot.dayPlans.map { it.id }, "day plans: date, habit id")
            assertEquals(
                setOf("c|$day", "a|$day", "b|${day.minusDays(1)}", "a-2", "a-0", "b|${day.plusDays(1)}").map { planId ->
                    snapshot.dailyCompletions.first { it.planId == planId }.let { CompletionKey(it.planId, it.date) }
                }.toSet(),
                snapshot.pendingCompletions,
            )
        }
    }
}
