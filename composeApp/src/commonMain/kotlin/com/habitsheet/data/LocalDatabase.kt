package com.habitsheet.data

import app.cash.sqldelight.db.SqlDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.database.HabitsQueries
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.monotonicUpdatedAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/**
 * The open database and the in-memory [snapshot] it feeds, shared by the repository's parts. This is the one place
 * that serialises access (a single lock), opens transactions and reloads the snapshot.
 */
internal class LocalDatabase private constructor(private val driver: SqlDriver) {
    private val database = HabitsDatabase(driver)
    private val queries: HabitsQueries = database.habitsQueries
    private val mutex = Mutex()
    private val mutableSnapshot = MutableStateFlow(HabitSnapshot())
    val snapshot: StateFlow<HabitSnapshot> = mutableSnapshot.asStateFlow()

    /** Runs [block] under the lock, outside a transaction and without touching the snapshot (reads, settings). */
    suspend fun <T> locked(block: HabitsQueries.() -> T): T = mutex.withLock { queries.block() }

    /** Runs [block] in one transaction under the lock, then reloads the snapshot once (only if it committed). */
    suspend fun write(block: HabitsQueries.() -> Unit) {
        mutex.withLock {
            database.transaction { queries.block() }
            reload()
        }
    }

    /**
     * Runs [block] in one transaction under the lock, then updates the snapshot with [patch] instead of re-reading
     * every table: for single-row writes whose result the caller knows exactly (a check-off). [patch] must give the
     * same snapshot a reload would (`SnapshotParityTest`).
     */
    suspend fun <T> writeAndPatch(block: HabitsQueries.() -> T, patch: HabitSnapshot.(T) -> HabitSnapshot) {
        mutex.withLock {
            val written = database.transactionWithResult { queries.block() }
            mutableSnapshot.value = mutableSnapshot.value.patch(written)
        }
    }

    suspend fun refresh() {
        mutex.withLock { reload() }
    }

    fun close() {
        driver.close()
    }

    private fun reload() {
        val parsedDates = HashMap<String, LocalDate>()
        val parseDate = { text: String -> parsedDates.getOrPut(text) { LocalDate.parse(text) } }
        val completionRows = queries.selectAllDailyCompletions().executeAsList()
        val dailyCompletions = ArrayList<DailyHabitCompletion>(completionRows.size)
        val pending = HashSet<CompletionKey>()
        completionRows.forEach { row ->
            val completion = row.toDomain(parseDate)
            dailyCompletions += completion
            if (row.pending_upload != 0L) pending += CompletionKey(completion.planId, completion.date)
        }
        mutableSnapshot.value = HabitSnapshot(
            categories = queries.selectAllCategories().executeAsList().map { it.toDomain() },
            dailyHabits = queries.selectAllDailyHabits().executeAsList().map { it.toDomain() },
            dailyCompletions = dailyCompletions.sortedWith(DAILY_COMPLETION_ORDER),
            weeklyPlans = queries.selectAllWeeklyPlans().executeAsList().map { it.toDomain() },
            dayPlans = queries.selectAllDayPlans().executeAsList().map { it.toDomain() },
            weeklyHabits = queries.selectAllWeeklyHabits().executeAsList().map { it.toDomain() },
            weeklyCompletions = queries.selectAllWeeklyCompletions().executeAsList().map { it.toDomain(parseDate) }
                .sortedWith(WEEKLY_COMPLETION_ORDER),
            pendingCompletions = pending,
            sheetManagedHabitIds = queries.getTextSetting(SettingKeys.SHEET_MANAGED_HABITS).executeAsOneOrNull()
                ?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty(),
        )
    }

    private fun seedDefaultsIfEmpty() {
        val defaultsAlreadySeeded = queries.getSetting(SettingKeys.DEFAULTS_SEEDED).executeAsOneOrNull() == 1L
        if (defaultsAlreadySeeded) return
        database.transaction {
            if (queries.selectCategoryCount().executeAsOne() == 0L) {
                val now = Clock.System.now().toEpochMilliseconds()
                DefaultData.categories(now).forEach { queries.insert(it) }
            }
            queries.setSetting(SettingKeys.DEFAULTS_SEEDED, 1L)
        }
    }

    companion object {
        /** Opens (creating or migrating) the database, seeds the default categories once and loads the snapshot. */
        fun open(driverFactory: DriverFactory): LocalDatabase {
            val driver = driverFactory.createDriver()
            try {
                driver.execute(null, "PRAGMA foreign_keys = ON", 0)
                return LocalDatabase(driver).apply {
                    seedDefaultsIfEmpty()
                    reload()
                }
            } catch (e: Throwable) {
                // Opening or migrating failed: release the file so the recovery screen can copy or move it.
                runCatching { driver.close() }
                throw e
            }
        }
    }
}

/** Keys of the `settingsEntity` (integer) and `textSettingsEntity` (text) tables. */
internal object SettingKeys {
    const val DEFAULTS_SEEDED = "defaults_seeded"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val THEME_MODE = "theme_mode"
    const val SHEET_URL = "sheet_url"
    const val SHEET_LAST_SYNC = "sheet_last_sync"
    const val SHEET_SYNCED_KEYS = "sheet_synced_keys"
    const val SHEET_MANAGED_HABITS = "sheet_managed_habits"
}

/** `updated_at` for a daily completion: never earlier than the stored one, and strictly later (upload acks compare it). */
internal fun HabitsQueries.nextCompletionTime(planId: String, date: String, candidate: Long): Long = monotonicUpdatedAt(
    candidate,
    selectDailyCompletionUpdatedAt(planId, date).executeAsOneOrNull(),
    strict = true,
)

internal fun HabitsQueries.nextDayPlanTime(id: String, candidate: Long): Long =
    monotonicUpdatedAt(candidate, selectDayPlanUpdatedAt(id).executeAsOneOrNull())

/** Forgets what the last sync learned from the sheet (managed habits, synced keys, last sync time). */
internal fun HabitsQueries.clearSheetSyncState() {
    setTextSetting(SettingKeys.SHEET_MANAGED_HABITS, "")
    setTextSetting(SettingKeys.SHEET_SYNCED_KEYS, "")
    setTextSetting(SettingKeys.SHEET_LAST_SYNC, "0")
}

/** Deletes every habit, plan, category and check-off (children first: the JVM driver ignores foreign keys). */
internal fun HabitsQueries.deleteAllHabitData() {
    clearAllDailyCompletions()
    clearAllWeeklyCompletions()
    clearAllDayPlans()
    clearAllWeeklyPlans()
    clearAllData()
    clearAllWeeklyHabits()
    clearAllCategories()
}
