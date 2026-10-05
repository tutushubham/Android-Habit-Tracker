package com.habitsheet.app.widget

import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.sessionToToggle
import com.habitsheet.domain.model.todaySessions
import com.habitsheet.domain.repository.HabitStore
import com.habitsheet.platform.Logger
import com.habitsheet.platform.e
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One row of the widget: a daily session (with its plan id) or a weekly habit. */
internal data class WidgetHabit(
    val id: String,
    val planId: String? = null,
    val name: String,
    val weekly: Boolean,
    val completed: Boolean,
)

/**
 * The widget's rules, free of RemoteViews: what it lists and what a tap writes. Reads and writes go through the
 * app's own [HabitStore] (the same queries, pending-upload flag and monotonic timestamps as a tap in the app).
 */
internal object HabitWidgetLogic {
    /** Today's sessions (one row per plan id) and this week's active weekly habits; open ones first, daily before weekly. */
    fun items(snapshot: HabitSnapshot, today: LocalDate): List<WidgetHabit> {
        val currentWeekStart = MonthKey.from(today).weekStartFor(today)
        val weeklyCompletionIds = snapshot.weeklyCompletions.asSequence()
            .filter { it.weekStartDate == currentWeekStart && it.completed }
            .map { it.weeklyHabitId }
            .toSet()
        return buildList {
            snapshot.todaySessions(today).forEach { session ->
                add(WidgetHabit(id = session.habitId, planId = session.planId, name = session.label, weekly = false, completed = session.completed))
            }
            snapshot.weeklyHabits
                .filter { it.isActiveOn(today) }
                .sortedBy { it.displayOrder }
                .forEach { habit ->
                    add(WidgetHabit(id = habit.id, name = habit.name, weekly = true, completed = habit.id in weeklyCompletionIds))
                }
        }.sortedWith(compareBy<WidgetHabit> { it.completed }.thenBy { it.weekly })
    }

    /** A launcher can keep yesterday's rows after midnight: a tap on them must not record anything. */
    fun isForToday(renderedDate: String?, habitId: String?, today: LocalDate): Boolean =
        renderedDate == today.toString() && !habitId.isNullOrBlank()

    suspend fun toggleDaily(store: HabitStore, planId: String?, habitId: String, today: LocalDate, nowMillis: Long) {
        val snapshot = store.snapshot.value
        val session = snapshot.sessionToToggle(today, planId, habitId) ?: return
        val completed = snapshot.dailyCompletions.any { it.planId == session.id && it.date == today && it.completed }
        store.setDailyCompletion(
            DailyHabitCompletion(
                habitId = session.habit.id,
                date = today,
                completed = !completed,
                updatedAtEpochMillis = nowMillis,
                planId = session.id,
            ),
        )
    }

    suspend fun toggleWeekly(store: HabitStore, habitId: String, today: LocalDate, nowMillis: Long) {
        val snapshot = store.snapshot.value
        val habit = snapshot.weeklyHabits.firstOrNull { it.id == habitId } ?: return
        if (!habit.isActiveOn(today)) return
        val weekStart = MonthKey.from(today).weekStartFor(today) ?: return
        val completed = snapshot.weeklyCompletions.any { it.weeklyHabitId == habitId && it.weekStartDate == weekStart && it.completed }
        store.setWeeklyCompletion(
            WeeklyHabitCompletion(weeklyHabitId = habitId, weekStartDate = weekStart, completed = !completed, updatedAtEpochMillis = nowMillis),
        )
    }
}

/** The app's data, opened for one widget action. Closing it releases the database file. */
internal class WidgetStore(val habits: HabitStore, private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
}

internal fun interface WidgetStoreOpener {
    fun open(): WidgetStore
}

/**
 * Broadcast receivers have about 10 seconds after `goAsync()`; widget work gives up well before that so
 * `finish()` always runs in time. (It bounds waiting for [lock] and anything else that suspends; a single SQLite
 * statement is not interruptible and relies on SQLite's own busy timeout.)
 */
internal val WIDGET_WORK_TIMEOUT: Duration = 8.seconds

/**
 * Runs one widget action: takes [lock] (if any), opens the store, runs [block], always closes the store. Never throws
 * (except to propagate the caller's cancellation): a failure, such as a damaged database, or a timeout is logged
 * (class names only) and reported as `false`, so the widget can show its "unavailable" state instead of crashing.
 * The lock covers opening the store, so a second tap sees what the first one wrote.
 */
internal suspend fun runWidgetWork(
    opener: WidgetStoreOpener,
    logger: Logger,
    timeout: Duration = WIDGET_WORK_TIMEOUT,
    lock: Mutex? = null,
    block: suspend (HabitStore) -> Unit,
): Boolean = try {
    withTimeout(timeout) {
        val work: suspend () -> Unit = { opener.open().use { store -> block(store.habits) } }
        if (lock != null) lock.withLock { work() } else work()
    }
    true
} catch (e: TimeoutCancellationException) {
    logger.e(WIDGET_TAG, "Widget work timed out", e)
    false
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    logger.e(WIDGET_TAG, "Widget work failed", e)
    false
}

private const val WIDGET_TAG = "Widget"
