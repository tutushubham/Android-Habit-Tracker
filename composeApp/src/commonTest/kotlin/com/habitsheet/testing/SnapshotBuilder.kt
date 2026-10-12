package com.habitsheet.testing

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.datetime.LocalDate

/**
 * Builds a [HabitSnapshot] without positional constructors:
 *
 * ```
 * val data = buildSnapshot {
 *     category("health", "Health")
 *     dailyHabit("run", "Run", category = "health", goal = 12)
 *     weeklyPlan("run", weekday = 1, "5 km")
 *     dayPlan("run", "2026-10-03", "10 km")
 *     done("run", "2026-10-01")
 * }
 * ```
 *
 * Dates are `LocalDate`s or ISO strings. Every row gets [stamp] as its `updatedAt` / `createdAt`, so tests that care
 * about ordering pass their own.
 */
fun buildSnapshot(stamp: Long = 0L, build: SnapshotBuilder.() -> Unit = {}): HabitSnapshot =
    SnapshotBuilder(stamp).apply(build).build()

// Every row field is a named, defaulted parameter on purpose: tests state only what they care about.
@Suppress("LongParameterList")
class SnapshotBuilder(private val stamp: Long = 0L) {
    private val categories = mutableListOf<Category>()
    private val dailyHabits = mutableListOf<DailyHabit>()
    private val dailyCompletions = mutableListOf<DailyHabitCompletion>()
    private val weeklyHabits = mutableListOf<WeeklyHabit>()
    private val weeklyCompletions = mutableListOf<WeeklyHabitCompletion>()
    private val weeklyPlans = mutableListOf<WeeklyPlan>()
    private val dayPlans = mutableListOf<DayPlan>()
    private val sheetManaged = mutableSetOf<String>()
    private val pending = mutableSetOf<CompletionKey>()

    fun category(id: String, name: String = id, order: Int = categories.size, active: Boolean = true) {
        categories += Category(id, name, order, active, stamp)
    }

    fun dailyHabit(
        id: String,
        name: String = id,
        category: String? = null,
        goal: Int = 10,
        order: Int = dailyHabits.size,
        active: Boolean = true,
        createdOn: LocalDate = DEFAULT_CREATED_ON,
        archivedOn: LocalDate? = null,
        kind: HabitKind = HabitKind.ACTION,
        datedOnly: Boolean = false,
    ) {
        dailyHabits += DailyHabit(
            id = id,
            name = name,
            categoryId = category,
            monthlyGoal = goal,
            displayOrder = order,
            active = active,
            createdOn = createdOn,
            archivedOn = archivedOn,
            createdAtEpochMillis = stamp,
            updatedAtEpochMillis = stamp,
            kind = kind,
            datedOnly = datedOnly,
        )
    }

    fun weeklyHabit(
        id: String,
        name: String = id,
        category: String? = null,
        order: Int = weeklyHabits.size,
        active: Boolean = true,
        createdOn: LocalDate = DEFAULT_CREATED_ON,
        archivedOn: LocalDate? = null,
    ) {
        weeklyHabits += WeeklyHabit(id, name, category, order, active, createdOn, archivedOn, stamp, stamp)
    }

    /** A recurring prescription; [weekday] is Monday = 1. */
    fun weeklyPlan(habit: String, weekday: Int, detail: String) {
        weeklyPlans += WeeklyPlan(habit, weekday, detail, stamp)
    }

    fun dayPlan(habit: String, date: LocalDate, detail: String = "", skipped: Boolean = false, id: String = "$habit|$date") {
        dayPlans += DayPlan(habit, date, detail, skipped, stamp, id)
    }

    fun dayPlan(habit: String, date: String, detail: String = "", skipped: Boolean = false, id: String? = null) {
        val day = LocalDate.parse(date)
        dayPlan(habit, day, detail, skipped, id ?: "$habit|$day")
    }

    /** A daily completion; [planId] defaults to the habit's single-session plan id `habit|date`. */
    fun done(habit: String, date: LocalDate, completed: Boolean = true, planId: String = "$habit|$date", pendingUpload: Boolean = false) {
        dailyCompletions += DailyHabitCompletion(habit, date, completed, stamp, planId)
        if (pendingUpload) pending += CompletionKey(planId, date)
    }

    fun done(habit: String, date: String, completed: Boolean = true, planId: String? = null, pendingUpload: Boolean = false) {
        val day = LocalDate.parse(date)
        done(habit, day, completed, planId ?: "$habit|$day", pendingUpload)
    }

    fun weeklyDone(habit: String, weekStart: LocalDate, completed: Boolean = true) {
        weeklyCompletions += WeeklyHabitCompletion(habit, weekStart, completed, stamp)
    }

    fun sheetManaged(vararg habitIds: String) {
        sheetManaged += habitIds
    }

    fun build() = HabitSnapshot(
        categories = categories.toList(),
        dailyHabits = dailyHabits.toList(),
        dailyCompletions = dailyCompletions.toList(),
        weeklyHabits = weeklyHabits.toList(),
        weeklyCompletions = weeklyCompletions.toList(),
        weeklyPlans = weeklyPlans.toList(),
        dayPlans = dayPlans.toList(),
        sheetManagedHabitIds = sheetManaged.toSet(),
        pendingCompletions = pending.toSet(),
    )

    private companion object {
        val DEFAULT_CREATED_ON = LocalDate(2020, 1, 1)
    }
}
