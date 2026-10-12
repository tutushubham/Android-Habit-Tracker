package com.habitsheet.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.habitsheet.app.MainActivity
import com.habitsheet.app.R

/**
 * Shows the daily reminder when its alarm fires, and sets the alarm again after a reboot, an app update or a change of
 * clock or time zone (Android drops alarms on reboot; a new zone moves the local time).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = AndroidReminderScheduler(context)
        when (intent.action) {
            ACTION_REMIND -> scheduler.stored()?.let { show(context, it.title, it.body) }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> scheduler.rescheduleStored()
        }
    }

    companion object {
        const val ACTION_REMIND = "com.habitsheet.app.action.DAILY_REMINDER"
        const val CHANNEL_ID = "daily_reminder"
        private const val NOTIFICATION_ID = 7301

        fun show(context: Context, title: String, body: String) {
            val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val manager = NotificationManagerCompat.from(context)
            if (!allowed || !manager.areNotificationsEnabled()) return
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // The permission was withdrawn between the check and the post: nothing to show.
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
