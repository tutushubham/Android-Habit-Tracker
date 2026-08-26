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
    suspend fun saveDailyHabit(habit: DailyHabit)
    suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long)
    suspend fun saveWeeklyHabit(habit: WeeklyHabit)
    suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long)
    suspend fun setDailyCompletion(completion: DailyHabitCompletion)
    suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion)
}

fun interface IdGenerator {
    fun newId(): String
}
