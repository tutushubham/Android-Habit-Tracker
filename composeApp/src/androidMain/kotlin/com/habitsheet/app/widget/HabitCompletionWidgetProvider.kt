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
import com.habitsheet.app.AndroidLogger
import com.habitsheet.app.MainActivity
import com.habitsheet.app.R
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.presentation.SystemDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
                runWidgetWork(storeOpener(context), logger, lock = mutationMutex) { store ->
                    val today = SystemDateProvider.today()
                    val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
                    // A launcher can retain yesterday's RemoteViews after midnight.
                    // Refresh instead of recording a completion for an unseen date.
                    if (habitId == null || !HabitWidgetLogic.isForToday(intent.getStringExtra(EXTRA_RENDERED_DATE), habitId, today)) return@runWidgetWork
                    val now = SystemDateProvider.nowEpochMillis()
                    when (intent.action) {
                        ACTION_TOGGLE_DAILY -> HabitWidgetLogic.toggleDaily(store, intent.getStringExtra(EXTRA_PLAN_ID), habitId, today, now)
                        ACTION_TOGGLE_WEEKLY -> HabitWidgetLogic.toggleWeekly(store, habitId, today, now)
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
        private const val EXTRA_PLAN_ID = "plan_id"
        private const val EXTRA_RENDERED_DATE = "rendered_date"

        /** All database work runs here, off the main thread; every job is bounded by [runWidgetWork]. */
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutationMutex = Mutex()
        private val logger = AndroidLogger()

        /** Opens the app's own database (the same repository and queries as the app) for one action. */
        private fun storeOpener(context: Context) = WidgetStoreOpener {
            val repository = LocalHabitRepository(AndroidDriverFactory(context.applicationContext))
            WidgetStore(repository) { repository.close() }
        }

        internal suspend fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
        ) {
            var snapshot: HabitSnapshot? = null
            val read = runWidgetWork(storeOpener(context), logger) { store -> snapshot = store.snapshot.value }
            // A damaged or missing database (or anything failing while drawing) shows the "unavailable" state.
            val views = snapshot.takeIf { read }
                ?.let { data -> runCatching { buildRemoteViews(context, manager, widgetId, data) }.getOrNull() }
                ?: errorRemoteViews(context)
            manager.updateAppWidget(widgetId, views)
        }

        private fun buildRemoteViews(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            snapshot: HabitSnapshot,
        ): RemoteViews {
            val today = SystemDateProvider.today()
            val items = HabitWidgetLogic.items(snapshot, today)

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
                item.planId?.let { putExtra(EXTRA_PLAN_ID, it) }
                putExtra(EXTRA_RENDERED_DATE, today.toString())
            }
            val requestCode = "$widgetId:$action:${item.planId ?: item.id}".hashCode()
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
