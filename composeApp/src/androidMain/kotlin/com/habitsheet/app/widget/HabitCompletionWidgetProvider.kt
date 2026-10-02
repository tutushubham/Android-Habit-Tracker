package com.habitsheet.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.habitsheet.app.AndroidDriverFactory
import com.habitsheet.app.MainActivity
import com.habitsheet.app.R
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.plannedHabitsOn
import com.habitsheet.presentation.SystemDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import java.text.DateFormat
import java.util.Date

/**
 * Responsive home-screen widget for checking off today's daily and weekly habits.
 *
 * It deliberately uses the platform AppWidget API instead of adding a second UI
 * framework to the app. Each interaction is validated against the current date
 * and repository snapshot before it is persisted.
 */
class HabitCompletionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidgetsAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetsAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_DAILY, ACTION_TOGGLE_WEEKLY -> handleToggle(context, intent)
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            -> HabitWidgetUpdater.requestUpdate(context)
        }
    }

    private fun handleToggle(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                mutationMutex.withLock {
                    val today = SystemDateProvider.today()
                    val renderedDate = intent.getStringExtra(EXTRA_RENDERED_DATE)
                    val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
                    if (renderedDate != today.toString() || habitId.isNullOrBlank()) {
                        // A launcher can retain yesterday's RemoteViews after midnight.
                        // Refresh instead of recording a completion for an unseen date.
                        return@withLock
                    }

                    withRepository(context) { repository ->
                        when (intent.action) {
                            ACTION_TOGGLE_DAILY -> toggleDaily(repository, habitId, today)
                            ACTION_TOGGLE_WEEKLY -> toggleWeekly(repository, habitId, today)
                        }
                    }
                }
            } finally {
                HabitWidgetUpdater.requestUpdate(context)
                context.sendBroadcast(
                    Intent(ACTION_WIDGET_DATA_CHANGED).setPackage(context.packageName),
                )
                pendingResult.finish()
            }
        }
    }

    private suspend fun toggleDaily(
        repository: LocalHabitRepository,
        habitId: String,
        today: LocalDate,
    ) {
        val snapshot = repository.snapshot.value
        if (snapshot.plannedHabitsOn(today).none { it.habit.id == habitId && !it.skipped }) return
        val completed = snapshot.dailyCompletions.any {
            it.habitId == habitId && it.date == today && it.completed
        }
        repository.setDailyCompletion(
            DailyHabitCompletion(
                habitId = habitId,
                date = today,
                completed = !completed,
                updatedAtEpochMillis = SystemDateProvider.nowEpochMillis(),
            ),
        )
    }

    private suspend fun toggleWeekly(
        repository: LocalHabitRepository,
        habitId: String,
        today: LocalDate,
    ) {
        val snapshot = repository.snapshot.value
        val habit = snapshot.weeklyHabits.firstOrNull { it.id == habitId } ?: return
        if (!habit.isActiveOn(today)) return
        val weekStart = MonthKey.from(today).weekStartFor(today) ?: return
        val completed = snapshot.weeklyCompletions.any {
            it.weeklyHabitId == habitId && it.weekStartDate == weekStart && it.completed
        }
        repository.setWeeklyCompletion(
            WeeklyHabitCompletion(
                weeklyHabitId = habitId,
                weekStartDate = weekStart,
                completed = !completed,
                updatedAtEpochMillis = SystemDateProvider.nowEpochMillis(),
            ),
        )
    }

    private fun updateWidgetsAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                widgetIds.forEach { widgetId ->
                    updateWidget(context, appWidgetManager, widgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_DATA_CHANGED =
            "com.habitsheet.app.action.WIDGET_DATA_CHANGED"

        private const val ACTION_TOGGLE_DAILY =
            "com.habitsheet.app.action.WIDGET_TOGGLE_DAILY"
        private const val ACTION_TOGGLE_WEEKLY =
            "com.habitsheet.app.action.WIDGET_TOGGLE_WEEKLY"
        private const val EXTRA_HABIT_ID = "habit_id"
        private const val EXTRA_RENDERED_DATE = "rendered_date"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutationMutex = Mutex()

        internal fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
        ) {
            val views = runCatching {
                withRepository(context) { repository ->
                    buildRemoteViews(context, manager, widgetId, repository.snapshot.value)
                }
            }.getOrElse {
                errorRemoteViews(context)
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun buildRemoteViews(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            snapshot: HabitSnapshot,
        ): RemoteViews {
            val today = SystemDateProvider.today()
            val currentWeekStart = MonthKey.from(today).weekStartFor(today)
            val dailyCompletionIds = snapshot.dailyCompletions.asSequence()
                .filter { it.date == today && it.completed }
                .map { it.habitId }
                .toSet()
            val weeklyCompletionIds = snapshot.weeklyCompletions.asSequence()
                .filter { it.weekStartDate == currentWeekStart && it.completed }
                .map { it.weeklyHabitId }
                .toSet()

            val items = buildList {
                snapshot.plannedHabitsOn(today)
                    .filterNot { it.skipped }
                    .forEach { plan ->
                        add(
                            WidgetHabit(
                                id = plan.habit.id,
                                name = listOfNotNull(plan.habit.name, plan.detail).joinToString(" · "),
                                weekly = false,
                                completed = plan.habit.id in dailyCompletionIds,
                            ),
                        )
                    }
                snapshot.weeklyHabits
                    .filter { it.isActiveOn(today) }
                    .sortedBy { it.displayOrder }
                    .forEach { habit ->
                        add(
                            WidgetHabit(
                                id = habit.id,
                                name = habit.name,
                                weekly = true,
                                completed = habit.id in weeklyCompletionIds,
                            ),
                        )
                    }
            }.sortedWith(compareBy<WidgetHabit> { it.completed }.thenBy { it.weekly })

            val options = manager.getAppWidgetOptions(widgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            val maxRows = when {
                minHeight < 150 -> 1
                minHeight < 220 -> 3
                minHeight < 300 -> 5
                else -> 7
            }
            val completed = items.count { it.completed }

            return RemoteViews(context.packageName, R.layout.habit_completion_widget).apply {
                setTextViewText(
                    R.id.widget_date,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()),
                )
                setTextViewText(
                    R.id.widget_progress_text,
                    context.resources.getQuantityString(
                        R.plurals.widget_progress,
                        items.size,
                        completed,
                        items.size,
                    ),
                )
                setProgressBar(R.id.widget_progress, items.size.coerceAtLeast(1), completed, false)
                setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_progress_text, openAppPendingIntent(context))
                removeAllViews(R.id.widget_habit_list)

                if (items.isEmpty()) {
                    setViewVisibility(R.id.widget_empty, View.VISIBLE)
                    setViewVisibility(R.id.widget_habit_list, View.GONE)
                    setTextViewText(R.id.widget_footer, context.getString(R.string.widget_add_habits))
                    setOnClickPendingIntent(R.id.widget_footer, openAppPendingIntent(context))
                } else {
                    setViewVisibility(R.id.widget_empty, View.GONE)
                    setViewVisibility(R.id.widget_habit_list, View.VISIBLE)
                    items.take(maxRows).forEach { item ->
                        addView(
                            R.id.widget_habit_list,
                            habitRow(context, widgetId, today, item),
                        )
                    }
                    val remaining = (items.size - maxRows).coerceAtLeast(0)
                    setTextViewText(
                        R.id.widget_footer,
                        if (remaining > 0) {
                            context.resources.getQuantityString(
                                R.plurals.widget_more_habits,
                                remaining,
                                remaining,
                            )
                        } else if (completed == items.size) {
                            context.getString(R.string.widget_all_done)
                        } else {
                            context.getString(R.string.widget_tap_to_open)
                        },
                    )
                    setOnClickPendingIntent(R.id.widget_footer, openAppPendingIntent(context))
                }
            }
        }

        private fun habitRow(
            context: Context,
            widgetId: Int,
            today: LocalDate,
            item: WidgetHabit,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.habit_completion_widget_row).apply {
            setTextViewText(R.id.widget_habit_name, item.name)
            setTextViewText(
                R.id.widget_habit_type,
                if (item.weekly) context.getString(R.string.widget_weekly) else "",
            )
            setViewVisibility(R.id.widget_habit_type, if (item.weekly) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_check, if (item.completed) "✓" else "")
            setTextColor(
                R.id.widget_check,
                context.getColor(if (item.completed) R.color.widget_check_mark else R.color.widget_text),
            )
            setInt(
                R.id.widget_check,
                "setBackgroundResource",
                if (item.completed) R.drawable.widget_check_on else R.drawable.widget_check_off,
            )
            setContentDescription(
                R.id.widget_check,
                context.getString(
                    if (item.completed) R.string.widget_mark_incomplete else R.string.widget_mark_complete,
                    item.name,
                ),
            )
            setOnClickPendingIntent(
                R.id.widget_habit_row,
                togglePendingIntent(context, widgetId, today, item),
            )
        }

        private fun togglePendingIntent(
            context: Context,
            widgetId: Int,
            today: LocalDate,
            item: WidgetHabit,
        ): PendingIntent {
            val action = if (item.weekly) ACTION_TOGGLE_WEEKLY else ACTION_TOGGLE_DAILY
            val intent = Intent(context, HabitCompletionWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_HABIT_ID, item.id)
                putExtra(EXTRA_RENDERED_DATE, today.toString())
            }
            val requestCode = "$widgetId:$action:${item.id}".hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun errorRemoteViews(context: Context) =
            RemoteViews(context.packageName, R.layout.habit_completion_widget).apply {
                setTextViewText(R.id.widget_progress_text, context.getString(R.string.widget_unavailable))
                setProgressBar(R.id.widget_progress, 1, 0, false)
                setViewVisibility(R.id.widget_habit_list, View.GONE)
                setViewVisibility(R.id.widget_empty, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_footer, openAppPendingIntent(context))
            }

        private inline fun <T> withRepository(
            context: Context,
            block: (LocalHabitRepository) -> T,
        ): T {
            val repository = LocalHabitRepository(AndroidDriverFactory(context.applicationContext))
            return try {
                block(repository)
            } finally {
                repository.close()
            }
        }
    }
}

/** Single entry point used by the activity after foreground edits and resume. */
object HabitWidgetUpdater {
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, HabitCompletionWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val updateIntent = Intent(appContext, HabitCompletionWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        appContext.sendBroadcast(updateIntent)
    }
}

private data class WidgetHabit(
    val id: String,
    val name: String,
    val weekly: Boolean,
    val completed: Boolean,
)
