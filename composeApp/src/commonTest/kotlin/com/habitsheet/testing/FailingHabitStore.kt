package com.habitsheet.testing

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.domain.repository.HabitStore
import kotlinx.datetime.LocalDate

/**
 * A [HabitStore] whose write operations throw while their name is in [failing] (`"setDailyCompletion"`,
 * `"saveDayPlan"`, ...), for tests of the error messages ViewModels show. Everything else, and every operation
 * once it is removed from [failing], goes to [inner].
 */
class FailingHabitStore(private val inner: HabitStore, vararg failing: String) : HabitStore by inner {
    val failing: MutableSet<String> = failing.toMutableSet()

    private fun failIfListed(operation: String) {
        check(operation !in failing) { "injected failure in $operation" }
    }

    override suspend fun saveCategory(category: Category) {
        failIfListed("saveCategory")
        inner.saveCategory(category)
    }

    override suspend fun deleteCategory(id: String) {
        failIfListed("deleteCategory")
        inner.deleteCategory(id)
    }

    override suspend fun saveDailyHabit(habit: DailyHabit) {
        failIfListed("saveDailyHabit")
        inner.saveDailyHabit(habit)
    }

    override suspend fun archiveDailyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        failIfListed("archiveDailyHabit")
        inner.archiveDailyHabit(id, archivedOn, updatedAtEpochMillis)
    }

    override suspend fun restoreDailyHabit(id: String, updatedAtEpochMillis: Long) {
        failIfListed("restoreDailyHabit")
        inner.restoreDailyHabit(id, updatedAtEpochMillis)
    }

    override suspend fun deleteDailyHabit(id: String) {
        failIfListed("deleteDailyHabit")
        inner.deleteDailyHabit(id)
    }

    override suspend fun updateDailyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) {
        failIfListed("updateDailyHabitOrders")
        inner.updateDailyHabitOrders(orders, updatedAtEpochMillis)
    }

    override suspend fun saveWeeklyHabit(habit: WeeklyHabit) {
        failIfListed("saveWeeklyHabit")
        inner.saveWeeklyHabit(habit)
    }

    override suspend fun archiveWeeklyHabit(id: String, archivedOn: LocalDate, updatedAtEpochMillis: Long) {
        failIfListed("archiveWeeklyHabit")
        inner.archiveWeeklyHabit(id, archivedOn, updatedAtEpochMillis)
    }

    override suspend fun restoreWeeklyHabit(id: String, updatedAtEpochMillis: Long) {
        failIfListed("restoreWeeklyHabit")
        inner.restoreWeeklyHabit(id, updatedAtEpochMillis)
    }

    override suspend fun deleteWeeklyHabit(id: String) {
        failIfListed("deleteWeeklyHabit")
        inner.deleteWeeklyHabit(id)
    }

    override suspend fun updateWeeklyHabitOrders(orders: Map<String, Int>, updatedAtEpochMillis: Long) {
        failIfListed("updateWeeklyHabitOrders")
        inner.updateWeeklyHabitOrders(orders, updatedAtEpochMillis)
    }

    override suspend fun setDailyCompletion(completion: DailyHabitCompletion) {
        failIfListed("setDailyCompletion")
        inner.setDailyCompletion(completion)
    }

    override suspend fun saveWeeklyPlan(plan: WeeklyPlan) {
        failIfListed("saveWeeklyPlan")
        inner.saveWeeklyPlan(plan)
    }

    override suspend fun deleteWeeklyPlan(habitId: String, weekday: Int) {
        failIfListed("deleteWeeklyPlan")
        inner.deleteWeeklyPlan(habitId, weekday)
    }

    override suspend fun saveDayPlan(plan: DayPlan) {
        failIfListed("saveDayPlan")
        inner.saveDayPlan(plan)
    }

    override suspend fun deleteDayPlan(habitId: String, date: LocalDate) {
        failIfListed("deleteDayPlan")
        inner.deleteDayPlan(habitId, date)
    }

    override suspend fun deleteDayPlanById(id: String) {
        failIfListed("deleteDayPlanById")
        inner.deleteDayPlanById(id)
    }

    override suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion) {
        failIfListed("setWeeklyCompletion")
        inner.setWeeklyCompletion(completion)
    }
}
