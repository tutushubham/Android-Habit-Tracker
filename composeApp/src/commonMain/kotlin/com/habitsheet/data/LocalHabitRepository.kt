package com.habitsheet.data

import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupValidator
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class LocalHabitRepository(
    driverFactory: DriverFactory,
) : HabitRepository {
    private val driver = driverFactory.createDriver()
    private val database: HabitsDatabase
    private val mutex = Mutex()
    private val mutableSnapshot = MutableStateFlow(HabitSnapshot())
    override val snapshot: StateFlow<HabitSnapshot> = mutableSnapshot.asStateFlow()

    init {
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        database = HabitsDatabase(driver)
        seedDefaultsIfEmpty()
        loadSnapshot()
    }

    override suspend fun refresh() {
        mutex.withLock { loadSnapshot() }
    }

    override suspend fun saveCategory(category: Category) {
        mutex.withLock {
            database.transaction {
                database.habitsQueries.insertCategory(
                    id = category.id,
                    name = category.name,
                    display_order = category.displayOrder.toLong(),
                    active = category.active.toDbLong(),
                    updated_at = category.updatedAtEpochMillis,
                )
                database.habitsQueries.updateCategory(
                    name = category.name,
                    display_order = category.displayOrder.toLong(),
                    active = category.active.toDbLong(),
                    updated_at = category.updatedAtEpochMillis,
                    id = category.id,
                )
            }
            loadSnapshot()
        }
    }

    override suspend fun deleteCategory(id: String) {
        mutex.withLock {
            database.habitsQueries.deleteCategory(id)
            loadSnapshot()
        }
    }

    override suspend fun saveDailyHabit(habit: DailyHabit) {
        require(habit.monthlyGoal >= 0)
        mutex.withLock {
            database.transaction { writeDailyHabit(habit) }
            loadSnapshot()
        }
    }

    /** Insert-or-update; must be called inside a transaction. */
    private fun writeDailyHabit(habit: DailyHabit) {
        database.habitsQueries.insertDailyHabit(
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
        database.habitsQueries.updateDailyHabit(
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
    }

    override suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        mutex.withLock {
            database.habitsQueries.archiveDailyHabit(
                archived_on = archivedOn.toString(),
                updated_at = updatedAtEpochMillis,
                id = id,
            )
            loadSnapshot()
        }
    }

    override suspend fun restoreDailyHabit(id: String, updatedAtEpochMillis: Long) {
        mutex.withLock {
            database.habitsQueries.restoreDailyHabit(
                updated_at = updatedAtEpochMillis,
                id = id,
            )
            loadSnapshot()
        }
    }

    override suspend fun deleteDailyHabit(id: String) {
        mutex.withLock {
            database.habitsQueries.deleteDailyHabit(id)
            loadSnapshot()
        }
    }

    override suspend fun updateDailyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) {
        mutex.withLock {
            database.transaction {
                orders.forEach { (id, order) ->
                    database.habitsQueries.updateDailyHabitOrder(
                        display_order = order.toLong(),
                        updated_at = updatedAtEpochMillis,
                        id = id
                    )
                }
            }
            loadSnapshot()
        }
    }

    override suspend fun saveWeeklyHabit(habit: WeeklyHabit) {
        mutex.withLock {
            database.transaction {
                database.habitsQueries.insertWeeklyHabit(
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
                database.habitsQueries.updateWeeklyHabit(
                    name = habit.name,
                    category_id = habit.categoryId,
                    display_order = habit.displayOrder.toLong(),
                    active = habit.active.toDbLong(),
                    created_on = habit.createdOn.toString(),
                    archived_on = habit.archivedOn?.toString(),
                    updated_at = habit.updatedAtEpochMillis,
                    id = habit.id,
                )
            }
            loadSnapshot()
        }
    }

    override suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        mutex.withLock {
            database.habitsQueries.archiveWeeklyHabit(
                archived_on = archivedOn.toString(),
                updated_at = updatedAtEpochMillis,
                id = id,
            )
            loadSnapshot()
        }
    }

    override suspend fun restoreWeeklyHabit(id: String, updatedAtEpochMillis: Long) {
        mutex.withLock {
            database.habitsQueries.restoreWeeklyHabit(
                updated_at = updatedAtEpochMillis,
                id = id,
            )
            loadSnapshot()
        }
    }

    override suspend fun deleteWeeklyHabit(id: String) {
        mutex.withLock {
            database.habitsQueries.deleteWeeklyHabit(id)
            loadSnapshot()
        }
    }

    override suspend fun updateWeeklyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) {
        mutex.withLock {
            database.transaction {
                orders.forEach { (id, order) ->
                    database.habitsQueries.updateWeeklyHabitOrder(
                        display_order = order.toLong(),
                        updated_at = updatedAtEpochMillis,
                        id = id
                    )
                }
            }
            loadSnapshot()
        }
    }

    override suspend fun setDailyCompletion(completion: DailyHabitCompletion) {
        mutex.withLock {
            database.habitsQueries.upsertDailyCompletion(
                plan_id = completion.planId,
                habit_id = completion.habitId,
                date = completion.date.toString(),
                completed = completion.completed.toDbLong(),
                updated_at = completion.updatedAtEpochMillis,
            )
            loadSnapshot()
        }
    }

    override suspend fun saveWeeklyPlan(plan: WeeklyPlan) {
        require(plan.weekday in 1..7 && plan.detail.isNotBlank())
        mutex.withLock {
            database.habitsQueries.upsertWeeklyPlan(plan.habitId, plan.weekday.toLong(), plan.detail, plan.updatedAtEpochMillis)
            loadSnapshot()
        }
    }

    override suspend fun deleteWeeklyPlan(habitId: String, weekday: Int) {
        mutex.withLock {
            database.habitsQueries.deleteWeeklyPlan(habitId, weekday.toLong())
            loadSnapshot()
        }
    }

    override suspend fun saveDayPlan(plan: DayPlan) {
        require(plan.detail.isNotBlank())
        mutex.withLock {
            database.habitsQueries.upsertDayPlan(plan.id, plan.habitId, plan.date.toString(), plan.detail, plan.skipped.toDbLong(), plan.updatedAtEpochMillis)
            loadSnapshot()
        }
    }

    override suspend fun deleteDayPlan(habitId: String, date: LocalDate) {
        mutex.withLock {
            database.habitsQueries.deleteDayPlan(habitId, date.toString())
            loadSnapshot()
        }
    }

    override suspend fun deleteDayPlanById(id: String) {
        mutex.withLock {
            database.habitsQueries.deleteDayPlanById(id)
            loadSnapshot()
        }
    }

    override suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion) {
        mutex.withLock {
            database.habitsQueries.upsertWeeklyCompletion(
                weekly_habit_id = completion.weeklyHabitId,
                week_start_date = completion.weekStartDate.toString(),
                completed = completion.completed.toDbLong(),
                updated_at = completion.updatedAtEpochMillis,
            )
            loadSnapshot()
        }
    }

    override suspend fun isOnboardingCompleted(): Boolean {
        return mutex.withLock {
            database.habitsQueries.getSetting("onboarding_completed").executeAsOneOrNull() == 1L
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        mutex.withLock {
            database.habitsQueries.setSetting("onboarding_completed", if (completed) 1L else 0L)
        }
    }

    override suspend fun getThemeMode(): Int {
        return mutex.withLock {
            database.habitsQueries.getSetting("theme_mode").executeAsOneOrNull()?.toInt() ?: 0
        }
    }

    override suspend fun setThemeMode(mode: Int) {
        mutex.withLock {
            database.habitsQueries.setSetting("theme_mode", mode.toLong())
        }
    }

    override suspend fun getSheetUrl(): String = mutex.withLock {
        database.habitsQueries.getTextSetting("sheet_url").executeAsOneOrNull().orEmpty()
    }

    override suspend fun setSheetUrl(url: String) {
        mutex.withLock {
            if (database.habitsQueries.getTextSetting("sheet_url").executeAsOneOrNull() != url) {
                database.habitsQueries.setTextSetting("sheet_last_sync", "0")
                database.habitsQueries.setTextSetting("sheet_synced_keys", "")
                database.habitsQueries.setTextSetting("sheet_managed_habits", "")
            }
            database.habitsQueries.setTextSetting("sheet_url", url)
            loadSnapshot()
        }
    }

    override suspend fun getSheetLastSync(): Long = mutex.withLock {
        database.habitsQueries.getTextSetting("sheet_last_sync").executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    override suspend fun setSheetLastSync(epochMillis: Long) {
        mutex.withLock { database.habitsQueries.setTextSetting("sheet_last_sync", epochMillis.toString()) }
    }

    override suspend fun getSheetSyncedKeys(): Set<String> = mutex.withLock {
        database.habitsQueries.getTextSetting("sheet_synced_keys").executeAsOneOrNull()
            ?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }

    override suspend fun setSheetSyncedKeys(keys: Set<String>) {
        mutex.withLock { database.habitsQueries.setTextSetting("sheet_synced_keys", keys.sorted().joinToString("\n")) }
    }

    override suspend fun setSheetManagedHabitIds(ids: Set<String>) {
        mutex.withLock {
            database.habitsQueries.setTextSetting("sheet_managed_habits", ids.sorted().joinToString("\n"))
            loadSnapshot()
        }
    }

    override suspend fun applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long) {
        mutex.withLock {
            // Any exception rolls the whole transaction back; the cached snapshot is only reloaded on success.
            database.transaction {
                changes.planIdsToDelete.forEach { database.habitsQueries.deleteDayPlanById(it) }
                changes.habitsToSave.forEach { habit ->
                    require(habit.monthlyGoal >= 0)
                    writeDailyHabit(habit)
                }
                changes.plansToSave.forEach { plan ->
                    require(plan.detail.isNotBlank())
                    database.habitsQueries.upsertDayPlan(plan.id, plan.habitId, plan.date.toString(), plan.detail, plan.skipped.toDbLong(), plan.updatedAtEpochMillis)
                }
                changes.completionsToSave.forEach { completion ->
                    database.habitsQueries.upsertDailyCompletion(
                        plan_id = completion.planId,
                        habit_id = completion.habitId,
                        date = completion.date.toString(),
                        completed = completion.completed.toDbLong(),
                        updated_at = completion.updatedAtEpochMillis,
                    )
                }
                database.habitsQueries.setTextSetting("sheet_managed_habits", changes.managedHabitIds.sorted().joinToString("\n"))
                database.habitsQueries.setTextSetting("sheet_synced_keys", newKeys.sorted().joinToString("\n"))
                database.habitsQueries.setTextSetting("sheet_last_sync", lastSync.toString())
            }
            loadSnapshot()
        }
    }

    override suspend fun clearAllData() {
        mutex.withLock {
            database.transaction {
                database.habitsQueries.clearAllDailyCompletions()
                database.habitsQueries.clearAllWeeklyCompletions()
                database.habitsQueries.clearAllDayPlans()
                database.habitsQueries.clearAllWeeklyPlans()
                database.habitsQueries.clearAllData()
                database.habitsQueries.clearAllWeeklyHabits()
                database.habitsQueries.clearAllCategories()
                database.habitsQueries.setSetting("onboarding_completed", 0L)
                database.habitsQueries.setTextSetting("sheet_managed_habits", "")
            }
            loadSnapshot()
        }
    }

    override suspend fun restoreFromSnapshot(snapshot: HabitSnapshot) {
        // Validate before opening the replacement transaction so corrupt or inconsistent
        // backups can never clear the user's current data.
        BackupValidator.validate(snapshot)
        mutex.withLock {
            database.transaction {
                database.habitsQueries.clearAllDailyCompletions()
                database.habitsQueries.clearAllWeeklyCompletions()
                database.habitsQueries.clearAllDayPlans()
                database.habitsQueries.clearAllWeeklyPlans()
                database.habitsQueries.clearAllData()
                database.habitsQueries.clearAllWeeklyHabits()
                database.habitsQueries.clearAllCategories()
                database.habitsQueries.setTextSetting("sheet_managed_habits", "")
                
                snapshot.categories.forEach { category ->
                    database.habitsQueries.insertCategory(
                        id = category.id,
                        name = category.name,
                        display_order = category.displayOrder.toLong(),
                        active = category.active.toDbLong(),
                        updated_at = category.updatedAtEpochMillis,
                    )
                }
                
                snapshot.dailyHabits.forEach { habit ->
                    database.habitsQueries.insertDailyHabit(
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
                }
                
                snapshot.dailyCompletions.forEach { completion ->
                    database.habitsQueries.upsertDailyCompletion(
                        plan_id = completion.planId,
                        habit_id = completion.habitId,
                        date = completion.date.toString(),
                        completed = completion.completed.toDbLong(),
                        updated_at = completion.updatedAtEpochMillis,
                    )
                }
                snapshot.weeklyPlans.forEach { plan ->
                    database.habitsQueries.upsertWeeklyPlan(plan.habitId, plan.weekday.toLong(), plan.detail, plan.updatedAtEpochMillis)
                }
                snapshot.dayPlans.forEach { plan ->
                    database.habitsQueries.upsertDayPlan(plan.id, plan.habitId, plan.date.toString(), plan.detail, plan.skipped.toDbLong(), plan.updatedAtEpochMillis)
                }
                
                snapshot.weeklyHabits.forEach { habit ->
                    database.habitsQueries.insertWeeklyHabit(
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
                }
                
                snapshot.weeklyCompletions.forEach { completion ->
                    database.habitsQueries.upsertWeeklyCompletion(
                        weekly_habit_id = completion.weeklyHabitId,
                        week_start_date = completion.weekStartDate.toString(),
                        completed = completion.completed.toDbLong(),
                        updated_at = completion.updatedAtEpochMillis,
                    )
                }
            }
            loadSnapshot()
        }
    }

    fun close() {
        driver.close()
    }

    private fun seedDefaultsIfEmpty() {
        val defaultsAlreadySeeded = database.habitsQueries
            .getSetting("defaults_seeded")
            .executeAsOneOrNull() == 1L
        if (defaultsAlreadySeeded) return
        database.transaction {
            if (database.habitsQueries.selectCategoryCount().executeAsOne() == 0L) {
                val now = Clock.System.now().toEpochMilliseconds()
                DefaultData.categories(now).forEach { category ->
                    database.habitsQueries.insertCategory(
                        id = category.id,
                        name = category.name,
                        display_order = category.displayOrder.toLong(),
                        active = category.active.toDbLong(),
                        updated_at = category.updatedAtEpochMillis,
                    )
                }
            }
            database.habitsQueries.setSetting("defaults_seeded", 1L)
        }
    }

    private fun loadSnapshot() {
        mutableSnapshot.value = HabitSnapshot(
            categories = database.habitsQueries.selectAllCategories().executeAsList().map { row ->
                Category(
                    id = row.id,
                    name = row.name,
                    displayOrder = row.display_order.toInt(),
                    active = row.active != 0L,
                    updatedAtEpochMillis = row.updated_at,
                )
            },
            dailyHabits = database.habitsQueries.selectAllDailyHabits().executeAsList().map { row ->
                DailyHabit(
                    id = row.id,
                    name = row.name,
                    categoryId = row.category_id,
                    monthlyGoal = row.monthly_goal.toInt(),
                    displayOrder = row.display_order.toInt(),
                    active = row.active != 0L,
                    createdOn = LocalDate.parse(row.created_on),
                    archivedOn = row.archived_on?.let(LocalDate::parse),
                    createdAtEpochMillis = row.created_at,
                    updatedAtEpochMillis = row.updated_at,
                    kind = HabitKind.entries.firstOrNull { it.name == row.kind } ?: HabitKind.ACTION,
                    datedOnly = row.dated_only != 0L,
                )
            },
            dailyCompletions = database.habitsQueries.selectAllDailyCompletions().executeAsList().map { row ->
                DailyHabitCompletion(
                    habitId = row.habit_id,
                    date = LocalDate.parse(row.date),
                    completed = row.completed != 0L,
                    updatedAtEpochMillis = row.updated_at,
                    planId = row.plan_id,
                )
            },
            weeklyPlans = database.habitsQueries.selectAllWeeklyPlans().executeAsList().map { row ->
                WeeklyPlan(row.habit_id, row.weekday.toInt(), row.detail, row.updated_at)
            },
            dayPlans = database.habitsQueries.selectAllDayPlans().executeAsList().map { row ->
                DayPlan(row.habit_id, LocalDate.parse(row.date), row.detail, row.skipped != 0L, row.updated_at, row.id)
            },
            weeklyHabits = database.habitsQueries.selectAllWeeklyHabits().executeAsList().map { row ->
                WeeklyHabit(
                    id = row.id,
                    name = row.name,
                    categoryId = row.category_id,
                    displayOrder = row.display_order.toInt(),
                    active = row.active != 0L,
                    createdOn = LocalDate.parse(row.created_on),
                    archivedOn = row.archived_on?.let(LocalDate::parse),
                    createdAtEpochMillis = row.created_at,
                    updatedAtEpochMillis = row.updated_at,
                )
            },
            weeklyCompletions = database.habitsQueries.selectAllWeeklyCompletions().executeAsList().map { row ->
                WeeklyHabitCompletion(
                    weeklyHabitId = row.weekly_habit_id,
                    weekStartDate = LocalDate.parse(row.week_start_date),
                    completed = row.completed != 0L,
                    updatedAtEpochMillis = row.updated_at,
                )
            },
            sheetManagedHabitIds = database.habitsQueries.getTextSetting("sheet_managed_habits")
                .executeAsOneOrNull()?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty(),
        )
    }
}

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L
