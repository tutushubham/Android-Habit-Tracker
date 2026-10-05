package com.habitsheet.data

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.domain.model.monotonicUpdatedAt
import com.habitsheet.domain.repository.BackupStore
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.domain.repository.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * The SQLite repository. Habit data (this class), settings and sync state ([SettingsStoreImpl]) and reset/restore
 * ([BackupStoreImpl]) share one [LocalDatabase]: one lock, one transaction helper, one snapshot.
 *
 * Every write reloads the snapshot, except a single check-off, which patches it (a tap stays fast however long the
 * history is). Changes made by another instance on the same file (the Android widget) show after [refresh].
 */
class LocalHabitRepository private constructor(
    private val db: LocalDatabase,
    settings: SettingsStoreImpl = SettingsStoreImpl(db),
) : HabitRepository, SettingsStore by settings, BackupStore by BackupStoreImpl(db, settings) {

    constructor(driverFactory: DriverFactory) : this(LocalDatabase.open(driverFactory))

    override val snapshot: StateFlow<HabitSnapshot> get() = db.snapshot

    override suspend fun refresh() = db.refresh()

    override suspend fun saveCategory(category: Category) = db.write {
        insert(category)
        update(category)
    }

    override suspend fun deleteCategory(id: String) = db.write {
        clearCategoryFromDailyHabits(id)
        clearCategoryFromWeeklyHabits(id)
        deleteCategory(id)
    }

    override suspend fun saveDailyHabit(habit: DailyHabit) {
        require(habit.monthlyGoal >= 0)
        db.write { write(habit) }
    }

    override suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) = db.write {
        archiveDailyHabit(archived_on = archivedOn.toString(), updated_at = updatedAtEpochMillis, id = id)
    }

    override suspend fun restoreDailyHabit(id: String, updatedAtEpochMillis: Long) = db.write {
        restoreDailyHabit(updated_at = updatedAtEpochMillis, id = id)
    }

    override suspend fun deleteDailyHabit(id: String) = db.write {
        deleteDailyCompletionsForHabit(id)
        deleteDayPlansForHabit(id)
        deleteWeeklyPlansForHabit(id)
        deleteDailyHabit(id)
    }

    override suspend fun updateDailyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) = db.write {
        orders.forEach { (id, order) ->
            updateDailyHabitOrder(display_order = order.toLong(), updated_at = updatedAtEpochMillis, id = id)
        }
    }

    override suspend fun saveWeeklyHabit(habit: WeeklyHabit) = db.write {
        insert(habit)
        update(habit)
    }

    override suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) = db.write {
        archiveWeeklyHabit(archived_on = archivedOn.toString(), updated_at = updatedAtEpochMillis, id = id)
    }

    override suspend fun restoreWeeklyHabit(id: String, updatedAtEpochMillis: Long) = db.write {
        restoreWeeklyHabit(updated_at = updatedAtEpochMillis, id = id)
    }

    override suspend fun deleteWeeklyHabit(id: String) = db.write {
        deleteWeeklyCompletionsForHabit(id)
        deleteWeeklyHabit(id)
    }

    override suspend fun updateWeeklyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) = db.write {
        orders.forEach { (id, order) ->
            updateWeeklyHabitOrder(display_order = order.toLong(), updated_at = updatedAtEpochMillis, id = id)
        }
    }

    override suspend fun setDailyCompletion(completion: DailyHabitCompletion) = db.writeAndPatch(
        block = {
            // A user/widget write: it still has to reach the sheet.
            val stored = completion.copy(
                updatedAtEpochMillis = nextCompletionTime(completion.planId, completion.date.toString(), completion.updatedAtEpochMillis),
            )
            upsertDailyCompletion(
                plan_id = stored.planId,
                habit_id = stored.habitId,
                date = stored.date.toString(),
                completed = stored.completed.toDbLong(),
                updated_at = stored.updatedAtEpochMillis,
                pending_upload = 1L,
            )
            stored
        },
        patch = { stored ->
            copy(
                dailyCompletions = dailyCompletions.replaceSorted(stored, DAILY_COMPLETION_ORDER) {
                    it.planId == stored.planId && it.date == stored.date
                },
                pendingCompletions = pendingCompletions + CompletionKey(stored.planId, stored.date),
            )
        },
    )

    override suspend fun saveWeeklyPlan(plan: WeeklyPlan) {
        require(plan.weekday in 1..7 && plan.detail.isNotBlank())
        db.write {
            upsertWeeklyPlan(
                plan.habitId, plan.weekday.toLong(), plan.detail,
                monotonicUpdatedAt(
                    plan.updatedAtEpochMillis,
                    selectWeeklyPlanUpdatedAt(plan.habitId, plan.weekday.toLong()).executeAsOneOrNull(),
                ),
            )
        }
    }

    override suspend fun deleteWeeklyPlan(habitId: String, weekday: Int) = db.write {
        deleteWeeklyPlan(habitId, weekday.toLong())
    }

    override suspend fun saveDayPlan(plan: DayPlan) {
        require(plan.detail.isNotBlank())
        db.write {
            upsertDayPlan(
                plan.id, plan.habitId, plan.date.toString(), plan.detail, plan.skipped.toDbLong(),
                nextDayPlanTime(plan.id, plan.updatedAtEpochMillis),
            )
        }
    }

    override suspend fun deleteDayPlan(habitId: String, date: LocalDate) = db.write {
        deleteDayPlan(habitId, date.toString())
    }

    override suspend fun deleteDayPlanById(id: String) = db.write {
        deleteDayPlanById(id)
    }

    override suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion) = db.writeAndPatch(
        block = {
            val stored = completion.copy(
                updatedAtEpochMillis = monotonicUpdatedAt(
                    completion.updatedAtEpochMillis,
                    selectWeeklyCompletionUpdatedAt(completion.weeklyHabitId, completion.weekStartDate.toString()).executeAsOneOrNull(),
                    strict = true,
                ),
            )
            upsertWeeklyCompletion(
                weekly_habit_id = stored.weeklyHabitId,
                week_start_date = stored.weekStartDate.toString(),
                completed = stored.completed.toDbLong(),
                updated_at = stored.updatedAtEpochMillis,
            )
            stored
        },
        patch = { stored ->
            copy(
                weeklyCompletions = weeklyCompletions.replaceSorted(stored, WEEKLY_COMPLETION_ORDER) {
                    it.weeklyHabitId == stored.weeklyHabitId && it.weekStartDate == stored.weekStartDate
                },
            )
        },
    )

    fun close() {
        db.close()
    }
}
