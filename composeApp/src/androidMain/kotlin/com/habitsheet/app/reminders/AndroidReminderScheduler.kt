package com.habitsheet.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.platform.ReminderScheduler
import java.util.Calendar
import java.util.TimeZone

/**
 * The daily reminder as an inexact repeating alarm (no exact-alarm permission; a few minutes' drift is fine for a
 * nudge). Android forgets alarms on reboot, so the schedule is also kept in shared preferences and [ReminderReceiver]
 * sets it again after a reboot, an app update or a clock / time-zone change.
 */
class AndroidReminderScheduler(context: Context) : ReminderScheduler {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun schedule(reminder: DailyReminder, title: String, body: String) {
        prefs.edit {
            putInt(KEY_MINUTE, reminder.minuteOfDay)
            putString(KEY_TITLE, title)
            putString(KEY_BODY, body)
        }
        setAlarm(reminder)
    }

    override fun cancel() {
        prefs.edit { clear() }
        val intent = alarmIntent()
        alarmManager().cancel(intent)
        intent.cancel()
    }

    /** Sets the alarm again from what [schedule] stored; nothing when the reminder is off. */
    fun rescheduleStored() {
        stored()?.let { setAlarm(it.reminder) }
    }

    internal fun stored(): StoredReminder? {
        val minute = prefs.getInt(KEY_MINUTE, -1).takeIf { it >= 0 } ?: return null
        return StoredReminder(DailyReminder(minute), prefs.getString(KEY_TITLE, null).orEmpty(), prefs.getString(KEY_BODY, null).orEmpty())
    }

    private fun setAlarm(reminder: DailyReminder) {
        val first = nextTriggerMillis(System.currentTimeMillis(), TimeZone.getDefault(), reminder)
        alarmManager().setInexactRepeating(AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY, alarmIntent())
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal data class StoredReminder(val reminder: DailyReminder, val title: String, val body: String)

    companion object {
        private const val PREFS = "daily_reminder"
        private const val KEY_MINUTE = "minute_of_day"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val REQUEST_CODE = 7301

        /** The next time [reminder] falls due after [nowMillis] in [zone] (later today, else tomorrow). */
        fun nextTriggerMillis(nowMillis: Long, zone: TimeZone, reminder: DailyReminder): Long {
            val calendar = Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= nowMillis) calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }
    }
}
