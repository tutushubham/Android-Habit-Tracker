package com.habitsheet.data

import com.habitsheet.database.CategoryEntity
import com.habitsheet.database.DailyCompletionEntity
import com.habitsheet.database.DailyHabitEntity
import com.habitsheet.database.DayPlanEntity
import com.habitsheet.database.HabitsQueries
import com.habitsheet.database.WeeklyCompletionEntity
import com.habitsheet.database.WeeklyHabitEntity
import com.habitsheet.database.WeeklyPlanEntity
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.datetime.LocalDate

// SQL rows <-> domain models. Pure functions; callers hold the lock and the transaction.

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    displayOrder = display_order.toInt(),
    active = active != 0L,
    updatedAtEpochMillis = updated_at,
)

internal fun DailyHabitEntity.toDomain() = DailyHabit(
    id = id,
    name = name,
    categoryId = category_id,
    monthlyGoal = monthly_goal.toInt(),
    displayOrder = display_order.toInt(),
    active = active != 0L,
    createdOn = LocalDate.parse(created_on),
    archivedOn = archived_on?.let(LocalDate::parse),
    createdAtEpochMillis = created_at,
    updatedAtEpochMillis = updated_at,
    kind = HabitKind.entries.firstOrNull { it.name == kind } ?: HabitKind.ACTION,
    datedOnly = dated_only != 0L,
)

/** [parseDate] lets a bulk read share one parsed [LocalDate] per distinct date (thousands of rows, few dates). */
internal fun DailyCompletionEntity.toDomain(parseDate: (String) -> LocalDate = LocalDate::parse) = DailyHabitCompletion(
    habitId = habit_id,
    date = parseDate(date),
    completed = completed != 0L,
    updatedAtEpochMillis = updated_at,
    planId = plan_id,
)

internal fun WeeklyPlanEntity.toDomain() = WeeklyPlan(habit_id, weekday.toInt(), detail, updated_at)

internal fun DayPlanEntity.toDomain() = DayPlan(habit_id, LocalDate.parse(date), detail, skipped != 0L, updated_at, id)

internal fun WeeklyHabitEntity.toDomain() = WeeklyHabit(
    id = id,
    name = name,
    categoryId = category_id,
    displayOrder = display_order.toInt(),
    active = active != 0L,
    createdOn = LocalDate.parse(created_on),
    archivedOn = archived_on?.let(LocalDate::parse),
    createdAtEpochMillis = created_at,
    updatedAtEpochMillis = updated_at,
)

internal fun WeeklyCompletionEntity.toDomain(parseDate: (String) -> LocalDate = LocalDate::parse) = WeeklyHabitCompletion(
    weeklyHabitId = weekly_habit_id,
    weekStartDate = parseDate(week_start_date),
    completed = completed != 0L,
    updatedAtEpochMillis = updated_at,
)

/** Snapshot order of daily completions: date, habit, then plan (two sessions of one habit on one day). */
internal val DAILY_COMPLETION_ORDER: Comparator<DailyHabitCompletion> =
    compareBy<DailyHabitCompletion>({ it.date }, { it.habitId }, { it.planId })

/** Snapshot order of weekly completions: week start, then habit. */
internal val WEEKLY_COMPLETION_ORDER: Comparator<WeeklyHabitCompletion> =
    compareBy<WeeklyHabitCompletion>({ it.weekStartDate }, { it.weeklyHabitId })

/**
 * Replaces the element with the same key as [value] (or adds it) in a list sorted by [order], keeping it sorted.
 * Used to patch one check-off into the snapshot without re-reading the table.
 */
internal fun <T> List<T>.replaceSorted(value: T, order: Comparator<T>, sameKey: (T) -> Boolean): List<T> {
    val others = filterNot(sameKey)
    val found = others.binarySearch(value, order)
    val index = if (found < 0) -found - 1 else found
    return ArrayList<T>(others.size + 1).apply {
        addAll(others.subList(0, index))
        add(value)
        addAll(others.subList(index, others.size))
    }
}

/** Insert-only (`INSERT OR IGNORE`): an existing id keeps its row. */
internal fun HabitsQueries.insert(category: Category) = insertCategory(
    id = category.id,
    name = category.name,
    display_order = category.displayOrder.toLong(),
    active = category.active.toDbLong(),
    updated_at = category.updatedAtEpochMillis,
)

internal fun HabitsQueries.update(category: Category) = updateCategory(
    name = category.name,
    display_order = category.displayOrder.toLong(),
    active = category.active.toDbLong(),
    updated_at = category.updatedAtEpochMillis,
    id = category.id,
)

/** Insert-only (`INSERT OR IGNORE`): an existing id keeps its row. */
internal fun HabitsQueries.insert(habit: DailyHabit) = insertDailyHabit(
    id = habit.id,
    name = habit.name,
    category_id = habit.categoryId,
    monthly_goal = habit.monthlyGoal.toLong(),
    display_order = habit.displayOrder.toLong(),
    active = habit.active.toDbLong(),
    created_on = habit.createdOn.toString(),
    archived_on = habit.archivedOn?.toString(),
    created_at = habit.createdAtEpochMillis,
    updated_at = habit.updatedAtEpochMillis,
    kind = habit.kind.name,
    dated_only = habit.datedOnly.toDbLong(),
)

internal fun HabitsQueries.update(habit: DailyHabit) = updateDailyHabit(
    name = habit.name,
    category_id = habit.categoryId,
    monthly_goal = habit.monthlyGoal.toLong(),
    display_order = habit.displayOrder.toLong(),
    active = habit.active.toDbLong(),
    created_on = habit.createdOn.toString(),
    archived_on = habit.archivedOn?.toString(),
    updated_at = habit.updatedAtEpochMillis,
    kind = habit.kind.name,
    dated_only = habit.datedOnly.toDbLong(),
    id = habit.id,
)

/** Insert-or-update; must be called inside a transaction. */
internal fun HabitsQueries.write(habit: DailyHabit) {
    insert(habit)
    update(habit)
}

/** Insert-only (`INSERT OR IGNORE`): an existing id keeps its row. */
internal fun HabitsQueries.insert(habit: WeeklyHabit) = insertWeeklyHabit(
    id = habit.id,
    name = habit.name,
    category_id = habit.categoryId,
    display_order = habit.displayOrder.toLong(),
    active = habit.active.toDbLong(),
    created_on = habit.createdOn.toString(),
    archived_on = habit.archivedOn?.toString(),
    created_at = habit.createdAtEpochMillis,
    updated_at = habit.updatedAtEpochMillis,
)

internal fun HabitsQueries.update(habit: WeeklyHabit) = updateWeeklyHabit(
    name = habit.name,
    category_id = habit.categoryId,
    display_order = habit.displayOrder.toLong(),
    active = habit.active.toDbLong(),
    created_on = habit.createdOn.toString(),
    archived_on = habit.archivedOn?.toString(),
    updated_at = habit.updatedAtEpochMillis,
    id = habit.id,
)

internal fun Boolean.toDbLong(): Long = if (this) 1L else 0L
