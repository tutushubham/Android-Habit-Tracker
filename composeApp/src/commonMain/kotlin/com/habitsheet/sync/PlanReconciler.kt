package com.habitsheet.sync

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges

/** A Done checkbox to write back to the sheet: [column] is `E` (6-column layout) or `F` (8-column layout). */
internal data class DoneUpload(val sheetRow: Int, val column: String, val done: Boolean)

/** Everything a first upload to a brand-new `Plan` tab needs to know. */
internal data class InitialUpload(val rows: List<SheetPlanRow>, val syncedKeys: Set<String>, val managedHabitIds: Set<String>)

/** A local habit renamed to the spelling used by the sheet (sheet wins). */
internal data class HabitRename(val habitId: String, val from: String, val to: String)

/** Things the user should know about; the sync itself still completed. */
internal sealed interface SyncWarning {
    /** [rows] unknown-ID sheet rows were skipped because [name] matches more than one local habit. */
    data class AmbiguousHabitName(val name: String, val rows: Int) : SyncWarning

    /** The Plan tab has no sessions although a previous sync imported some; local sessions were kept. */
    data object PlanTabEmpty : SyncWarning

    fun describe(): String = when (this) {
        is AmbiguousHabitName -> "$rows row${if (rows == 1) "" else "s"} skipped: \"$name\" matches more than one habit, rename one"
        PlanTabEmpty -> "Plan tab has no sessions; kept local sessions"
    }
}

/** What a sync must change locally ([changes]) and remotely ([doneUploads]). */
internal data class ReconcileResult(
    val changes: SheetSyncChanges,
    val doneUploads: List<DoneUpload>,
    val syncedKeys: Set<String>,
    val sessionCount: Int,
    val renames: List<HabitRename> = emptyList(),
    val warnings: List<SyncWarning> = emptyList(),
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

    /**
     * Rows of local habits that the sheet has never heard of: not sheet-managed, name absent from [remoteRows]
     * and none of their plans already present there by ID (a sheet-side rename must not look "local only").
     */
    fun localOnlyRows(snapshot: HabitSnapshot, remoteRows: List<SheetPlanRow>, window: SheetSyncWindow): List<SheetPlanRow> {
        val remoteNames = remoteRows.map { normalize(it.habit) }.toSet()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val habitsWithRemotePlans = snapshot.dayPlans.filter { it.id in remoteIds }.map { it.habitId }.toSet()
        val localOnlyIds = snapshot.dailyHabits
            .filter { it.id !in snapshot.sheetManagedHabitIds && normalize(it.name) !in remoteNames && it.id !in habitsWithRemotePlans }
            .map { it.id }.toSet()
        return planRowsFor(snapshot, window).filter { row ->
            row.id !in remoteIds && snapshot.dailyHabits.any { it.id in localOnlyIds && it.name.equals(row.habit, ignoreCase = true) }
        }
    }

    /**
     * Habit identity: a row whose ID is a known local plan belongs to that plan's habit; otherwise the
     * habit is found by normalised name, otherwise created. See docs/production-plan/notes/p0-b-identity.md.
     *
     * @param oldKeys row keys imported by the previous sync; only these may be deleted locally when the row vanishes
     * @param lastSync epoch millis of the previous successful sync (0 = never, sheet wins everywhere)
     */
    fun reconcile(
        snapshot: HabitSnapshot,
        remoteRows: List<SheetPlanRow>,
        oldKeys: Set<String>,
        lastSync: Long,
        nowMillis: Long,
        newId: () -> String,
    ): ReconcileResult {
        val warnings = mutableListOf<SyncWarning>()
        val keys = remoteRows.map(::rowKey).toSet()

        // A deleted sheet row deletes only a row previously imported from this sheet.
        // An empty tab after earlier imports is treated as "cleared by accident", not "delete everything".
        val deletions = mutableListOf<String>()
        val tabEmptied = remoteRows.isEmpty() && oldKeys.isNotEmpty()
        if (tabEmptied) {
            warnings += SyncWarning.PlanTabEmpty
        } else {
            for (key in oldKeys - keys) {
                val old = snapshot.dayPlans.firstOrNull { plan ->
                    plan.id == key || snapshot.dailyHabits.firstOrNull { it.id == plan.habitId }
                        ?.let { rowKey(it.name, plan.date) == key } == true
                } ?: continue
                deletions += old.id
            }
        }
        val deleted = deletions.toSet()
        val planOwner = snapshot.dayPlans.filter { it.id !in deleted }.associate { it.id to it.habitId }

        // Working copy of the habits, by id and by normalised name.
        val habitsById = snapshot.dailyHabits.associateBy { it.id }.toMutableMap()
        val byName = snapshot.dailyHabits.groupBy { normalize(it.name) }
            .mapValuesTo(mutableMapOf()) { (_, habits) -> habits.toMutableList() }
        val saved = linkedMapOf<String, DailyHabit>()
        val renames = mutableListOf<HabitRename>()

        // Rename: every sheet row of a habit's known plans agrees on one new name that no other habit uses.
        remoteRows.filter { it.id in planOwner }.groupBy { planOwner.getValue(it.id) }.forEach { (habitId, owned) ->
            val habit = habitsById[habitId] ?: return@forEach
            val target = owned.map { normalize(it.habit) }.toSet().singleOrNull() ?: return@forEach
            if (target == normalize(habit.name) || byName[target].orEmpty().isNotEmpty()) return@forEach
            val renamed = habit.copy(name = owned.first().habit, updatedAtEpochMillis = nowMillis)
            byName[normalize(habit.name)]?.remove(habit)
            byName.getOrPut(target) { mutableListOf() }.add(renamed)
            habitsById[habitId] = renamed
            saved[habitId] = renamed
            renames += HabitRename(habitId, habit.name, renamed.name)
        }

        val plans = mutableListOf<DayPlan>()
        val completions = mutableListOf<DailyHabitCompletion>()
        val uploads = mutableListOf<DoneUpload>()
        val skippedByName = linkedMapOf<String, Int>()
        for (row in remoteRows) {
            val key = normalize(row.habit)
            val candidates = byName[key].orEmpty()
            val owner = planOwner[row.id]?.let(habitsById::get)
            val habit = when {
                candidates.size == 1 -> candidates.single()
                candidates.size > 1 && owner != null && candidates.any { it.id == owner.id } -> owner
                candidates.size > 1 -> {
                    skippedByName.merge(row.habit, 1, Int::plus)
                    continue
                }
                else -> DailyHabit(
                    id = newId(),
                    name = row.habit,
                    categoryId = categoryIdFor(row, snapshot.categories),
                    monthlyGoal = 0,
                    displayOrder = habitsById.size,
                    active = true,
                    createdOn = row.date,
                    createdAtEpochMillis = nowMillis,
                    updatedAtEpochMillis = nowMillis,
                    kind = if (row.habit.startsWith("No ", ignoreCase = true) || row.area == "Avoidance") HabitKind.AVOIDANCE else HabitKind.ACTION,
                    datedOnly = true,
                ).also {
                    saved[it.id] = it
                    habitsById[it.id] = it
                    byName.getOrPut(key) { mutableListOf() }.add(it)
                }
            }
            val currentPlan = snapshot.dayPlans.firstOrNull { it.id == row.id && it.id !in deleted }
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
        skippedByName.forEach { (name, rows) -> warnings += SyncWarning.AmbiguousHabitName(name, rows) }

        val managedNames = remoteRows.map { normalize(it.habit) }.toSet()
        return ReconcileResult(
            changes = SheetSyncChanges(
                planIdsToDelete = deletions,
                habitsToSave = saved.values.toList(),
                plansToSave = plans,
                completionsToSave = completions,
                managedHabitIds = snapshot.sheetManagedHabitIds +
                    habitsById.values.filter { normalize(it.name) in managedNames }.map { it.id },
            ),
            doneUploads = uploads,
            syncedKeys = if (tabEmptied) oldKeys else keys,
            sessionCount = remoteRows.size - skippedByName.values.sum(),
            renames = renames,
            warnings = warnings,
        )
    }
}

private fun normalize(name: String) = name.trim().lowercase()

/** Maps the sheet Area column to an existing category by name (ignoring a trailing emoji), else null. */
private fun categoryIdFor(row: SheetPlanRow, categories: List<Category>): String? {
    val area = row.area?.trim().orEmpty()
    if (area.isEmpty()) return null
    return categories.firstOrNull { it.name.trim().equals(area, ignoreCase = true) }?.id
        ?: categories.firstOrNull { it.name.substringBefore(' ').equals(area, ignoreCase = true) }?.id
}
