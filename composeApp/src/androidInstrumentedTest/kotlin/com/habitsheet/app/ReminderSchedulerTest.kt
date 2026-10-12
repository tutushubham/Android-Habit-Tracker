package com.habitsheet.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitsheet.app.reminders.AndroidReminderScheduler
import com.habitsheet.app.reminders.ReminderReceiver
import com.habitsheet.domain.model.DailyReminder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The daily reminder on a device: the alarm, setting it again after a reboot, and the notification it shows. */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val context: Context = targetContext
    private val scheduler = AndroidReminderScheduler(context)

    private fun alarm(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        7301,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    )

    @Before
    @After
    fun noReminder() {
        scheduler.cancel()
        alarm()?.cancel()
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test
    fun schedulingSetsTheAlarmAndCancellingRemovesIt() {
        scheduler.schedule(DailyReminder(450), "Habit Sheet", "Time to check off today’s habits.")
        assertNotNull(alarm(), "alarm set")
        assertEquals(DailyReminder(450), scheduler.stored()?.reminder)

        scheduler.cancel()
        assertNull(alarm(), "alarm removed")
        assertNull(scheduler.stored())
    }

    @Test
    fun aRebootSetsTheStoredAlarmAgainAndDoesNothingWhenOff() {
        scheduler.schedule(DailyReminder(450), "Habit Sheet", "Body")
        alarm()?.cancel() // what a reboot does to alarms
        assertNull(alarm())

        ReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(alarm(), "set again from the stored schedule")

        scheduler.cancel()
        ReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(alarm(), "off stays off")
    }

    @Test
    fun theAlarmShowsTheStoredTextWhenNotificationsAreAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        scheduler.schedule(DailyReminder(450), "Habit Sheet", "Time to check off today’s habits.")

        ReminderReceiver().onReceive(context, Intent(ReminderReceiver.ACTION_REMIND))

        val manager = context.getSystemService(NotificationManager::class.java)
        waitUntil { manager.activeNotifications.any { it.id == 7301 } }
        val notification = manager.activeNotifications.singleOrNull { it.id == 7301 }?.notification
        assertNotNull(notification, "notification posted")
        assertEquals("Habit Sheet", notification.extras.getString("android.title"))
        assertEquals("Time to check off today’s habits.", notification.extras.getString("android.text"))
        assertTrue(notification.contentIntent != null, "tapping opens the app")
    }
}
