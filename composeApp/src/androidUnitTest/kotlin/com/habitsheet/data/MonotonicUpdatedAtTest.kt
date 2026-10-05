package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionAck
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A device clock that jumps backwards must never make a stored row look older (real SQLite). */
class MonotonicUpdatedAtTest {
    private val day = LocalDate(2026, 8, 5)
    private val habit = DailyHabit("run", "Run", null, 10, 0, true, LocalDate(2026, 8, 1), null, 1000, 1000)

    private suspend fun withRepository(block: suspend (LocalHabitRepository) -> Unit) {
        val file = Files.createTempFile("habit-sheet-monotonic", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val repo = LocalHabitRepository({ JdbcSqliteDriver(url) })
        try {
            block(repo)
        } finally {
            repo.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun editingHabitsAndCategoriesWithAnOlderClockKeepsTheNewerStamp() = runTest {
        withRepository { repo ->
            repo.saveCategory(Category("c", "Cat", 0, true, 5_000))
            repo.saveCategory(Category("c", "Cat renamed", 0, true, 100))
            assertEquals(5_000, repo.snapshot.value.categories.single { it.id == "c" }.updatedAtEpochMillis)
            assertEquals("Cat renamed", repo.snapshot.value.categories.single { it.id == "c" }.name)

            repo.saveDailyHabit(habit.copy(updatedAtEpochMillis = 5_000))
            repo.saveDailyHabit(habit.copy(name = "Run more", updatedAtEpochMillis = 100))
            repo.archiveDailyHabit("run", day, 50)
            repo.updateDailyHabitOrders(mapOf("run" to 3), 20)
            val saved = repo.snapshot.value.dailyHabits.single()
            assertEquals(5_000, saved.updatedAtEpochMillis)
            assertEquals("Run more", saved.name)
            assertEquals(3, saved.displayOrder)
            assertEquals(day, saved.archivedOn)
            repo.restoreDailyHabit("run", 10)
            assertEquals(5_000, repo.snapshot.value.dailyHabits.single().updatedAtEpochMillis)

            val weekly = WeeklyHabit("gym", "Gym", null, 0, true, LocalDate(2026, 8, 1), null, 1000, 7_000)
            repo.saveWeeklyHabit(weekly)
            repo.saveWeeklyHabit(weekly.copy(name = "Gym 2", updatedAtEpochMillis = 1))
            repo.archiveWeeklyHabit("gym", day, 2)
            repo.updateWeeklyHabitOrders(mapOf("gym" to 1), 3)
            repo.restoreWeeklyHabit("gym", 4)
            assertEquals(7_000, repo.snapshot.value.weeklyHabits.single().updatedAtEpochMillis)
        }
    }

    @Test
    fun plansKeepTheNewerStampButAlwaysTakeTheNewContent() = runTest {
        withRepository { repo ->
            repo.saveDailyHabit(habit)
            repo.saveDayPlan(DayPlan("run", day, "Easy", false, 9_000, "p1"))
            repo.saveDayPlan(DayPlan("run", day, "Hard", false, 100, "p1"))
            val dayPlan = repo.snapshot.value.dayPlans.single()
            assertEquals("Hard", dayPlan.detail)
            assertEquals(9_000, dayPlan.updatedAtEpochMillis)

            repo.saveWeeklyPlan(WeeklyPlan("run", 2, "A", 9_000))
            repo.saveWeeklyPlan(WeeklyPlan("run", 2, "B", 100))
            val weeklyPlan = repo.snapshot.value.weeklyPlans.single()
            assertEquals("B", weeklyPlan.detail)
            assertEquals(9_000, weeklyPlan.updatedAtEpochMillis)
        }
    }

    @Test
    fun completionsAlwaysAdvanceSoAnUploadAcknowledgementCanNeverMatchALaterToggle() = runTest {
        withRepository { repo ->
            repo.saveDailyHabit(habit)
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 5_000))
            val uploaded = repo.snapshot.value.dailyCompletions.single().updatedAtEpochMillis

            // The phone's clock jumps back an hour, then the person un-checks the habit.
            repo.setDailyCompletion(DailyHabitCompletion("run", day, false, 5_000 - 3_600_000))
            val later = repo.snapshot.value.dailyCompletions.single()
            assertTrue(later.updatedAtEpochMillis > uploaded)
            assertEquals(false, later.completed)

            // The sync that uploaded the old value finishes now: it must NOT clear the newer pending toggle.
            repo.applySheetSync(
                SheetSyncChanges(completionsToAcknowledge = listOf(CompletionAck(later.planId, day, uploaded)), managedHabitIds = setOf("run")),
                emptySet(),
                1,
            )
            assertEquals(1, repo.snapshot.value.pendingCompletions.size)
        }
    }

    @Test
    fun weeklyCompletionsAdvanceToo() = runTest {
        withRepository { repo ->
            repo.saveWeeklyHabit(WeeklyHabit("gym", "Gym", null, 0, true, LocalDate(2026, 8, 1), null, 1, 1))
            repo.setWeeklyCompletion(WeeklyHabitCompletion("gym", day, true, 5_000))
            repo.setWeeklyCompletion(WeeklyHabitCompletion("gym", day, false, 10))
            val stored = repo.snapshot.value.weeklyCompletions.single()
            assertEquals(5_001, stored.updatedAtEpochMillis)
            assertEquals(false, stored.completed)
        }
    }

    @Test
    fun sheetAppliedRowsAlsoNeverGoBack() = runTest {
        withRepository { repo ->
            repo.saveDailyHabit(habit)
            repo.saveDayPlan(DayPlan("run", day, "Easy", false, 9_000, "p1"))
            repo.applySheetSync(
                SheetSyncChanges(
                    plansToSave = listOf(DayPlan("run", day, "From sheet", false, 10, "p1")),
                    completionsToSave = listOf(DailyHabitCompletion("run", day, true, 10, "p1")),
                    managedHabitIds = setOf("run"),
                ),
                emptySet(),
                1,
            )
            assertEquals("From sheet", repo.snapshot.value.dayPlans.single().detail)
            assertEquals(9_000, repo.snapshot.value.dayPlans.single().updatedAtEpochMillis)
        }
    }
}
