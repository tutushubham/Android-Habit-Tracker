package com.habitsheet.sync

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot

/** A Done checkbox to write back to the sheet: [column] is `E` (6-column layout) or `F` (8-column layout). */
internal data class DoneUpload(val sheetRow: Int, val column: String, val done: Boolean)

/** Everything a first upload to a brand-new `Plan` tab needs to know. */
internal data class InitialUpload(val rows: List<SheetPlanRow>, val syncedKeys: Set<String>, val managedHabitIds: Set<String>)

/** What a sync must change locally ([planIdsToDelete] .. [completionsToSave]) and remotely ([doneUploads]). */
internal data class ReconcileResult(
    val planIdsToDelete: List<String>,
    val habitsToCreate: List<DailyHabit>,
    val plansToSave: List<DayPlan>,
    val completionsToSave: List<DailyHabitCompletion>,
    val doneUploads: List<DoneUpload>,
    val managedHabitIds: Set<String>,
    val syncedKeys: Set<String>,
    val sessionCount: Int,
)

/**
 * Pure decision logic of a sync: no IO, no clock, no id generation of its own. The sheet is authoritative
 * except for local Done checks newer than the last sync, which are uploaded instead.
 */
internal object PlanReconciler {
    /** Rows for a brand-new tab: every planned local session in [window]. */
    fun initialUpload(snapshot: HabitSnapshot, window: SheetSyncWindow): InitialUpload {
        val rows = planRowsFor(snapshot, window)
        return InitialUpload(
            rows = rows,
            syncedKeys = rows.map(::rowKey).toSet(),
            managedHabitIds = snapshot.sheetManagedHabitIds + rows.mapNotNull { row ->
                snapshot.dailyHabits.firstOrNull { it.name.equals(row.habit, ignoreCase = true) }?.id
            },
        )
    }

    /** Rows of local habits that the sheet has never heard of (not sheet-managed, name absent from [remoteRows]). */
    fun localOnlyRows(snapshot: HabitSnapshot, remoteRows: List<SheetPlanRow>, window: SheetSyncWindow): List<SheetPlanRow> {
        val remoteNames = remoteRows.map { it.habit.trim().lowercase() }.toSet()
        val localOnlyIds = snapshot.dailyHabits
            .filter { it.id !in snapshot.sheetManagedHabitIds && it.name.trim().lowercase() !in remoteNames }
            .map { it.id }.toSet()
        return planRowsFor(snapshot, window).filter { row ->
            snapshot.dailyHabits.any { it.id in localOnlyIds && it.name.equals(row.habit, ignoreCase = true) }
        }
    }

    /**
     * @param oldKeys row keys imported by the previous sync; only these may be deleted locally when the row vanishes
     * @param lastSync epoch millis of the previous successful sync (0 = never, sheet wins everywhere)
     * @throws IllegalArgumentException when two local habits share a name
     */
    fun reconcile(
        snapshot: HabitSnapshot,
        remoteRows: List<SheetPlanRow>,
        oldKeys: Set<String>,
        lastSync: Long,
        nowMillis: Long,
        newId: () -> String,
    ): ReconcileResult {
        require(snapshot.dailyHabits.groupBy { it.name.trim().lowercase() }.values.all { it.size == 1 }) {
            "Two app habits have the same name. Rename one before syncing."
        }
        val habitsByName = snapshot.dailyHabits.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val keys = remoteRows.map(::rowKey).toSet()

        // A deleted sheet row deletes only a row previously imported from this sheet.
        val deletions = mutableListOf<String>()
        for (key in oldKeys - keys) {
            val old = snapshot.dayPlans.firstOrNull { plan ->
                plan.id == key || snapshot.dailyHabits.firstOrNull { it.id == plan.habitId }
                    ?.let { rowKey(it.name, plan.date) == key } == true
            } ?: continue
            deletions += old.id
        }

        val created = mutableListOf<DailyHabit>()
        val plans = mutableListOf<DayPlan>()
        val completions = mutableListOf<DailyHabitCompletion>()
        val uploads = mutableListOf<DoneUpload>()
        for (row in remoteRows) {
            val habitKey = row.habit.trim().lowercase()
            val habit = habitsByName[habitKey] ?: DailyHabit(
                id = newId(),
                name = row.habit,
                categoryId = categoryIdFor(row, snapshot.categories),
                monthlyGoal = 0,
                displayOrder = habitsByName.size,
                active = true,
                createdOn = row.date,
                createdAtEpochMillis = nowMillis,
                updatedAtEpochMillis = nowMillis,
                kind = if (row.habit.startsWith("No ", ignoreCase = true) || row.area == "Avoidance") HabitKind.AVOIDANCE else HabitKind.ACTION,
                datedOnly = true,
            ).also {
                created += it
                habitsByName[habitKey] = it
            }
            val currentPlan = snapshot.dayPlans.firstOrNull { it.id == row.id && it.id !in deletions }
            if (currentPlan?.habitId != habit.id || currentPlan.date != row.date || currentPlan.detail != row.session || currentPlan.skipped != row.skip) {
                plans += DayPlan(habit.id, row.date, row.session, row.skip, nowMillis, row.id)
            }
            val localDone = snapshot.dailyCompletions.firstOrNull { it.planId == row.id && it.date == row.date }
            if (lastSync > 0 && localDone != null && localDone.updatedAtEpochMillis > lastSync && localDone.completed != row.done) {
                uploads += DoneUpload(row.sheetRow, row.doneColumn, localDone.completed)
            } else if (localDone?.completed != row.done) {
                completions += DailyHabitCompletion(habit.id, row.date, row.done, nowMillis, row.id)
            }
        }

        val managedNames = remoteRows.map { it.habit.trim().lowercase() }.toSet()
        return ReconcileResult(
            planIdsToDelete = deletions,
            habitsToCreate = created,
            plansToSave = plans,
            completionsToSave = completions,
            doneUploads = uploads,
            managedHabitIds = snapshot.sheetManagedHabitIds +
                (snapshot.dailyHabits + created).filter { it.name.trim().lowercase() in managedNames }.map { it.id },
            syncedKeys = keys,
            sessionCount = remoteRows.size,
        )
    }
}

/** Maps the sheet Area column to an existing category by name (ignoring a trailing emoji), else null. */
private fun categoryIdFor(row: SheetPlanRow, categories: List<Category>): String? {
    val area = row.area?.trim().orEmpty()
    if (area.isEmpty()) return null
    return categories.firstOrNull { it.name.trim().equals(area, ignoreCase = true) }?.id
        ?: categories.firstOrNull { it.name.substringBefore(' ').equals(area, ignoreCase = true) }?.id
}
