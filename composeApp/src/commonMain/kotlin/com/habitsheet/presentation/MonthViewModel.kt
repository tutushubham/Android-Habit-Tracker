package com.habitsheet.presentation

import com.habitsheet.domain.calculation.CategorySummary
import com.habitsheet.domain.calculation.DailyShareSummary
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class MonthUiState(
    val selectedMonth: MonthKey,
    val today: LocalDate,
    val selectedDay: LocalDate,
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
    val todaySummary: DailyShareSummary,
    val onboardingVisible: Boolean,
    val scrollToTodayTrigger: Long,
    val todayMode: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = habits.isEmpty() && weeklyHabits.isEmpty()
}

class MonthViewModel(
    private val repository: HabitRepository,
    private val dateProvider: DateProvider = SystemDateProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val selectedMonth = MutableStateFlow(MonthKey.from(dateProvider.today()))
    private val selectedDay = MutableStateFlow(dateProvider.today())
    private val onboardingVisible = MutableStateFlow(false)
    private val scrollToTodayTrigger = MutableStateFlow(0L)
    private val todayMode = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val completionMutex = Mutex()
    private val today = flow {
        while (true) {
            emit(dateProvider.today())
            delay(1.minutes) // Refresh today's date every minute
        }
    }

    init {
        scope.launch {
            try {
                onboardingVisible.value = !repository.isOnboardingCompleted()
            } catch (e: Exception) {
                error.value = "Couldn't load app settings."
            }
        }
    }

    val state: StateFlow<MonthUiState> = combine(
        repository.snapshot,
        selectedMonth,
        onboardingVisible,
        scrollToTodayTrigger,
        today,
        todayMode,
        selectedDay,
        error
    ) { args: Array<Any?> ->
        val snapshot = args[0] as HabitSnapshot
        val month = args[1] as MonthKey
        val onboarding = args[2] as Boolean
        val trigger = args[3] as Long
        val currentToday = args[4] as LocalDate
        val mode = args[5] as Boolean
        val day = args[6] as LocalDate
        val currentError = args[7] as String?
        snapshot.toUiState(month, currentToday, day, onboarding, trigger, mode, currentError)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = repository.snapshot.value.toUiState(selectedMonth.value, dateProvider.today(), selectedDay.value, false, 0, false, null),
    )

    fun setTodayMode(enabled: Boolean) {
        todayMode.value = enabled
        if (enabled) {
            val currentDay = dateProvider.today()
            selectedDay.value = currentDay
            selectedMonth.value = MonthKey.from(currentDay)
        }
    }

    fun previousDay() = selectDay(LocalDate.fromEpochDays(selectedDay.value.toEpochDays() - 1))

    fun nextDay() = selectDay(LocalDate.fromEpochDays(selectedDay.value.toEpochDays() + 1))

    fun currentDay() = selectDay(dateProvider.today())

    private fun selectDay(day: LocalDate) {
        selectedDay.value = day
        selectedMonth.value = MonthKey.from(day)
    }

    fun clearError() {
        error.value = null
    }

    fun completeOnboarding() {
        onboardingVisible.value = false
        scope.launch {
            try {
                repository.setOnboardingCompleted(true)
            } catch (e: Exception) {
                // Not critical, but we can log or ignore
            }
        }
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.previous()
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.next()
    }

    fun currentMonth() {
        val today = dateProvider.today()
        selectedMonth.value = MonthKey.from(today)
        scrollToTodayTrigger.value = dateProvider.nowEpochMillis()
    }

    fun selectMonth(month: MonthKey) {
        selectedMonth.value = month
    }

    fun toggleDaily(habitId: String, date: LocalDate) {
        scope.launch {
            try {
                completionMutex.withLock {
                    val habit = repository.snapshot.value.dailyHabits.firstOrNull { it.id == habitId }
                    if (habit == null || !habit.isActiveOn(date)) return@withLock
                    val key = habitId to date
                    val completed = repository.snapshot.value.dailyCompletions
                        .any { it.completed && (it.habitId to it.date) == key }
                    repository.setDailyCompletion(
                        DailyHabitCompletion(habitId, date, !completed, dateProvider.nowEpochMillis()),
                    )
                }
            } catch (e: Exception) {
                error.value = "Couldn't update habit. Please try again."
            }
        }
    }

    fun toggleWeekly(habitId: String, weekStartDate: LocalDate) {
        scope.launch {
            try {
                completionMutex.withLock {
                    val habit = repository.snapshot.value.weeklyHabits.firstOrNull { it.id == habitId }
                    val month = MonthKey.from(weekStartDate)
                    val weekIndex = (weekStartDate.day - 1) / 7
                    val activeDuringWeek = habit != null && month.datesForWeek(weekIndex).any(habit::isActiveOn)
                    if (!activeDuringWeek) return@withLock
                    val key = habitId to weekStartDate
                    val completed = repository.snapshot.value.weeklyCompletions
                        .any { it.completed && (it.weeklyHabitId to it.weekStartDate) == key }
                    repository.setWeeklyCompletion(
                        WeeklyHabitCompletion(habitId, weekStartDate, !completed, dateProvider.nowEpochMillis()),
                    )
                }
            } catch (e: Exception) {
                error.value = "Couldn't update weekly habit. Please try again."
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}

private fun HabitSnapshot.toUiState(
    month: MonthKey, 
    today: LocalDate, 
    selectedDay: LocalDate,
    onboardingVisible: Boolean,
    scrollToTodayTrigger: Long,
    todayMode: Boolean,
    error: String?
): MonthUiState {
    val habitSummaries = HabitCalculations.habitSummaries(month, dailyHabits, dailyCompletions)
    val dailySummaries = HabitCalculations.dailySummaries(month, dailyHabits, dailyCompletions)
    val weekly = HabitCalculations.weeklyBlockSummaries(month, weeklyHabits, weeklyCompletions)
    val monthDates = month.dates().toSet()
    val weekStarts = month.weekStarts().toSet()
    return MonthUiState(
        selectedMonth = month,
        today = today,
        selectedDay = selectedDay,
        categories = categories.sortedBy { it.displayOrder },
        habits = habitSummaries,
        daily = dailySummaries,
        categorySummaries = HabitCalculations.categorySummaries(categories, habitSummaries),
        dailyWeeks = HabitCalculations.dailyWeekSummaries(month, dailySummaries),
        weeklyHabits = weeklyHabits
            .filter { habit -> month.dates().any(habit::isActiveOn) }
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
        todaySummary = HabitCalculations.dailyShareSummary(selectedDay, dailyHabits, categories, dailyCompletions),
        onboardingVisible = onboardingVisible,
        scrollToTodayTrigger = scrollToTodayTrigger,
        todayMode = todayMode,
        error = error,
    )
}
