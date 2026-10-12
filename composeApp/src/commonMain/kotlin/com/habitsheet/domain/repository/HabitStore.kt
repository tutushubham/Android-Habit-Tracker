package com.habitsheet.domain.repository

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.domain.plan.PlanApplication
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/** Habits, categories, plans and check-offs: the person's data. Every write is reflected in [snapshot]. */
interface HabitStore {
    val snapshot: StateFlow<HabitSnapshot>

    /** Re-reads everything from storage (another writer, such as the Android widget, may have changed it). */
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

    /** A check-off by the person (app or widget): stored with the pending-upload flag set. */
    suspend fun setDailyCompletion(completion: DailyHabitCompletion)
    suspend fun saveWeeklyPlan(plan: WeeklyPlan)
    suspend fun deleteWeeklyPlan(habitId: String, weekday: Int)
    suspend fun saveDayPlan(plan: DayPlan)
    suspend fun deleteDayPlan(habitId: String, date: LocalDate)
    suspend fun deleteDayPlanById(id: String)
    suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion)

    /**
     * Adds a plan from the first-run wizard ([com.habitsheet.domain.plan.PlanMerger]) in one transaction: its new
     * categories, habits and dated sessions. Additive; applying the same plan again changes nothing. Rejected before
     * anything is written when a session names a habit, or a habit a category, that neither exists nor comes with it.
     */
    suspend fun applyPlan(application: PlanApplication)
}

/** The checks [HabitStore.applyPlan] makes before writing, shared by every implementation. */
fun PlanApplication.requireConsistentWith(snapshot: HabitSnapshot) {
    val habitIds = snapshot.dailyHabits.mapTo(HashSet()) { it.id } + habits.map { it.id }
    val categoryIds = snapshot.categories.mapTo(HashSet()) { it.id } + categories.map { it.id }
    require(habits.all { it.monthlyGoal >= 0 && it.name.isNotBlank() }) { "a plan habit has no name or a negative goal" }
    require(habits.all { it.categoryId == null || it.categoryId in categoryIds }) { "a plan habit names an unknown category" }
    require(dayPlans.all { it.detail.isNotBlank() }) { "a plan session has no text" }
    require(dayPlans.all { it.habitId in habitIds }) { "a plan session names an unknown habit" }
}
