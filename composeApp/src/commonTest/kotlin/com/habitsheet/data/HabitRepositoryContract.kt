package com.habitsheet.data

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
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A repository under test and a way to "restart the app": a fresh instance on the same storage. */
interface RepositoryHandle {
    val repository: HabitRepository

    /** Closes the repository and opens a new one over the same data (the same instance for in-memory storage). */
    suspend fun reopen(): HabitRepository
}

/**
 * Behaviour every [HabitRepository] must share: SQLite in production, the in-memory double in ViewModel tests.
 * Running one set of expectations against both keeps the double honest (what a ViewModel test sees is what the app
 * does) and covers every store method, idempotent upserts and delete cascades against the real database.
 *
 * Every test starts from `clearAllData()`, so the ten default categories SQLite seeds on a new file are out of the way.
 */
abstract class HabitRepositoryContract {
    protected abstract suspend fun withRepository(block: suspend (RepositoryHandle) -> Unit)

    private val day = LocalDate(2026, 10, 5)
    private val monday = LocalDate(2026, 10, 5)

    private fun category(id: String, name: String = id, order: Int = 0, stamp: Long = 100) = Category(id, name, order, true, stamp)

    @Suppress("LongParameterList")
    private fun habit(id: String, name: String = id, order: Int = 0, goal: Int = 10, categoryId: String? = null, stamp: Long = 100) =
        DailyHabit(id, name, categoryId, goal, order, true, LocalDate(2026, 9, 1), null, stamp, stamp)

    private fun weeklyHabit(id: String, order: Int = 0, categoryId: String? = null, stamp: Long = 100) =
        WeeklyHabit(id, id, categoryId, order, true, LocalDate(2026, 9, 1), null, stamp, stamp)

    private suspend fun test(block: suspend (HabitRepository, RepositoryHandle) -> Unit) = withRepository { handle ->
        handle.repository.clearAllData()
        block(handle.repository, handle)
    }

    // --- categories ---

    @Test
    fun savingACategoryTwiceKeepsOneRowAndAnEditReplacesItsFields() = runTest {
        test { repo, handle ->
            repo.saveCategory(category("c1", "Fitness"))
            repo.saveCategory(category("c1", "Fitness"))
            assertEquals(listOf("Fitness"), repo.snapshot.value.categories.map { it.name })

            repo.saveCategory(category("c1", "Health", order = 3, stamp = 200))
            assertEquals(Category("c1", "Health", 3, true, 200), repo.snapshot.value.categories.single())
            assertEquals(repo.snapshot.value.categories, handle.reopen().snapshot.value.categories)
        }
    }

    @Test
    fun deletingACategoryKeepsItsHabitsAndDetachesThem() = runTest {
        test { repo, _ ->
            repo.saveCategory(category("c1"))
            repo.saveCategory(category("c2"))
            repo.saveDailyHabit(habit("run", categoryId = "c1"))
            repo.saveDailyHabit(habit("read", categoryId = "c2"))
            repo.saveWeeklyHabit(weeklyHabit("gym", categoryId = "c1"))

            repo.deleteCategory("c1")

            val snapshot = repo.snapshot.value
            assertEquals(listOf("c2"), snapshot.categories.map { it.id })
            assertNull(snapshot.dailyHabits.single { it.id == "run" }.categoryId)
            assertEquals("c2", snapshot.dailyHabits.single { it.id == "read" }.categoryId)
            assertNull(snapshot.weeklyHabits.single().categoryId)
        }
    }

    // --- daily habits ---

    @Test
    fun savingADailyHabitIsIdempotentAndNeverMovesUpdatedAtBackwards() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run", "Run", stamp = 500))
            repo.saveDailyHabit(habit("run", "Run", stamp = 500))
            assertEquals(1, repo.snapshot.value.dailyHabits.size)

            // Renamed with a device clock that went backwards: new content, but the newer stamp stays.
            repo.saveDailyHabit(habit("run", "Jog", goal = 12, stamp = 200))
            val stored = repo.snapshot.value.dailyHabits.single()
            assertEquals("Jog", stored.name)
            assertEquals(12, stored.monthlyGoal)
            assertEquals(500, stored.updatedAtEpochMillis)
            assertEquals(stored, handle.reopen().snapshot.value.dailyHabits.single())
        }
    }

    @Test
    fun aNegativeGoalIsRejectedAndChangesNothing() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("run"))
            val before = repo.snapshot.value
            assertFailsWith<IllegalArgumentException> { repo.saveDailyHabit(habit("run", goal = -1)) }
            assertFailsWith<IllegalArgumentException> { repo.saveDailyHabit(habit("new", goal = -1)) }
            assertEquals(before, repo.snapshot.value)
        }
    }

    @Test
    fun archivingAndRestoringADailyHabitKeepsItsHistoryAndTheNewerStamp() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run", stamp = 500))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 600))

            repo.archiveDailyHabit("run", LocalDate(2026, 10, 10), updatedAtEpochMillis = 200)
            val archived = repo.snapshot.value.dailyHabits.single()
            assertEquals(false, archived.active)
            assertEquals(LocalDate(2026, 10, 10), archived.archivedOn)
            assertEquals(500, archived.updatedAtEpochMillis, "an older clock never lowers updated_at")
            assertEquals(1, repo.snapshot.value.dailyCompletions.size)

            repo.restoreDailyHabit("run", updatedAtEpochMillis = 900)
            val restored = repo.snapshot.value.dailyHabits.single()
            assertEquals(true, restored.active)
            assertNull(restored.archivedOn)
            assertEquals(900, restored.updatedAtEpochMillis)
            assertEquals(restored, handle.reopen().snapshot.value.dailyHabits.single())
        }
    }

    @Test
    fun reorderingDailyHabitsIgnoresUnknownIds() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("a", order = 0))
            repo.saveDailyHabit(habit("b", order = 1))

            repo.updateDailyHabitOrders(mapOf("a" to 5, "b" to 4, "ghost" to 0), updatedAtEpochMillis = 300)

            val byId = repo.snapshot.value.dailyHabits.associateBy { it.id }
            assertEquals(setOf("a", "b"), byId.keys)
            assertEquals(5, byId.getValue("a").displayOrder)
            assertEquals(4, byId.getValue("b").displayOrder)
            assertEquals(300, byId.getValue("a").updatedAtEpochMillis)
        }
    }

    @Test
    fun deletingADailyHabitRemovesOnlyItsOwnRows() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run"))
            repo.saveDailyHabit(habit("read"))
            repo.saveWeeklyPlan(WeeklyPlan("run", 1, "Easy", 100))
            repo.saveWeeklyPlan(WeeklyPlan("read", 1, "20 pages", 100))
            repo.saveDayPlan(DayPlan("run", day, "Tempo", false, 100, "run-1"))
            repo.saveDayPlan(DayPlan("read", day, "Chapter 2", false, 100, "read-1"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 100, "run-1"))
            repo.setDailyCompletion(DailyHabitCompletion("read", day, true, 100, "read-1"))

            repo.deleteDailyHabit("run")

            val snapshot = repo.snapshot.value
            assertEquals(listOf("read"), snapshot.dailyHabits.map { it.id })
            assertEquals(listOf("read"), snapshot.weeklyPlans.map { it.habitId })
            assertEquals(listOf("read-1"), snapshot.dayPlans.map { it.id })
            assertEquals(listOf("read-1"), snapshot.dailyCompletions.map { it.planId })
            assertEquals(snapshot.dailyCompletions, handle.reopen().snapshot.value.dailyCompletions)
        }
    }

    // --- weekly habits ---

    @Test
    fun weeklyHabitsAreIdempotentAndArchiveRestoreReorderAndDeleteWork() = runTest {
        test { repo, handle ->
            repo.saveWeeklyHabit(weeklyHabit("gym", stamp = 500))
            repo.saveWeeklyHabit(weeklyHabit("gym", stamp = 500))
            repo.saveWeeklyHabit(weeklyHabit("long", order = 1))
            assertEquals(2, repo.snapshot.value.weeklyHabits.size)

            repo.archiveWeeklyHabit("gym", LocalDate(2026, 10, 12), updatedAtEpochMillis = 200)
            val archived = repo.snapshot.value.weeklyHabits.single { it.id == "gym" }
            assertEquals(false, archived.active)
            assertEquals(LocalDate(2026, 10, 12), archived.archivedOn)
            assertEquals(500, archived.updatedAtEpochMillis)

            repo.restoreWeeklyHabit("gym", updatedAtEpochMillis = 800)
            assertEquals(true, repo.snapshot.value.weeklyHabits.single { it.id == "gym" }.active)

            repo.updateWeeklyHabitOrders(mapOf("gym" to 2, "long" to 0, "ghost" to 9), updatedAtEpochMillis = 900)
            assertEquals(mapOf("gym" to 2, "long" to 0), repo.snapshot.value.weeklyHabits.associate { it.id to it.displayOrder })

            repo.setWeeklyCompletion(WeeklyHabitCompletion("gym", monday, true, 100))
            repo.setWeeklyCompletion(WeeklyHabitCompletion("long", monday, true, 100))
            repo.deleteWeeklyHabit("gym")
            assertEquals(listOf("long"), repo.snapshot.value.weeklyHabits.map { it.id })
            assertEquals(listOf("long"), repo.snapshot.value.weeklyCompletions.map { it.weeklyHabitId })
            assertEquals(repo.snapshot.value.weeklyCompletions, handle.reopen().snapshot.value.weeklyCompletions)
        }
    }

    // --- check-offs ---

    @Test
    fun checkingTheSameSessionTwiceKeepsOneRowAdvancesTheStampAndStaysPending() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 1_000))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 1_000))
            val stored = repo.snapshot.value.dailyCompletions.single()
            assertEquals(true, stored.completed)
            assertTrue(stored.updatedAtEpochMillis > 1_000, "the repeat is strictly newer so an upload ack can tell them apart")
            assertEquals(setOf(CompletionKey("run|$day", day)), repo.snapshot.value.pendingCompletions)

            repo.setDailyCompletion(DailyHabitCompletion("run", day, false, 5))
            assertEquals(false, repo.snapshot.value.dailyCompletions.single().completed)
            assertEquals(1, handle.reopen().snapshot.value.dailyCompletions.size)
            assertEquals(setOf(CompletionKey("run|$day", day)), handle.reopen().snapshot.value.pendingCompletions)
        }
    }

    @Test
    fun twoSessionsOfOneHabitOnOneDayAreCheckedOffIndependently() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("run"))
            repo.saveDayPlan(DayPlan("run", day, "Easy", false, 100, "run-a"))
            repo.saveDayPlan(DayPlan("run", day, "Mobility", false, 100, "run-b"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 200, "run-a"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, false, 200, "run-b"))

            val byPlan = repo.snapshot.value.dailyCompletions.associate { it.planId to it.completed }
            assertEquals(mapOf("run-a" to true, "run-b" to false), byPlan)
        }
    }

    @Test
    fun aWeeklyCheckOffIsIdempotentAndAlwaysAdvances() = runTest {
        test { repo, _ ->
            repo.saveWeeklyHabit(weeklyHabit("gym"))
            repo.setWeeklyCompletion(WeeklyHabitCompletion("gym", monday, true, 1_000))
            repo.setWeeklyCompletion(WeeklyHabitCompletion("gym", monday, true, 1_000))
            val stored = repo.snapshot.value.weeklyCompletions.single()
            assertTrue(stored.completed && stored.updatedAtEpochMillis > 1_000)
        }
    }

    // --- plans ---

    @Test
    fun weeklyPlanUpsertsPerHabitAndWeekdayAndValidatesItsInput() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run"))
            repo.saveWeeklyPlan(WeeklyPlan("run", 2, "Easy", 100))
            repo.saveWeeklyPlan(WeeklyPlan("run", 2, "Easy", 100))
            repo.saveWeeklyPlan(WeeklyPlan("run", 4, "Intervals", 100))
            repo.saveWeeklyPlan(WeeklyPlan("run", 2, "Tempo", 50))
            val plans = repo.snapshot.value.weeklyPlans.associate { it.weekday to it }
            assertEquals(setOf(2, 4), plans.keys)
            assertEquals("Tempo", plans.getValue(2).detail)
            assertEquals(100, plans.getValue(2).updatedAtEpochMillis, "newer stamp kept, new content taken")

            val before = repo.snapshot.value
            assertFailsWith<IllegalArgumentException> { repo.saveWeeklyPlan(WeeklyPlan("run", 3, "  ", 100)) }
            assertFailsWith<IllegalArgumentException> { repo.saveWeeklyPlan(WeeklyPlan("run", 0, "Easy", 100)) }
            assertFailsWith<IllegalArgumentException> { repo.saveWeeklyPlan(WeeklyPlan("run", 8, "Easy", 100)) }
            assertEquals(before, repo.snapshot.value)

            repo.deleteWeeklyPlan("run", 2)
            assertEquals(listOf(4), repo.snapshot.value.weeklyPlans.map { it.weekday })
            repo.deleteWeeklyPlan("run", 2) // deleting what is not there is not an error
            assertEquals(repo.snapshot.value.weeklyPlans, handle.reopen().snapshot.value.weeklyPlans)
        }
    }

    @Test
    fun dayPlansUpsertByIdAllowSeveralSessionsAndDeleteByHabitAndDateOrById() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run"))
            repo.saveDayPlan(DayPlan("run", day, "Easy", false, 100, "a"))
            repo.saveDayPlan(DayPlan("run", day, "Easy", false, 100, "a"))
            repo.saveDayPlan(DayPlan("run", day, "Mobility", true, 100, "b"))
            assertEquals(setOf("a", "b"), repo.snapshot.value.dayPlans.map { it.id }.toSet())

            repo.saveDayPlan(DayPlan("run", day, "Easy 8 km", false, 50, "a"))
            val edited = repo.snapshot.value.dayPlans.single { it.id == "a" }
            assertEquals("Easy 8 km", edited.detail)
            assertEquals(100, edited.updatedAtEpochMillis)
            assertTrue(repo.snapshot.value.dayPlans.single { it.id == "b" }.skipped)

            val before = repo.snapshot.value
            assertFailsWith<IllegalArgumentException> { repo.saveDayPlan(DayPlan("run", day, " ", false, 100, "c")) }
            assertEquals(before, repo.snapshot.value)

            repo.deleteDayPlanById("a")
            assertEquals(listOf("b"), repo.snapshot.value.dayPlans.map { it.id })
            repo.saveDayPlan(DayPlan("run", LocalDate(2026, 10, 6), "Long", false, 100, "d"))
            repo.deleteDayPlan("run", day)
            assertEquals(listOf("d"), repo.snapshot.value.dayPlans.map { it.id })
            assertEquals(repo.snapshot.value.dayPlans, handle.reopen().snapshot.value.dayPlans)
        }
    }

    @Test
    fun removingASessionKeepsItsCheckOffAsHistory() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("run"))
            repo.saveDayPlan(DayPlan("run", day, "Easy", false, 100, "a"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 200, "a"))
            repo.deleteDayPlanById("a")
            assertEquals(listOf("a"), repo.snapshot.value.dailyCompletions.map { it.planId })
        }
    }

    // --- settings and sync state ---

    @Test
    fun settingsRoundTripAndSurviveARestart() = runTest {
        test { repo, handle ->
            repo.setOnboardingCompleted(true)
            repo.setThemeMode(2)
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/abc/edit")
            repo.setSheetLastSync(1234)
            repo.setSheetSyncedKeys(setOf("b|1", "a|1"))
            repo.setSheetManagedHabitIds(setOf("run", "read"))

            val reopened = handle.reopen()
            assertEquals(true, reopened.isOnboardingCompleted())
            assertEquals(2, reopened.getThemeMode())
            assertEquals("https://docs.google.com/spreadsheets/d/abc/edit", reopened.getSheetUrl())
            assertEquals(1234, reopened.getSheetLastSync())
            assertEquals(setOf("a|1", "b|1"), reopened.getSheetSyncedKeys())
            assertEquals(setOf("run", "read"), reopened.snapshot.value.sheetManagedHabitIds)
        }
    }

    @Test
    fun relinkingTheSameSheetKeepsSyncStateAndADifferentOneForgetsItButKeepsTheChecks() = runTest {
        test { repo, _ ->
            val a = "https://docs.google.com/spreadsheets/d/a/edit"
            repo.saveDailyHabit(habit("run"))
            repo.setSheetUrl(a)
            repo.setSheetLastSync(50)
            repo.setSheetSyncedKeys(setOf("k"))
            repo.setSheetManagedHabitIds(setOf("run"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 100))

            repo.setSheetUrl(a)
            assertEquals(50, repo.getSheetLastSync())
            assertEquals(setOf("k"), repo.getSheetSyncedKeys())
            assertEquals(1, repo.snapshot.value.pendingCompletions.size)

            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/b/edit")
            assertEquals(0, repo.getSheetLastSync())
            assertTrue(repo.getSheetSyncedKeys().isEmpty())
            assertTrue(repo.snapshot.value.sheetManagedHabitIds.isEmpty())
            assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
            assertEquals(1, repo.snapshot.value.dailyCompletions.size, "the check-off itself is kept")
        }
    }

    // --- applySheetSync ---

    private fun syncChanges() = SheetSyncChanges(
        planIdsToDelete = listOf("gone"),
        habitsToSave = listOf(habit("run", "Run", stamp = 300).copy(datedOnly = true), habit("yoga", "Yoga", stamp = 300).copy(datedOnly = true)),
        plansToSave = listOf(
            DayPlan("run", day, "Tempo", false, 300, "run-1"),
            DayPlan("yoga", day, "Flow", false, 300, "yoga-1"),
        ),
        completionsToSave = listOf(DailyHabitCompletion("yoga", day, true, 300, "yoga-1")),
        managedHabitIds = setOf("run", "yoga"),
    )

    @Test
    fun applyingTheSameSheetSyncTwiceLeavesTheSameState() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("run", "Old name"))
            repo.saveDayPlan(DayPlan("run", day, "Old", false, 100, "gone"))

            repo.applySheetSync(syncChanges(), setOf("run-1", "yoga-1"), lastSync = 777)
            val once = repo.snapshot.value
            repo.applySheetSync(syncChanges(), setOf("run-1", "yoga-1"), lastSync = 777)

            assertEquals(once, repo.snapshot.value)
            assertEquals(setOf("run-1", "yoga-1"), repo.getSheetSyncedKeys())
            assertEquals(777, repo.getSheetLastSync())
            assertEquals(setOf("Run", "Yoga"), once.dailyHabits.map { it.name }.toSet())
            assertEquals(setOf("run-1", "yoga-1"), once.dayPlans.map { it.id }.toSet())
            assertTrue(once.pendingCompletions.isEmpty(), "values from the sheet are not pending")
            assertEquals(once, handle.reopen().snapshot.value)
        }
    }

    @Test
    fun aSheetSyncWithABadRowChangesNothing() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("run"))
            repo.setSheetLastSync(10)
            val before = repo.snapshot.value
            val bad = syncChanges().copy(plansToSave = listOf(DayPlan("run", day, "Fine", false, 300, "ok"), DayPlan("run", day, " ", false, 300, "blank")))

            assertFailsWith<IllegalArgumentException> { repo.applySheetSync(bad, setOf("ok"), lastSync = 99) }

            assertEquals(before, repo.snapshot.value)
            assertEquals(10, repo.getSheetLastSync())
            assertTrue(repo.getSheetSyncedKeys().isEmpty())
        }
    }

    @Test
    fun anAcknowledgementClearsPendingOnlyForTheValueItSaw() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("run"))
            repo.setDailyCompletion(DailyHabitCompletion("run", day, true, 1_000))
            val stamp = repo.snapshot.value.dailyCompletions.single().updatedAtEpochMillis
            val managed = setOf("run")

            repo.applySheetSync(SheetSyncChanges(completionsToAcknowledge = listOf(CompletionAck("run|$day", day, stamp - 1)), managedHabitIds = managed), emptySet(), 1)
            assertEquals(1, repo.snapshot.value.pendingCompletions.size)

            repo.applySheetSync(SheetSyncChanges(completionsToAcknowledge = listOf(CompletionAck("run|$day", day, stamp)), managedHabitIds = managed), emptySet(), 2)
            assertTrue(repo.snapshot.value.pendingCompletions.isEmpty())
            assertEquals(true, repo.snapshot.value.dailyCompletions.single().completed)
        }
    }

    // --- backup, restore, reset ---

    private fun backup() = buildSnapshot(stamp = 400) {
        category("c1", "Fitness")
        dailyHabit("run", "Run", category = "c1", goal = 12, createdOn = LocalDate(2026, 9, 1))
        weeklyHabit("gym", createdOn = LocalDate(2026, 9, 1))
        weeklyPlan("run", 1, "Easy")
        dayPlan("run", day, "Tempo", id = "run-1")
        done("run", day, planId = "run-1")
        weeklyDone("gym", monday)
    }

    @Test
    fun restoringReplacesEverythingClearsSyncStateAndKeepsTheLinkUnlessAsked() = runTest {
        test { repo, handle ->
            repo.saveDailyHabit(habit("old"))
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/mine/edit")
            repo.setSheetLastSync(5)
            repo.setSheetSyncedKeys(setOf("k"))
            repo.setSheetManagedHabitIds(setOf("old"))
            repo.setDailyCompletion(DailyHabitCompletion("old", day, true, 100))
            val settings = BackupSettings(themeMode = 2, onboardingCompleted = true, sheetUrl = "https://docs.google.com/spreadsheets/d/other/edit")

            repo.restoreFromSnapshot(backup(), settings, restoreSheetLink = false)

            val snapshot = repo.snapshot.value
            assertEquals(backup().copy(sheetManagedHabitIds = emptySet(), pendingCompletions = emptySet()), snapshot)
            assertEquals("https://docs.google.com/spreadsheets/d/mine/edit", repo.getSheetUrl())
            assertEquals(0, repo.getSheetLastSync())
            assertTrue(repo.getSheetSyncedKeys().isEmpty())
            assertEquals(2, repo.getThemeMode())
            assertEquals(true, repo.isOnboardingCompleted())
            assertEquals(snapshot, handle.reopen().snapshot.value)

            repo.restoreFromSnapshot(backup(), settings, restoreSheetLink = true)
            assertEquals("https://docs.google.com/spreadsheets/d/other/edit", repo.getSheetUrl())
        }
    }

    @Test
    fun anInconsistentBackupIsRejectedBeforeAnythingIsCleared() = runTest {
        test { repo, _ ->
            repo.saveDailyHabit(habit("keep"))
            repo.setThemeMode(1)
            val before = repo.snapshot.value
            val orphan = HabitSnapshot(dailyCompletions = listOf(DailyHabitCompletion("ghost", day, true, 1)))

            assertFailsWith<IllegalArgumentException> { repo.restoreFromSnapshot(orphan, BackupSettings(themeMode = 2, onboardingCompleted = true, sheetUrl = null)) }

            assertEquals(before, repo.snapshot.value)
            assertEquals(1, repo.getThemeMode())
        }
    }

    @Test
    fun clearingAllDataForgetsTheSheetLinkAndSyncStateAndShowsTheTutorialAgain() = runTest {
        test { repo, handle ->
            repo.restoreFromSnapshot(backup())
            repo.setOnboardingCompleted(true)
            repo.setThemeMode(2)
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/mine/edit")
            repo.setSheetLastSync(9)
            repo.setSheetSyncedKeys(setOf("k"))

            repo.clearAllData()

            assertEquals(HabitSnapshot(), repo.snapshot.value)
            assertEquals("", repo.getSheetUrl())
            assertEquals(0, repo.getSheetLastSync())
            assertTrue(repo.getSheetSyncedKeys().isEmpty())
            assertEquals(false, repo.isOnboardingCompleted())
            assertEquals(2, repo.getThemeMode(), "theme is a device preference and is kept")
            assertEquals(HabitSnapshot(), handle.reopen().snapshot.value)
        }
    }

    @Test
    fun backupSettingsCarryTheLinkOnlyWhenAsked() = runTest {
        test { repo, _ ->
            repo.setThemeMode(1)
            repo.setOnboardingCompleted(true)
            assertNull(repo.backupSettings(includeSheetLink = true).sheetUrl, "no link saved yet")
            repo.setSheetUrl("https://docs.google.com/spreadsheets/d/mine/edit")
            assertEquals("https://docs.google.com/spreadsheets/d/mine/edit", repo.backupSettings(includeSheetLink = true).sheetUrl)
            val without = repo.backupSettings(includeSheetLink = false)
            assertNull(without.sheetUrl)
            assertEquals(1, without.themeMode)
            assertEquals(true, without.onboardingCompleted)
        }
    }

    @Test
    fun refreshChangesNothingWhenNoOtherWriterExists() = runTest {
        test { repo, _ ->
            repo.restoreFromSnapshot(backup())
            val before = repo.snapshot.value
            repo.refresh()
            assertEquals(before, repo.snapshot.value)
        }
    }
}
