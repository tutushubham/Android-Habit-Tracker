package com.habitsheet.data

import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
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

    override suspend fun saveDailyHabit(habit: DailyHabit) {
        require(habit.monthlyGoal >= 0)
        mutex.withLock {
            database.transaction {
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
                    id = habit.id,
                )
            }
            loadSnapshot()
        }
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

    override suspend fun setDailyCompletion(completion: DailyHabitCompletion) {
        mutex.withLock {
            database.habitsQueries.upsertDailyCompletion(
                habit_id = completion.habitId,
                date = completion.date.toString(),
                completed = completion.completed.toDbLong(),
                updated_at = completion.updatedAtEpochMillis,
            )
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

    fun close() {
        driver.close()
    }

    private fun seedDefaultsIfEmpty() {
        if (database.habitsQueries.selectCategoryCount().executeAsOne() != 0L) return
        val now = Clock.System.now().toEpochMilliseconds()
        database.transaction {
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
                )
            },
            dailyCompletions = database.habitsQueries.selectAllDailyCompletions().executeAsList().map { row ->
                DailyHabitCompletion(
                    habitId = row.habit_id,
                    date = LocalDate.parse(row.date),
                    completed = row.completed != 0L,
                    updatedAtEpochMillis = row.updated_at,
                )
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
        )
    }
}

private fun Boolean.toDbLong(): Long = if (this) 1L else 0L
