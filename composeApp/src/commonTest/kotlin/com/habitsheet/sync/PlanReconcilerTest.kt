package com.habitsheet.sync

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionAck
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure tests: no HTTP, no repository, no clock. */
class PlanReconcilerTest {
    private val day = LocalDate(2026, 10, 1)
    private val tomorrow = LocalDate(2026, 10, 2)
    private val now = 1_000L

    private fun habit(id: String, name: String) = DailyHabit(id, name, null, 12, 0, true, day, null, 1, 1, datedOnly = true)
    private fun plan(habitId: String, date: LocalDate, detail: String, id: String, skipped: Boolean = false) =
        DayPlan(habitId, date, detail, skipped, 1, id)
    private fun row(id: String, date: LocalDate, habit: String, session: String, done: Boolean = false, skip: Boolean = false, sheetRow: Int = 2, area: String? = null, doneColumn: String = "E") =
        SheetPlanRow(id, date, habit, session, done, skip, sheetRow, area, doneColumn)

    private var ids = 0
    private fun reconcile(
        snapshot: HabitSnapshot,
        rows: List<SheetPlanRow>,
        oldKeys: Set<String> = emptySet(),
    ) = PlanReconciler.reconcile(snapshot, rows, oldKeys, now) { "new-${++ids}" }

    private val runSnapshot = HabitSnapshot(
        dailyHabits = listOf(habit("run", "Run")),
        dayPlans = listOf(plan("run", day, "Easy", "run-1")),
    )

    @Test
    fun unchangedSheetProducesNoWritesAndStillMarksHabitManaged() {
        val result = reconcile(
            runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, false, 1, "run-1"))),
            listOf(row("run-1", day, "Run", "Easy")),
        )
        assertTrue(result.changes.plansToSave.isEmpty() && result.changes.completionsToSave.isEmpty() && result.doneUploads.isEmpty())
        assertTrue(result.changes.habitsToSave.isEmpty() && result.changes.planIdsToDelete.isEmpty())
        assertEquals(setOf("run"), result.changes.managedHabitIds)
        assertEquals(setOf("run-1"), result.syncedKeys)
        assertEquals(1, result.sessionCount)
    }

    @Test
    fun changedSessionOrSkipOrDateSavesPlanFromSheet() {
        val result = reconcile(runSnapshot, listOf(row("run-1", tomorrow, "Run", "Tempo", skip = true)))
        assertEquals(listOf(DayPlan("run", tomorrow, "Tempo", true, now, "run-1")), result.changes.plansToSave)
    }

    @Test
    fun unknownHabitIsCreatedDatedOnlyWithNextDisplayOrderAndInjectedId() {
        val categories = listOf(Category("fit", "Fitness 🏃", 0, true, 1))
        val result = reconcile(
            runSnapshot.copy(categories = categories),
            listOf(
                row("run-1", day, "Run", "Easy"),
                row("y-1", tomorrow, "Yoga", "Flow", area = "Fitness", sheetRow = 3),
                row("y-2", tomorrow, "yoga", "Again", sheetRow = 4),
            ),
        )
        val yoga = result.changes.habitsToSave.single()
        assertEquals("new-1", yoga.id)
        assertEquals("Yoga", yoga.name)
        assertEquals(1, yoga.displayOrder)
        assertEquals("fit", yoga.categoryId)
        assertTrue(yoga.datedOnly)
        assertEquals(HabitKind.ACTION, yoga.kind)
        assertEquals(tomorrow, yoga.createdOn)
        assertEquals(now, yoga.createdAtEpochMillis)
        assertEquals(listOf("y-1", "y-2"), result.changes.plansToSave.map { it.id })
        assertTrue(result.changes.plansToSave.all { it.habitId == "new-1" })
        assertEquals(setOf("run", "new-1"), result.changes.managedHabitIds)
    }

    @Test
    fun avoidanceKindFromNoPrefixOrArea() {
        val result = reconcile(
            HabitSnapshot(),
            listOf(row("a", day, "No sugar", "x"), row("b", day, "Phone", "x", area = "Avoidance")),
        )
        assertEquals(listOf(HabitKind.AVOIDANCE, HabitKind.AVOIDANCE), result.changes.habitsToSave.map { it.kind })
    }

    @Test
    fun habitMatchingIsCaseAndWhitespaceInsensitive() {
        val result = reconcile(runSnapshot, listOf(row("run-1", day, "  RUN ", "Easy")))
        assertTrue(result.changes.habitsToSave.isEmpty())
        assertEquals(setOf("run"), result.changes.managedHabitIds)
    }

    @Test
    fun duplicateLocalNamesNeverThrowKnownIdsSyncAndUnknownIdsAreSkippedWithWarning() {
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(habit("a", "Run"), habit("b", " run")),
            dayPlans = listOf(plan("b", day, "Easy", "run-1")),
        )
        val result = reconcile(snapshot, listOf(
            row("run-1", day, "Run", "Edited"),
            row("run-2", tomorrow, "Run", "Mystery", sheetRow = 3),
            row("run-3", tomorrow, "RUN", "Mystery 2", sheetRow = 4),
        ))
        assertEquals(listOf(DayPlan("b", day, "Edited", false, now, "run-1")), result.changes.plansToSave)
        assertEquals(listOf(SyncWarning.AmbiguousHabitName("Run", 1), SyncWarning.AmbiguousHabitName("RUN", 1)), result.warnings)
        assertEquals(1, result.sessionCount)
        assertEquals(setOf("run-1", "run-2", "run-3"), result.syncedKeys)
        assertTrue(result.changes.habitsToSave.isEmpty())
    }

    @Test
    fun renamingAllRowsOfAHabitInTheSheetRenamesTheLocalHabit() {
        val snapshot = runSnapshot.copy(dayPlans = runSnapshot.dayPlans + plan("run", tomorrow, "Long", "run-2"))
        val result = reconcile(snapshot, listOf(
            row("run-1", day, "Jog", "Easy"),
            row("run-2", tomorrow, "jog", "Long", sheetRow = 3),
        ))
        assertEquals(listOf(HabitRename("run", "Run", "Jog")), result.renames)
        assertEquals(listOf("run"), result.changes.habitsToSave.map { it.id })
        assertEquals("Jog", result.changes.habitsToSave.single().name)
        assertTrue(result.changes.plansToSave.isEmpty(), "plans keep their habit")
        assertEquals(setOf("run"), result.changes.managedHabitIds)
    }

    @Test
    fun renameIsBlockedWhenTheNewNameBelongsToAnotherHabit() {
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run"), habit("jog", "Jog")),
            dayPlans = listOf(plan("run", day, "Easy", "run-1")),
        )
        val result = reconcile(snapshot, listOf(row("run-1", day, "Jog", "Easy")))
        assertTrue(result.renames.isEmpty())
        // Treated as a move of this session to the existing habit "Jog".
        assertEquals(listOf(DayPlan("jog", day, "Easy", false, now, "run-1")), result.changes.plansToSave)
    }

    @Test
    fun retypingOneRowOfSeveralIsAMoveNotARename() {
        val snapshot = runSnapshot.copy(dayPlans = runSnapshot.dayPlans + plan("run", tomorrow, "Long", "run-2"))
        val result = reconcile(snapshot, listOf(
            row("run-1", day, "Run", "Easy"),
            row("run-2", tomorrow, "Jog", "Long", sheetRow = 3),
        ))
        assertTrue(result.renames.isEmpty())
        val jog = result.changes.habitsToSave.single()
        assertEquals("Jog", jog.name)
        assertEquals(listOf(DayPlan(jog.id, tomorrow, "Long", false, now, "run-2")), result.changes.plansToSave)
    }

    @Test
    fun emptiedPlanTabKeepsLocalPlansWarnsAndRemembersOldKeys() {
        val result = reconcile(runSnapshot, emptyList(), oldKeys = setOf("run-1"))
        assertTrue(result.changes.planIdsToDelete.isEmpty())
        assertEquals(listOf<SyncWarning>(SyncWarning.PlanTabEmpty), result.warnings)
        assertEquals(setOf("run-1"), result.syncedKeys)
    }

    @Test
    fun emptyTabWithNoPreviousImportIsQuietlyEmpty() {
        val result = reconcile(runSnapshot, emptyList())
        assertTrue(result.warnings.isEmpty() && result.changes.planIdsToDelete.isEmpty())
    }

    private fun pending(snapshot: HabitSnapshot) =
        snapshot.copy(pendingCompletions = snapshot.dailyCompletions.map { CompletionKey(it.planId, it.date) }.toSet())

    @Test
    fun sheetValueWinsOverNonPendingLocalCheckEvenIfLocalIsNewer() {
        val completion = DailyHabitCompletion("run", day, true, 9_999, "run-1")
        val snapshot = runSnapshot.copy(dailyCompletions = listOf(completion))
        val result = reconcile(snapshot, listOf(row("run-1", day, "Run", "Easy", done = false)))
        assertEquals(listOf(DailyHabitCompletion("run", day, false, now, "run-1")), result.changes.completionsToSave)
        assertTrue(result.doneUploads.isEmpty() && result.changes.completionsToAcknowledge.isEmpty())
    }

    @Test
    fun pendingLocalCheckIsUploadedToTheRightColumnAndRowWithAnAck() {
        val snapshot = pending(runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 150, "run-1"))))
        val ack = CompletionAck("run-1", day, 150)
        val six = reconcile(snapshot, listOf(row("run-1", day, "Run", "Easy", sheetRow = 7)))
        assertEquals(listOf(DoneUpload(7, "E", true, ack)), six.doneUploads)
        assertEquals(listOf(ack), six.changes.completionsToAcknowledge)
        assertTrue(six.changes.completionsToSave.isEmpty())

        val eight = reconcile(snapshot, listOf(row("run-1", day, "Run", "Easy", sheetRow = 7, doneColumn = "F")))
        assertEquals(listOf(DoneUpload(7, "F", true, ack)), eight.doneUploads)
    }

    @Test
    fun pendingLocalCheckAlreadyMatchingTheSheetOnlyClearsItsFlag() {
        val snapshot = pending(runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 150, "run-1"))))
        val result = reconcile(snapshot, listOf(row("run-1", day, "Run", "Easy", done = true)))
        assertTrue(result.doneUploads.isEmpty() && result.changes.completionsToSave.isEmpty())
        assertEquals(listOf(CompletionAck("run-1", day, 150)), result.changes.completionsToAcknowledge)
    }

    @Test
    fun pendingFlagDoesNotDependOnAnyClock() {
        // An "old" updatedAt (1) that is pending still wins; a "new" one that is not pending does not.
        val oldButPending = pending(runSnapshot.copy(dailyCompletions = listOf(DailyHabitCompletion("run", day, true, 1, "run-1"))))
        assertEquals(1, reconcile(oldButPending, listOf(row("run-1", day, "Run", "Easy"))).doneUploads.size)
    }

    @Test
    fun rowWithNoLocalCompletionGetsOneWrittenEvenWhenNotDone() {
        // Pinned current behaviour: null != false, so a "not done" completion row is materialised.
        val result = reconcile(runSnapshot, listOf(row("run-1", day, "Run", "Easy")))
        assertEquals(listOf(DailyHabitCompletion("run", day, false, now, "run-1")), result.changes.completionsToSave)
    }

    @Test
    fun deletesOnlyPlansWhoseKeyWasSyncedBeforeAndIsNowMissing() {
        val snapshot = runSnapshot.copy(dayPlans = runSnapshot.dayPlans + plan("run", tomorrow, "Long", "run-2") + plan("run", tomorrow, "Local", "run-3"))
        val result = reconcile(snapshot, listOf(row("run-1", day, "Run", "Easy")), oldKeys = setOf("run-1", "run-2", "gone"))
        assertEquals(listOf("run-2"), result.changes.planIdsToDelete)
        assertEquals(setOf("run-1"), result.syncedKeys)
    }

    @Test
    fun legacyNameAndDateKeyAlsoMatchesPlanForDeletion() {
        val snapshot = runSnapshot
        val result = reconcile(snapshot, listOf(row("other-1", tomorrow, "Other", "x")), oldKeys = setOf("run|2026-10-01"))
        assertEquals(listOf("run-1"), result.changes.planIdsToDelete)
    }

    @Test
    fun plansDeletedThisRoundAreNotTreatedAsCurrentForRowsWithSameId() {
        val result = reconcile(runSnapshot, listOf(row("run-1", day, "Run", "Easy")), oldKeys = setOf("run|2026-10-01"))
        // Legacy key is stale, but run-1 is still in the sheet, so the plan is deleted and re-saved from the sheet.
        assertEquals(listOf("run-1"), result.changes.planIdsToDelete)
        assertEquals(1, result.changes.plansToSave.size)
    }

    @Test
    fun localOnlyRowsSkipManagedAndRemoteHabits() {
        val snapshot = HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run"), habit("journal", "Journal"), habit("read", "Read")),
            dayPlans = listOf(
                plan("run", day, "Easy", "run-1"),
                plan("journal", tomorrow, "Page", "j-1"),
                plan("read", tomorrow, "Book", "r-1"),
            ),
            sheetManagedHabitIds = setOf("read"),
        )
        val rows = PlanReconciler.localOnlyRows(snapshot, listOf(row("run-1", day, "RUN", "Easy")), SheetSyncWindow.rolling(day))
        assertEquals(listOf("j-1"), rows.map { it.id })
    }

    @Test
    fun initialUploadCoversAllPlannedRowsAndMarksTheirHabitsManaged() {
        val upload = PlanReconciler.initialUpload(runSnapshot, SheetSyncWindow.rolling(day))
        assertEquals(listOf("run-1"), upload.rows.map { it.id })
        assertEquals(setOf("run-1"), upload.syncedKeys)
        assertEquals(setOf("run"), upload.managedHabitIds)
        assertFalse(upload.rows.single().done)
    }
}
