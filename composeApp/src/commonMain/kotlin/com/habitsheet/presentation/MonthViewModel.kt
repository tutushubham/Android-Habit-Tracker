package com.habitsheet.presentation

import com.habitsheet.domain.calculation.CategorySummary
import com.habitsheet.domain.calculation.DailySummary
import com.habitsheet.domain.calculation.DailyWeekSummary
import com.habitsheet.domain.calculation.HabitCalculations
import com.habitsheet.domain.calculation.HabitSummary
import com.habitsheet.domain.calculation.ProgressSummary
import com.habitsheet.domain.calculation.WeeklyBlockSummary
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.repository.HabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class MonthUiState(
    val selectedMonth: MonthKey,
    val today: LocalDate,
    val categories: List<Category>,
    val habits: List<HabitSummary>,
    val daily: List<DailySummary>,
    val categorySummaries: List<CategorySummary>,
    val dailyWeeks: List<DailyWeekSummary>,
    val weeklyHabits: List<WeeklyHabit>,
    val weeklyBlocks: List<WeeklyBlockSummary>,
    val monthlyProgress: ProgressSummary,
    val weeklyProgress: ProgressSummary,
    val dailyCompletionKeys: Set<Pair<String, LocalDate>>,
    val weeklyCompletionKeys: Set<Pair<String, LocalDate>>,
) {
    val isEmpty: Boolean get() = habits.isEmpty() && weeklyHabits.isEmpty()
}

class MonthViewModel(
    private val repository: HabitRepository,
    private val dateProvider: DateProvider = SystemDateProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val selectedMonth = MutableStateFlow(MonthKey.from(dateProvider.today()))

    val state: StateFlow<MonthUiState> = combine(repository.snapshot, selectedMonth) { snapshot, month ->
        snapshot.toUiState(month, dateProvider.today())
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = repository.snapshot.value.toUiState(selectedMonth.value, dateProvider.today()),
    )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.previous()
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.next()
    }

    fun currentMonth() {
        selectedMonth.value = MonthKey.from(dateProvider.today())
    }

    fun selectMonth(month: MonthKey) {
        selectedMonth.value = month
    }

    fun toggleDaily(habitId: String, date: LocalDate) {
        val completed = habitId to date !in state.value.dailyCompletionKeys
        scope.launch {
            repository.setDailyCompletion(
                DailyHabitCompletion(habitId, date, completed, dateProvider.nowEpochMillis()),
            )
        }
    }

    fun toggleWeekly(habitId: String, weekStartDate: LocalDate) {
        val completed = habitId to weekStartDate !in state.value.weeklyCompletionKeys
        scope.launch {
            repository.setWeeklyCompletion(
                WeeklyHabitCompletion(habitId, weekStartDate, completed, dateProvider.nowEpochMillis()),
            )
        }
    }

    fun close() {
        scope.cancel()
    }
}

private fun HabitSnapshot.toUiState(month: MonthKey, today: LocalDate): MonthUiState {
    val habitSummaries = HabitCalculations.habitSummaries(month, dailyHabits, dailyCompletions)
    val dailySummaries = HabitCalculations.dailySummaries(month, dailyHabits, dailyCompletions)
    val weekly = HabitCalculations.weeklyBlockSummaries(month, weeklyHabits, weeklyCompletions)
    val monthDates = month.dates().toSet()
    val weekStarts = month.weekStarts().toSet()
    return MonthUiState(
        selectedMonth = month,
        today = today,
        categories = categories.sortedBy { it.displayOrder },
        habits = habitSummaries,
        daily = dailySummaries,
        categorySummaries = HabitCalculations.categorySummaries(categories, habitSummaries),
        dailyWeeks = HabitCalculations.dailyWeekSummaries(month, dailySummaries),
        weeklyHabits = weeklyHabits
            .filter { habit -> weekStarts.any(habit::isActiveOn) }
            .sortedBy { it.displayOrder },
        weeklyBlocks = weekly,
        monthlyProgress = HabitCalculations.monthlyProgress(habitSummaries),
        weeklyProgress = HabitCalculations.weeklyOverall(weekly),
        dailyCompletionKeys = dailyCompletions
            .asSequence()
            .filter { it.completed && it.date in monthDates }
            .map { it.habitId to it.date }
            .toSet(),
        weeklyCompletionKeys = weeklyCompletions
            .asSequence()
            .filter { it.completed && it.weekStartDate in weekStarts }
            .map { it.weeklyHabitId to it.weekStartDate }
            .toSet(),
    )
}
