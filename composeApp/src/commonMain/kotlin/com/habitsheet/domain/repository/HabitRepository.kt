package com.habitsheet.domain.repository

import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

interface HabitRepository {
    val snapshot: StateFlow<HabitSnapshot>

    suspend fun refresh()
    suspend fun saveCategory(category: Category)
    suspend fun deleteCategory(id: String)
    suspend fun saveDailyHabit(habit: DailyHabit)
    suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long)
    suspend fun restoreDailyHabit(id: String, updatedAtEpochMillis: Long)
    suspend fun deleteDailyHabit(id: String)
    suspend fun updateDailyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long)
    suspend fun saveWeeklyHabit(habit: WeeklyHabit)
    suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long)
    suspend fun restoreWeeklyHabit(id: String, updatedAtEpochMillis: Long)
    suspend fun deleteWeeklyHabit(id: String)
    suspend fun updateWeeklyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long)
    suspend fun setDailyCompletion(completion: DailyHabitCompletion)
    suspend fun saveWeeklyPlan(plan: WeeklyPlan)
    suspend fun deleteWeeklyPlan(habitId: String, weekday: Int)
    suspend fun saveDayPlan(plan: DayPlan)
    suspend fun deleteDayPlan(habitId: String, date: LocalDate)
    suspend fun deleteDayPlanById(id: String)
    suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion)
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun getThemeMode(): Int
    suspend fun setThemeMode(mode: Int)
    suspend fun getSheetUrl(): String
    suspend fun setSheetUrl(url: String)
    suspend fun getSheetLastSync(): Long
    suspend fun setSheetLastSync(epochMillis: Long)
    suspend fun getSheetSyncedKeys(): Set<String>
    suspend fun setSheetSyncedKeys(keys: Set<String>)
    suspend fun setSheetManagedHabitIds(ids: Set<String>)
    /**
     * Applies a whole sheet sync atomically: [changes], the new synced [newKeys] and [lastSync].
     * Either everything is written (and the snapshot reloaded once) or nothing is.
     */
    suspend fun applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long)
    suspend fun clearAllData()
    /**
     * Replaces all habit data with [snapshot] in one transaction (validated first, so a bad backup changes nothing).
     * Sheet sync state is always cleared (synced keys, last sync, managed habits, pending flags) so the next sync
     * is a clean "sheet wins" reconcile. The sheet link is left alone unless [restoreSheetLink] is true and
     * [settings] carries one. A non-null [settings] also restores theme and onboarding.
     */
    suspend fun restoreFromSnapshot(snapshot: HabitSnapshot, settings: BackupSettings? = null, restoreSheetLink: Boolean = false)
}

fun interface IdGenerator {
    fun newId(): String
}
