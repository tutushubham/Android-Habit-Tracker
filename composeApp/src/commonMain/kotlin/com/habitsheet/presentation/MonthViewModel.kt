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
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.domain.model.PlannedHabit
import com.habitsheet.domain.model.plannedHabitsOn
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.platform.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val dueKeys: Set<Pair<String, LocalDate>>,
    val weeklyCompletionKeys: Set<Pair<String, LocalDate>>,
    val todaySummary: DailyShareSummary,
    val dayPlan: List<PlannedHabit>,
    val tomorrowPlan: List<PlannedHabit>,
    val monthPlan: Map<LocalDate, List<PlannedHabit>>,
    val allDailyHabits: List<com.habitsheet.domain.model.DailyHabit>,
    val weeklyPlans: List<WeeklyPlan>,
    val dayPlans: List<DayPlan>,
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
    private val onLocalChange: (() -> Unit)? = null,
    private val logger: Logger = NoOpLogger,
) {
    private val selectedMonth = MutableStateFlow(MonthKey.from(dateProvider.today()))
    private val selectedDay = MutableStateFlow(dateProvider.today())
    private val followToday = MutableStateFlow(true)
    private val onboardingVisible = MutableStateFlow(false)
    private val scrollToTodayTrigger = MutableStateFlow(0L)
    private val todayMode = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val completionMutex = Mutex()
    private val today = MutableStateFlow(dateProvider.today())

    /**
     * Re-reads today's date. Called every minute, when the app returns to the foreground and when the system
     * reports a date or time-zone change. If the person is following today, the selected day moves with it, and
     * so does the visible month if it was the month of the previous "today".
     */
    fun refreshToday() {
        val current = dateProvider.today()
        val previous = today.value
        if (current == previous) return
        if (followToday.value) {
            selectedDay.value = current
            if (selectedMonth.value == MonthKey.from(previous)) selectedMonth.value = MonthKey.from(current)
        }
        today.value = current
    }

    init {
        scope.launch {
            while (true) {
                delay(1.minutes) // Safety net; foreground and time-zone events call refreshToday() directly.
                refreshToday()
            }
        }
        scope.launch {
            runCatchingCancellable {
                // Existing installs that already have habits are not shown the first-run tutorial.
                onboardingVisible.value = !repository.isOnboardingCompleted() &&
                    repository.snapshot.value.dailyHabits.isEmpty()
            }.onFailure {
                logger.e(TAG, "loading onboarding state failed", it)
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
        initialValue = repository.snapshot.value.toUiState(selectedMonth.value, dateProvider.today(), selectedDay.value, false, 0, true, null),
    )

    fun setTodayMode(enabled: Boolean) {
        todayMode.value = enabled
        if (enabled) {
            followToday.value = true
            val currentDay = dateProvider.today()
            selectedDay.value = currentDay
            selectedMonth.value = MonthKey.from(currentDay)
        }
    }

    fun previousDay() = selectDay(LocalDate.fromEpochDays(selectedDay.value.toEpochDays() - 1))

    fun nextDay() = selectDay(LocalDate.fromEpochDays(selectedDay.value.toEpochDays() + 1))

    fun openDay(date: LocalDate) {
        selectDay(date)
        todayMode.value = true
    }

    fun currentDay() {
        followToday.value = true
        selectDay(dateProvider.today(), follow = true)
    }

    private fun selectDay(day: LocalDate, follow: Boolean = false) {
        followToday.value = follow
        selectedDay.value = day
        selectedMonth.value = MonthKey.from(day)
    }

    fun clearError() {
        error.value = null
    }

    fun completeOnboarding() {
        onboardingVisible.value = false
        scope.launch {
            runCatchingCancellable {
                repository.setOnboardingCompleted(true)
            }.onFailure {
                // Not critical: the tutorial may show once more.
                logger.w(TAG, "completeOnboarding failed", it)
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
        val planned = repository.snapshot.value.plannedHabitsOn(date).firstOrNull { it.habit.id == habitId && !it.skipped }
            ?: return
        togglePlanned(planned.id, date)
    }

    fun togglePlanned(planId: String, date: LocalDate) {
        scope.launch {
            runCatchingCancellable {
                completionMutex.withLock {
                    val planned = repository.snapshot.value.plannedHabitsOn(date).firstOrNull { it.id == planId && !it.skipped }
                        ?: return@withLock
                    val key = planId to date
                    val completed = repository.snapshot.value.dailyCompletions
                        .any { it.completed && (it.planId to it.date) == key }
                    repository.setDailyCompletion(
                        DailyHabitCompletion(planned.habit.id, date, !completed, dateProvider.nowEpochMillis(), planId),
                    )
                    onLocalChange?.invoke()
                }
            }.onFailure {
                logger.e(TAG, "togglePlanned failed", it)
                error.value = "Couldn't update habit. Please try again."
            }
        }
    }

    fun saveDayPlan(habitId: String, date: LocalDate, detail: String, skipped: Boolean = false, id: String = "$habitId|$date") {
        if (detail.isBlank()) return
        scope.launch {
            runCatchingCancellable {
                repository.saveDayPlan(DayPlan(habitId, date, detail.trim(), skipped, dateProvider.nowEpochMillis(), id))
            }.onFailure {
                logger.e(TAG, "saveDayPlan failed", it)
                error.value = "Couldn't save the day plan."
            }
        }
    }

    fun deleteDayPlan(habitId: String, date: LocalDate) {
        scope.launch {
            runCatchingCancellable { repository.deleteDayPlan(habitId, date) }.onFailure {
                logger.e(TAG, "deleteDayPlan failed", it)
                error.value = "Couldn't remove the day plan."
            }
        }
    }

    fun deleteDayPlanById(id: String) {
        scope.launch {
            runCatchingCancellable { repository.deleteDayPlanById(id) }.onFailure {
                logger.e(TAG, "deleteDayPlanById failed", it)
                error.value = "Couldn't remove the day plan."
            }
        }
    }

    fun saveWeeklyPlan(habitId: String, weekday: Int, detail: String) {
        if (detail.isBlank()) return
        scope.launch {
            runCatchingCancellable { repository.saveWeeklyPlan(WeeklyPlan(habitId, weekday, detail.trim(), dateProvider.nowEpochMillis())) }.onFailure {
                logger.e(TAG, "saveWeeklyPlan failed", it)
                error.value = "Couldn't save the weekly plan."
            }
        }
    }

    fun deleteWeeklyPlan(habitId: String, weekday: Int) {
        scope.launch {
            runCatchingCancellable { repository.deleteWeeklyPlan(habitId, weekday) }.onFailure {
                logger.e(TAG, "deleteWeeklyPlan failed", it)
                error.value = "Couldn't remove the weekly plan."
            }
        }
    }

    fun toggleWeekly(habitId: String, weekStartDate: LocalDate) {
        scope.launch {
            runCatchingCancellable {
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
            }.onFailure {
                logger.e(TAG, "toggleWeekly failed", it)
                error.value = "Couldn't update weekly habit. Please try again."
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "Month"
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
    val monthPlan = month.dates().associateWith(::plannedHabitsOn)
    val dueKeys = monthPlan.flatMap { (date, planned) -> planned.filterNot { it.skipped }.map { it.id to date } }.toSet()
    val completedKeys = dailyCompletions.filter { it.completed }.map { it.planId to it.date }.toSet()
    val habitSummaries = dailyHabits.filter { habit -> month.dates().any(habit::isActiveOn) }
        .sortedBy { it.displayOrder }
        .map { habit ->
            val planned = monthPlan.flatMap { (date, items) -> items.filter { it.habit.id == habit.id && !it.skipped }.map { it.id to date } }
            HabitSummary(habit, planned.count { it in completedKeys }, planned.size)
        }
    val dailySummaries = month.dates().map { date ->
        val due = dueKeys.count { it.second == date }
        val done = dueKeys.count { it.second == date && it in completedKeys }
        DailySummary(date, done, due - done, due)
    }
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
            .map { it.planId to it.date }
            .toSet(),
        dueKeys = dueKeys,
        weeklyCompletionKeys = weeklyCompletions
            .asSequence()
            .filter { it.completed && it.weekStartDate in weekStarts }
            .map { it.weeklyHabitId to it.weekStartDate }
            .toSet(),
        todaySummary = HabitCalculations.plannedDailyShareSummary(selectedDay, this),
        dayPlan = plannedHabitsOn(selectedDay),
        tomorrowPlan = plannedHabitsOn(LocalDate.fromEpochDays(selectedDay.toEpochDays() + 1)),
        monthPlan = monthPlan,
        allDailyHabits = dailyHabits,
        weeklyPlans = weeklyPlans,
        dayPlans = dayPlans,
        onboardingVisible = onboardingVisible,
        scrollToTodayTrigger = scrollToTodayTrigger,
        todayMode = todayMode,
        error = error,
    )
}
