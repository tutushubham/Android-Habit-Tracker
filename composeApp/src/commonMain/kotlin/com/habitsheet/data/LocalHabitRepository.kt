package com.habitsheet.data

import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.backup.BackupValidator
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.HabitKind
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
    seedOndPlan: Boolean = true,
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
        if (seedOndPlan) seedOndPlanIfNeeded()
        if (seedOndPlan) seedWinterArcRoutinesIfNeeded()
        if (seedOndPlan) ensureWinterArcHabitsAreDatedOnly()
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

    /** Add the OND starter plan once, preserving existing habits, edits and completions. */
    private fun seedOndPlanIfNeeded() {
        if (database.habitsQueries.getSetting("ond_2026_seeded").executeAsOneOrNull() == 1L) return
        val now = Clock.System.now().toEpochMilliseconds()
        val templates = listOf(
            Triple("Run", "category-4", 12),
            Triple("Workout", "category-4", 12),
            Triple("Android", "category-10", 12),
            Triple("DSA", "category-10", 12),
            Triple("SDE", "category-10", 8),
            Triple("Mobility", "category-4", 8),
            Triple("Wake Early", "category-8", 30),
            Triple("Morning Routine", "category-7", 30),
            Triple("Study", "category-10", 30),
            Triple("Sleep on Time", "category-8", 30),
            Triple("No Junk Food", "category-2", 30),
            Triple("No Adult Content", "category-5", 30),
            Triple("No Gooning", "category-5", 30),
        )
        val weekdays = mapOf(
            "Run" to listOf(2, 4, 7),
            "Workout" to listOf(1, 3, 5),
            "Android" to listOf(1, 3, 5),
            "DSA" to listOf(2, 4, 6),
            "SDE" to listOf(5, 7),
        )
        database.transaction {
            val habits = database.habitsQueries.selectAllDailyHabits().executeAsList()
            val ids = templates.associate { (name, categoryId, monthlyGoal) ->
                val existing = habits.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val id = existing?.id ?: "ond-2026-${name.lowercase()}"
                if (existing == null) {
                    database.habitsQueries.insertDailyHabit(
                        id = id,
                        name = name,
                        category_id = categoryId,
                        monthly_goal = monthlyGoal.toLong(),
                        display_order = (habits.size + templates.indexOf(Triple(name, categoryId, monthlyGoal))).toLong(),
                        active = 1L,
                        created_on = "2026-10-01",
                        archived_on = null,
                        created_at = now,
                        updated_at = now,
                        kind = if (name.startsWith("No ")) HabitKind.AVOIDANCE.name else HabitKind.ACTION.name,
                        // Plan-driven: only appear on weekly/dated sessions, never as silent daily checkboxes.
                        dated_only = 1L,
                    )
                }
                name to id
            }
            val existingWeekly = database.habitsQueries.selectAllWeeklyPlans().executeAsList()
                .map { it.habit_id to it.weekday.toInt() }.toSet()
            weekdays.forEach { (name, days) ->
                val id = ids.getValue(name)
                if (existingWeekly.none { it.first == id }) {
                    days.forEach { weekday ->
                        database.habitsQueries.upsertWeeklyPlan(id, weekday.toLong(), "Plan in OND sheet", now)
                    }
                }
            }
            val existingDays = database.habitsQueries.selectAllDayPlans().executeAsList()
                .map { it.id }.toSet()
            OndSeedData.sessions.forEach { session ->
                val id = ids.getValue(session.habit)
                if (session.id !in existingDays) {
                    database.habitsQueries.upsertDayPlan(session.id, id, session.date, session.detail, session.skipped.toDbLong(), now)
                }
            }
            database.habitsQueries.setSetting("ond_2026_seeded", 1L)
        }
    }

    /** Add new Winter Arc routines to existing installations without re-seeding edited sessions. */
    private fun seedWinterArcRoutinesIfNeeded() {
        if (database.habitsQueries.getSetting("winter_arc_routines_seeded").executeAsOneOrNull() == 1L) return
        val now = Clock.System.now().toEpochMilliseconds()
        val routineNames = setOf("Mobility", "Wake Early", "Morning Routine", "Study", "Sleep on Time", "No Junk Food", "No Adult Content", "No Gooning")
        val categories = mapOf(
            "Mobility" to "category-4", "Wake Early" to "category-8", "Morning Routine" to "category-7",
            "Study" to "category-10", "Sleep on Time" to "category-8", "No Junk Food" to "category-2",
            "No Adult Content" to "category-5", "No Gooning" to "category-5",
        )
        database.transaction {
            val existing = database.habitsQueries.selectAllDailyHabits().executeAsList()
            val ids = routineNames.associateWith { name ->
                val current = existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val id = current?.id ?: "ond-2026-${name.lowercase().replace(' ', '-')}"
                if (current == null) database.habitsQueries.insertDailyHabit(
                    id = id, name = name, category_id = categories[name], monthly_goal = 30L,
                    display_order = (existing.size + routineNames.indexOf(name)).toLong(), active = 1L,
                    created_on = "2026-10-01", archived_on = null, created_at = now, updated_at = now,
                    kind = if (name.startsWith("No ")) HabitKind.AVOIDANCE.name else HabitKind.ACTION.name,
                    dated_only = 1L,
                )
                id
            }
            val oldRows = database.habitsQueries.selectAllDayPlans().executeAsList().map { it.id }.toSet()
            OndSeedData.sessions.filter { it.habit in routineNames && it.id !in oldRows }.forEach { session ->
                database.habitsQueries.upsertDayPlan(session.id, ids.getValue(session.habit), session.date,
                    session.detail, session.skipped.toDbLong(), now)
            }
            database.habitsQueries.setSetting("winter_arc_routines_seeded", 1L)
        }
    }

    /**
     * Existing installs seeded routines without datedOnly, so unplanned days still showed
     * actionable checkboxes. Mark Winter Arc habit names as plan-driven once.
     */
    private fun ensureWinterArcHabitsAreDatedOnly() {
        if (database.habitsQueries.getSetting("winter_arc_dated_only").executeAsOneOrNull() == 1L) return
        val names = OndSeedData.sessions.map { it.habit.trim().lowercase() }.toSet()
        val now = Clock.System.now().toEpochMilliseconds()
        database.transaction {
            database.habitsQueries.selectAllDailyHabits().executeAsList()
                .filter { it.name.trim().lowercase() in names && it.dated_only == 0L }
                .forEach { row ->
                    database.habitsQueries.updateDailyHabit(
                        name = row.name,
                        category_id = row.category_id,
                        monthly_goal = row.monthly_goal,
                        display_order = row.display_order,
                        active = row.active,
                        created_on = row.created_on,
                        archived_on = row.archived_on,
                        updated_at = now,
                        kind = row.kind,
                        dated_only = 1L,
                        id = row.id,
                    )
                }
            database.habitsQueries.setSetting("winter_arc_dated_only", 1L)
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
