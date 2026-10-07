package com.habitsheet.presentation

import androidx.lifecycle.ViewModel
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.repository.HabitStore
import com.habitsheet.domain.repository.IdGenerator
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManageHabitsViewModel(
    private val repository: HabitStore,
    private val idGenerator: IdGenerator,
    private val dateProvider: DateProvider = SystemDateProvider,
    scope: CoroutineScope? = null,
    private val logger: Logger = NoOpLogger,
) : ViewModel() {
    /** Defaults to the ViewModel's own background scope; tests inject theirs. */
    private val scope: CoroutineScope = scope ?: backgroundScope()
    val state: StateFlow<com.habitsheet.domain.model.HabitSnapshot> = repository.snapshot

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun saveCategory(id: String, name: String, displayOrder: Int, active: Boolean = true) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val now = dateProvider.nowEpochMillis()
        scope.launch {
            runCatchingCancellable {
                repository.saveCategory(Category(id, trimmed, displayOrder, active, now))
            }.onFailure {
                logger.e(TAG, "saveCategory failed", it)
                _error.value = UiText.of(Res.string.err_save_category)
            }
        }
    }

    fun addCategory(name: String) {
        val active = state.value.categories.filter { it.active }
        if (active.size >= MAX_CATEGORIES) return
        saveCategory(
            id = idGenerator.newId(),
            name = name,
            displayOrder = (state.value.categories.maxOfOrNull { it.displayOrder } ?: -1) + 1,
        )
    }

    fun archiveCategory(id: String) {
        val category = state.value.categories.firstOrNull { it.id == id } ?: return
        saveCategory(id, category.name, category.displayOrder, active = false)
    }

    fun deleteCategory(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.deleteCategory(id)
            }.onFailure {
                logger.e(TAG, "deleteCategory failed", it)
                _error.value = UiText.of(Res.string.err_delete_category)
            }
        }
    }

    fun addDailyHabit(name: String, categoryId: String?, monthlyGoal: Int, kind: HabitKind = HabitKind.ACTION, datedOnly: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || monthlyGoal < 0) return
        if (state.value.dailyHabits.count { it.active } >= MAX_DAILY_HABITS) {
            _error.value = UiText.of(Res.string.err_max_daily_habits, MAX_DAILY_HABITS)
            return
        }
        val now = dateProvider.nowEpochMillis()
        scope.launch {
            runCatchingCancellable {
                repository.saveDailyHabit(
                    DailyHabit(
                        id = idGenerator.newId(),
                        name = trimmed,
                        categoryId = categoryId,
                        monthlyGoal = monthlyGoal,
                        displayOrder = (state.value.dailyHabits.maxOfOrNull { it.displayOrder } ?: -1) + 1,
                        active = true,
                        createdOn = dateProvider.today(),
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        kind = kind,
                        datedOnly = datedOnly,
                    ),
                )
            }.onFailure {
                logger.e(TAG, "addDailyHabit failed", it)
                _error.value = UiText.of(Res.string.err_create_habit)
            }
        }
    }

    fun updateDailyHabit(habit: DailyHabit) {
        if (habit.name.isBlank() || habit.monthlyGoal < 0) return
        scope.launch {
            runCatchingCancellable {
                repository.saveDailyHabit(habit.copy(updatedAtEpochMillis = dateProvider.nowEpochMillis()))
            }.onFailure {
                logger.e(TAG, "updateDailyHabit failed", it)
                _error.value = UiText.of(Res.string.err_update_habit)
            }
        }
    }

    fun archiveDailyHabit(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.archiveDailyHabit(id, dateProvider.today(), dateProvider.nowEpochMillis())
            }.onFailure {
                logger.e(TAG, "archiveDailyHabit failed", it)
                _error.value = UiText.of(Res.string.err_archive_habit)
            }
        }
    }

    fun restoreDailyHabit(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.restoreDailyHabit(id, dateProvider.nowEpochMillis())
            }.onFailure {
                logger.e(TAG, "restoreDailyHabit failed", it)
                _error.value = UiText.of(Res.string.err_restore_habit)
            }
        }
    }

    fun deleteDailyHabit(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.deleteDailyHabit(id)
            }.onFailure {
                logger.e(TAG, "deleteDailyHabit failed", it)
                _error.value = UiText.of(Res.string.err_delete_habit)
            }
        }
    }

    fun moveDailyHabit(fromIndex: Int, toIndex: Int) {
        val habits = state.value.dailyHabits.filter { it.active }.sortedBy { it.displayOrder }.toMutableList()
        if (fromIndex !in habits.indices || toIndex !in habits.indices) return

        val habit = habits.removeAt(fromIndex)
        habits.add(toIndex, habit)

        val newOrders = habits.mapIndexed { index, h -> h.id to index }.toMap()
        scope.launch {
            runCatchingCancellable {
                repository.updateDailyHabitOrders(newOrders, dateProvider.nowEpochMillis())
            }.onFailure {
                logger.e(TAG, "moveDailyHabit failed", it)
                _error.value = UiText.of(Res.string.err_reorder_habits)
            }
        }
    }

    fun addWeeklyHabit(name: String, categoryId: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (state.value.weeklyHabits.count { it.active } >= MAX_WEEKLY_HABITS) {
            _error.value = UiText.of(Res.string.err_max_weekly_habits, MAX_WEEKLY_HABITS)
            return
        }
        val now = dateProvider.nowEpochMillis()
        scope.launch {
            runCatchingCancellable {
                repository.saveWeeklyHabit(
                    WeeklyHabit(
                        id = idGenerator.newId(),
                        name = trimmed,
                        categoryId = categoryId,
                        displayOrder = (state.value.weeklyHabits.maxOfOrNull { it.displayOrder } ?: -1) + 1,
                        active = true,
                        createdOn = dateProvider.today(),
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            }.onFailure {
                logger.e(TAG, "addWeeklyHabit failed", it)
                _error.value = UiText.of(Res.string.err_create_weekly_habit)
            }
        }
    }

    fun updateWeeklyHabit(habit: WeeklyHabit) {
        if (habit.name.isBlank()) return
        scope.launch {
            runCatchingCancellable {
                repository.saveWeeklyHabit(habit.copy(updatedAtEpochMillis = dateProvider.nowEpochMillis()))
            }.onFailure {
                logger.e(TAG, "updateWeeklyHabit failed", it)
                _error.value = UiText.of(Res.string.err_update_weekly_habit)
            }
        }
    }

    fun archiveWeeklyHabit(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.archiveWeeklyHabit(id, dateProvider.today(), dateProvider.nowEpochMillis())
            }.onFailure {
                logger.e(TAG, "archiveWeeklyHabit failed", it)
                _error.value = UiText.of(Res.string.err_archive_weekly_habit)
            }
        }
    }

    fun restoreWeeklyHabit(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.restoreWeeklyHabit(id, dateProvider.nowEpochMillis())
            }.onFailure {
                logger.e(TAG, "restoreWeeklyHabit failed", it)
                _error.value = UiText.of(Res.string.err_restore_weekly_habit)
            }
        }
    }

    fun deleteWeeklyHabit(id: String) {
        scope.launch {
            runCatchingCancellable {
                repository.deleteWeeklyHabit(id)
            }.onFailure {
                logger.e(TAG, "deleteWeeklyHabit failed", it)
                _error.value = UiText.of(Res.string.err_delete_weekly_habit)
            }
        }
    }

    fun moveWeeklyHabit(fromIndex: Int, toIndex: Int) {
        val habits = state.value.weeklyHabits.filter { it.active }.sortedBy { it.displayOrder }.toMutableList()
        if (fromIndex !in habits.indices || toIndex !in habits.indices) return

        val habit = habits.removeAt(fromIndex)
        habits.add(toIndex, habit)

        val newOrders = habits.mapIndexed { index, h -> h.id to index }.toMap()
        scope.launch {
            runCatchingCancellable {
                repository.updateWeeklyHabitOrders(newOrders, dateProvider.nowEpochMillis())
            }.onFailure {
                logger.e(TAG, "moveWeeklyHabit failed", it)
                _error.value = UiText.of(Res.string.err_reorder_weekly_habits)
            }
        }
    }

    companion object {
        private const val TAG = "ManageHabits"
        const val MAX_CATEGORIES = 10
        const val MAX_DAILY_HABITS = 20
        const val MAX_WEEKLY_HABITS = 20
    }
}
