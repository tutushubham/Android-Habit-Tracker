package com.habitsheet.data

import com.habitsheet.domain.backup.BackupValidator
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

class InMemoryHabitRepository(initial: HabitSnapshot = HabitSnapshot()) : HabitRepository {
    private val mutableSnapshot = MutableStateFlow(initial)
    override val snapshot: StateFlow<HabitSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun saveCategory(category: Category) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            categories = mutableSnapshot.value.categories.upsert(category) { it.id },
        )
    }

    override suspend fun deleteCategory(id: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            categories = mutableSnapshot.value.categories.filterNot { it.id == id },
            dailyHabits = mutableSnapshot.value.dailyHabits.map { habit ->
                if (habit.categoryId == id) habit.copy(categoryId = null) else habit
            },
            weeklyHabits = mutableSnapshot.value.weeklyHabits.map { habit ->
                if (habit.categoryId == id) habit.copy(categoryId = null) else habit
            },
        )
    }

    override suspend fun saveDailyHabit(habit: DailyHabit) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.upsert(habit) { it.id },
        )
    }

    override suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.map { habit ->
                if (habit.id == id) habit.copy(
                    active = false,
                    archivedOn = archivedOn,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                ) else habit
            },
        )
    }

    override suspend fun restoreDailyHabit(id: String, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.map { habit ->
                if (habit.id == id) habit.copy(
                    active = true,
                    archivedOn = null,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                ) else habit
            },
        )
    }

    override suspend fun deleteDailyHabit(id: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.filterNot { it.id == id },
            dailyCompletions = mutableSnapshot.value.dailyCompletions.filterNot { it.habitId == id },
            weeklyPlans = mutableSnapshot.value.weeklyPlans.filterNot { it.habitId == id },
            dayPlans = mutableSnapshot.value.dayPlans.filterNot { it.habitId == id },
        )
    }

    override suspend fun updateDailyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.map { habit ->
                orders[habit.id]?.let { order -> 
                    habit.copy(displayOrder = order, updatedAtEpochMillis = updatedAtEpochMillis)
                } ?: habit
            }
        )
    }

    override suspend fun saveWeeklyHabit(habit: WeeklyHabit) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.upsert(habit) { it.id },
        )
    }

    override suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.map { habit ->
                if (habit.id == id) habit.copy(
                    active = false,
                    archivedOn = archivedOn,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                ) else habit
            },
        )
    }

    override suspend fun restoreWeeklyHabit(id: String, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.map { habit ->
                if (habit.id == id) habit.copy(
                    active = true,
                    archivedOn = null,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                ) else habit
            },
        )
    }

    override suspend fun deleteWeeklyHabit(id: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.filterNot { it.id == id },
            weeklyCompletions = mutableSnapshot.value.weeklyCompletions.filterNot { it.weeklyHabitId == id },
        )
    }

    override suspend fun updateWeeklyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyHabits = mutableSnapshot.value.weeklyHabits.map { habit ->
                orders[habit.id]?.let { order -> 
                    habit.copy(displayOrder = order, updatedAtEpochMillis = updatedAtEpochMillis)
                } ?: habit
            }
        )
    }

    override suspend fun setDailyCompletion(completion: DailyHabitCompletion) {
        require(mutableSnapshot.value.dailyHabits.any { it.id == completion.habitId }) { "Unknown daily habit" }
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyCompletions = mutableSnapshot.value.dailyCompletions.upsert(completion) {
                it.planId to it.date
            },
        )
    }

    override suspend fun saveWeeklyPlan(plan: WeeklyPlan) {
        mutableSnapshot.value = mutableSnapshot.value.copy(weeklyPlans = mutableSnapshot.value.weeklyPlans.upsert(plan) { it.habitId to it.weekday })
    }

    override suspend fun deleteWeeklyPlan(habitId: String, weekday: Int) {
        mutableSnapshot.value = mutableSnapshot.value.copy(weeklyPlans = mutableSnapshot.value.weeklyPlans.filterNot { it.habitId == habitId && it.weekday == weekday })
    }

    override suspend fun saveDayPlan(plan: DayPlan) {
        mutableSnapshot.value = mutableSnapshot.value.copy(dayPlans = mutableSnapshot.value.dayPlans.upsert(plan) { it.id })
    }

    override suspend fun deleteDayPlan(habitId: String, date: LocalDate) {
        mutableSnapshot.value = mutableSnapshot.value.copy(dayPlans = mutableSnapshot.value.dayPlans.filterNot { it.habitId == habitId && it.date == date })
    }

    override suspend fun deleteDayPlanById(id: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(dayPlans = mutableSnapshot.value.dayPlans.filterNot { it.id == id })
    }

    override suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion) {
        require(mutableSnapshot.value.weeklyHabits.any { it.id == completion.weeklyHabitId }) { "Unknown weekly habit" }
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyCompletions = mutableSnapshot.value.weeklyCompletions.upsert(completion) {
                it.weeklyHabitId to it.weekStartDate
            },
        )
    }

    private var onboardingCompleted = false
    override suspend fun isOnboardingCompleted(): Boolean = onboardingCompleted
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboardingCompleted = completed
    }

    private var themeMode = 0
    override suspend fun getThemeMode(): Int = themeMode
    override suspend fun setThemeMode(mode: Int) {
        themeMode = mode
    }

    private var sheetUrl = ""
    private var sheetLastSync = 0L
    private var sheetSyncedKeys = emptySet<String>()
    override suspend fun getSheetUrl(): String = sheetUrl
    override suspend fun setSheetUrl(url: String) {
        if (sheetUrl != url) {
            sheetLastSync = 0L
            sheetSyncedKeys = emptySet()
            mutableSnapshot.value = mutableSnapshot.value.copy(sheetManagedHabitIds = emptySet())
        }
        sheetUrl = url
    }
    override suspend fun getSheetLastSync(): Long = sheetLastSync
    override suspend fun setSheetLastSync(epochMillis: Long) { sheetLastSync = epochMillis }
    override suspend fun getSheetSyncedKeys(): Set<String> = sheetSyncedKeys
    override suspend fun setSheetSyncedKeys(keys: Set<String>) { sheetSyncedKeys = keys }
    override suspend fun setSheetManagedHabitIds(ids: Set<String>) {
        mutableSnapshot.value = mutableSnapshot.value.copy(sheetManagedHabitIds = ids)
    }

    override suspend fun clearAllData() {
        mutableSnapshot.value = HabitSnapshot()
        onboardingCompleted = false
    }

    override suspend fun restoreFromSnapshot(snapshot: HabitSnapshot) {
        BackupValidator.validate(snapshot)
        mutableSnapshot.value = snapshot
    }
}

private fun <T, K> List<T>.upsert(value: T, key: (T) -> K): List<T> {
    val target = key(value)
    var replaced = false
    val updated = map { current ->
        if (key(current) == target) {
            replaced = true
            value
        } else current
    }
    return if (replaced) updated else updated + value
}
