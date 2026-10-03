package com.habitsheet.data

import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.model.monotonicUpdatedAt
import com.habitsheet.domain.backup.BackupValidator
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.CompletionKey
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
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
        val previous = mutableSnapshot.value.categories.firstOrNull { it.id == category.id }?.updatedAtEpochMillis
        mutableSnapshot.value = mutableSnapshot.value.copy(
            categories = mutableSnapshot.value.categories.upsert(category.copy(updatedAtEpochMillis = monotonicUpdatedAt(category.updatedAtEpochMillis, previous))) { it.id },
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
        val previous = mutableSnapshot.value.dailyHabits.firstOrNull { it.id == habit.id }?.updatedAtEpochMillis
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyHabits = mutableSnapshot.value.dailyHabits.upsert(habit.copy(updatedAtEpochMillis = monotonicUpdatedAt(habit.updatedAtEpochMillis, previous))) { it.id },
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
        val previous = mutableSnapshot.value.dailyCompletions
            .firstOrNull { it.planId == completion.planId && it.date == completion.date }?.updatedAtEpochMillis
        val stored = completion.copy(updatedAtEpochMillis = monotonicUpdatedAt(completion.updatedAtEpochMillis, previous, strict = true))
        mutableSnapshot.value = mutableSnapshot.value.copy(
            dailyCompletions = mutableSnapshot.value.dailyCompletions.upsert(stored) {
                it.planId to it.date
            },
            pendingCompletions = mutableSnapshot.value.pendingCompletions + CompletionKey(completion.planId, completion.date),
        )
    }

    override suspend fun saveWeeklyPlan(plan: WeeklyPlan) {
        val previous = mutableSnapshot.value.weeklyPlans.firstOrNull { it.habitId == plan.habitId && it.weekday == plan.weekday }?.updatedAtEpochMillis
        val stored = plan.copy(updatedAtEpochMillis = monotonicUpdatedAt(plan.updatedAtEpochMillis, previous))
        mutableSnapshot.value = mutableSnapshot.value.copy(weeklyPlans = mutableSnapshot.value.weeklyPlans.upsert(stored) { it.habitId to it.weekday })
    }

    override suspend fun deleteWeeklyPlan(habitId: String, weekday: Int) {
        mutableSnapshot.value = mutableSnapshot.value.copy(weeklyPlans = mutableSnapshot.value.weeklyPlans.filterNot { it.habitId == habitId && it.weekday == weekday })
    }

    override suspend fun saveDayPlan(plan: DayPlan) {
        val previous = mutableSnapshot.value.dayPlans.firstOrNull { it.id == plan.id }?.updatedAtEpochMillis
        val stored = plan.copy(updatedAtEpochMillis = monotonicUpdatedAt(plan.updatedAtEpochMillis, previous))
        mutableSnapshot.value = mutableSnapshot.value.copy(dayPlans = mutableSnapshot.value.dayPlans.upsert(stored) { it.id })
    }

    override suspend fun deleteDayPlan(habitId: String, date: LocalDate) {
        mutableSnapshot.value = mutableSnapshot.value.copy(dayPlans = mutableSnapshot.value.dayPlans.filterNot { it.habitId == habitId && it.date == date })
    }

    override suspend fun deleteDayPlanById(id: String) {
        mutableSnapshot.value = mutableSnapshot.value.copy(dayPlans = mutableSnapshot.value.dayPlans.filterNot { it.id == id })
    }

    override suspend fun setWeeklyCompletion(completion: WeeklyHabitCompletion) {
        require(mutableSnapshot.value.weeklyHabits.any { it.id == completion.weeklyHabitId }) { "Unknown weekly habit" }
        val previous = mutableSnapshot.value.weeklyCompletions
            .firstOrNull { it.weeklyHabitId == completion.weeklyHabitId && it.weekStartDate == completion.weekStartDate }?.updatedAtEpochMillis
        val stored = completion.copy(updatedAtEpochMillis = monotonicUpdatedAt(completion.updatedAtEpochMillis, previous, strict = true))
        mutableSnapshot.value = mutableSnapshot.value.copy(
            weeklyCompletions = mutableSnapshot.value.weeklyCompletions.upsert(stored) {
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
            mutableSnapshot.value = mutableSnapshot.value.copy(sheetManagedHabitIds = emptySet(), pendingCompletions = emptySet())
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

    override suspend fun applySheetSync(changes: SheetSyncChanges, newKeys: Set<String>, lastSync: Long) {
        // Build the complete new state first; the single assignment at the end is the "commit".
        var next = mutableSnapshot.value
        val deleted = changes.planIdsToDelete.toSet()
        next = next.copy(dayPlans = next.dayPlans.filterNot { it.id in deleted })
        changes.habitsToSave.forEach { habit ->
            require(habit.monthlyGoal >= 0)
            next = next.copy(dailyHabits = next.dailyHabits.upsert(habit) { it.id })
        }
        changes.plansToSave.forEach { plan ->
            require(plan.detail.isNotBlank())
            require(next.dailyHabits.any { it.id == plan.habitId }) { "Unknown daily habit" }
            next = next.copy(dayPlans = next.dayPlans.upsert(plan) { it.id })
        }
        changes.completionsToSave.forEach { completion ->
            require(next.dailyHabits.any { it.id == completion.habitId }) { "Unknown daily habit" }
            // A check toggled after the sync read its snapshot stays pending and wins.
            if (CompletionKey(completion.planId, completion.date) in next.pendingCompletions) return@forEach
            next = next.copy(dailyCompletions = next.dailyCompletions.upsert(completion) { it.planId to it.date })
        }
        changes.completionsToAcknowledge.forEach { ack ->
            val current = next.dailyCompletions.firstOrNull { it.planId == ack.planId && it.date == ack.date }
            if (current != null && current.updatedAtEpochMillis == ack.updatedAtEpochMillis) {
                next = next.copy(pendingCompletions = next.pendingCompletions - CompletionKey(ack.planId, ack.date))
            }
        }
        next = next.copy(sheetManagedHabitIds = changes.managedHabitIds)
        mutableSnapshot.value = next
        sheetSyncedKeys = newKeys
        sheetLastSync = lastSync
    }

    override suspend fun clearAllData() {
        mutableSnapshot.value = HabitSnapshot()
        onboardingCompleted = false
        sheetUrl = ""
        sheetSyncedKeys = emptySet()
        sheetLastSync = 0L
    }

    override suspend fun restoreFromSnapshot(snapshot: HabitSnapshot, settings: BackupSettings?, restoreSheetLink: Boolean) {
        BackupValidator.validate(snapshot)
        settings?.let(BackupValidator::validate)
        sheetSyncedKeys = emptySet()
        sheetLastSync = 0L
        settings?.themeMode?.let { themeMode = it }
        settings?.onboardingCompleted?.let { onboardingCompleted = it }
        if (restoreSheetLink && !settings?.sheetUrl.isNullOrBlank()) sheetUrl = settings!!.sheetUrl!!
        mutableSnapshot.value = snapshot.copy(sheetManagedHabitIds = emptySet(), pendingCompletions = emptySet())
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
