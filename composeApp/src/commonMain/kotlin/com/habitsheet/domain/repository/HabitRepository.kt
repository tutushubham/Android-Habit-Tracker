package com.habitsheet.domain.repository

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
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
    suspend fun saveWeeklyHabit(habit: WeeklyHabit)
    suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long)
    suspend fun restoreWeeklyHabit(id: String, updatedAtEpochMillis: Long)
    suspend fun deleteWeeklyHabit(id: String)
    suspend fun setDailyCompletion(completion: DailyHabitCompletion)
    suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion)
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun getThemeMode(): Int
    suspend fun setThemeMode(mode: Int)
    suspend fun clearAllData()
    suspend fun restoreFromSnapshot(snapshot: HabitSnapshot)
}

fun interface IdGenerator {
    fun newId(): String
}
