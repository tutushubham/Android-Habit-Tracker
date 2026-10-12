package com.habitsheet.domain.plan

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MAX_DAILY_HABITS
import kotlinx.datetime.LocalDate

/** The rows a plan adds: written in one transaction by `HabitStore.applyPlan`. Ids are fixed, so applying twice is a no-op. */
data class PlanApplication(
    val categories: List<Category> = emptyList(),
    val habits: List<DailyHabit> = emptyList(),
    val dayPlans: List<DayPlan> = emptyList(),
) {
    val isEmpty: Boolean get() = categories.isEmpty() && habits.isEmpty() && dayPlans.isEmpty()
}

/** The person's wording for plan labels, and the name given to a plan habit that would clash with a sheet-managed one. */
interface PlanWording {
    fun text(label: PlanLabel): String

    fun copyName(name: String): String
}

/** When the plan is applied: new habits start [today]; every row is stamped [nowEpochMillis]. */
data class PlanTime(val today: LocalDate, val nowEpochMillis: Long)

sealed interface PlanMergeResult {
    /** [reusedHabits] already existed by name and only receive sessions. */
    data class Ok(val application: PlanApplication, val reusedHabits: Int) : PlanMergeResult

    /** The plan would take the person past [MAX_DAILY_HABITS] active daily habits. */
    data class TooManyHabits(val limit: Int, val wouldHave: Int) : PlanMergeResult
}

/**
 * Turns a draft into rows that fit the person's existing data. Pure: wording comes in through [wording], ids through
 * [newId] (called once per new row with a stable seed), time through [PlanTime].
 *
 * - A plan habit whose name matches an existing habit (case-insensitive) reuses it, unless that habit is managed by
 *   the Google Sheet: then the plan gets its own habit named by [PlanWording.copyName], because a sheet-managed habit follows the sheet.
 * - Categories map to the default ones when the person still has them, else to a category with the same name, else a
 *   new one is created.
 * - A session never replaces a plan row the person already has for that habit and day.
 * - Only enabled habits and their sessions are added.
 */
object PlanMerger {
    fun merge(
        snapshot: HabitSnapshot,
        draft: PlanDraft,
        wording: PlanWording,
        newId: (seed: String) -> String,
        time: PlanTime,
    ): PlanMergeResult {
        val today = time.today
        val now = time.nowEpochMillis
        val text = wording::text
        val categories = CategoryResolver(snapshot, text, newId, now)
        val active = snapshot.dailyHabits.filter { it.active && it.archivedOn == null }
        var nextOrder = (snapshot.dailyHabits.maxOfOrNull { it.displayOrder } ?: -1) + 1

        val habitIds = mutableMapOf<String, String>()
        val newHabits = mutableListOf<DailyHabit>()
        var reused = 0
        for (draftHabit in draft.enabledHabits) {
            val (name, existingId) = matchExisting(text(draftHabit.label).trim(), active, snapshot.sheetManagedHabitIds, wording)
            if (existingId != null) {
                habitIds[draftHabit.key] = existingId
                reused++
                continue
            }
            val id = newId("habit-${draftHabit.key}")
            habitIds[draftHabit.key] = id
            newHabits += DailyHabit(
                id = id,
                name = name,
                categoryId = categories.idFor(draftHabit.category),
                monthlyGoal = draftHabit.monthlyGoal,
                displayOrder = nextOrder++,
                active = true,
                createdOn = today,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                kind = draftHabit.kind,
                datedOnly = draftHabit.datedOnly,
            )
        }
        val wouldHave = active.size + newHabits.size
        if (newHabits.isNotEmpty() && wouldHave > MAX_DAILY_HABITS) return PlanMergeResult.TooManyHabits(MAX_DAILY_HABITS, wouldHave)

        val existingPlanIds = snapshot.dayPlans.mapTo(HashSet()) { it.id }
        val dayPlans = draft.sessions.mapNotNull { session ->
            val habitId = habitIds[session.habitKey] ?: return@mapNotNull null
            DayPlan(habitId, session.date, text(session.label).trim(), skipped = false, updatedAtEpochMillis = now)
                .takeUnless { it.id in existingPlanIds }
        }.distinctBy { it.id }
        val usedCategories = newHabits.mapNotNullTo(HashSet()) { it.categoryId }
        return PlanMergeResult.Ok(
            PlanApplication(categories.created.filter { it.id in usedCategories }, newHabits, dayPlans),
            reusedHabits = reused,
        )
    }

    /** Every label [merge] may ask [PlanWording.text] for with this [draft], so a caller can resolve them up front. */
    fun labelsNeeded(draft: PlanDraft): Set<PlanLabel> =
        (draft.enabledHabits.map { it.label } + draft.sessions.map { it.label } + CategoryResolver.names.values.map { PlanLabel(it) }).toSet()

    private fun String.normalized(): String = trim().lowercase()

    /** The name the plan habit gets and the id of an existing habit it reuses, if any (see the rules above). */
    private fun matchExisting(
        name: String,
        active: List<DailyHabit>,
        sheetManaged: Set<String>,
        wording: PlanWording,
    ): Pair<String, String?> {
        val existing = active.firstOrNull { it.name.normalized() == name.normalized() } ?: return name to null
        if (existing.id !in sheetManaged) return name to existing.id
        val copy = wording.copyName(name)
        return copy to active.firstOrNull { it.name.normalized() == copy.normalized() && it.id !in sheetManaged }?.id
    }

    private class CategoryResolver(
        private val snapshot: HabitSnapshot,
        private val text: (PlanLabel) -> String,
        private val newId: (String) -> String,
        private val now: Long,
    ) {
        val created = mutableListOf<Category>()
        private var nextOrder = (snapshot.categories.maxOfOrNull { it.displayOrder } ?: -1) + 1

        fun idFor(category: PlanCategory): String {
            val active = snapshot.categories.filter { it.active } + created
            active.firstOrNull { it.id == defaultIds.getValue(category) }?.let { return it.id }
            val name = text(PlanLabel(names.getValue(category))).trim()
            active.firstOrNull { it.name.normalized() == name.normalized() }?.let { return it.id }
            return Category(newId("category-${category.name.lowercase()}"), name, nextOrder++, active = true, updatedAtEpochMillis = now)
                .also { created += it }.id
        }

        companion object {
            /** The ids `DefaultData` gives the default categories (Career is 1, Fitness 4, Health 5, Sleep 8, Study 10). */
            val defaultIds = mapOf(
                PlanCategory.CAREER to "category-1",
                PlanCategory.FITNESS to "category-4",
                PlanCategory.HEALTH to "category-5",
                PlanCategory.SLEEP to "category-8",
                PlanCategory.STUDY to "category-10",
            )
            val names = mapOf(
                PlanCategory.CAREER to PlanText.CATEGORY_CAREER,
                PlanCategory.FITNESS to PlanText.CATEGORY_FITNESS,
                PlanCategory.HEALTH to PlanText.CATEGORY_HEALTH,
                PlanCategory.SLEEP to PlanText.CATEGORY_SLEEP,
                PlanCategory.STUDY to PlanText.CATEGORY_STUDY,
            )
        }
    }
}
