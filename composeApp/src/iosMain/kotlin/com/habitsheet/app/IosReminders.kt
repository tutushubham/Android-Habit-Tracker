@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.habitsheet.app

import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.platform.NotificationPermission
import com.habitsheet.platform.NotificationPermissionStatus
import com.habitsheet.platform.ReminderScheduler
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/** The fixed identifier: scheduling again replaces the pending request instead of adding a second one. */
internal const val DAILY_REMINDER_ID = "daily-reminder"

/** The repeating request for [reminder] at its hour and minute, every day, in the device's current time zone. */
internal fun dailyReminderRequest(reminder: DailyReminder, title: String, body: String): UNNotificationRequest {
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound)
    }
    val time = NSDateComponents().apply {
        hour = reminder.hour.toLong()
        minute = reminder.minute.toLong()
    }
    val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(time, repeats = true)
    return UNNotificationRequest.requestWithIdentifier(DAILY_REMINDER_ID, content, trigger)
}

/** iOS keeps a pending repeating request across restarts; nothing to set again after a reboot. */
class IosReminderScheduler : ReminderScheduler {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun schedule(reminder: DailyReminder, title: String, body: String) {
        center.addNotificationRequest(dailyReminderRequest(reminder, title, body), withCompletionHandler = null)
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(DAILY_REMINDER_ID))
    }
}

class IosNotificationPermission : NotificationPermission {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun status(): NotificationPermissionStatus = suspendCancellableCoroutine { continuation ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> NotificationPermissionStatus.GRANTED
                UNAuthorizationStatusNotDetermined -> NotificationPermissionStatus.NOT_ASKED
                else -> NotificationPermissionStatus.DENIED
            }
            if (continuation.isActive) continuation.resume(status)
        }
    }

    override suspend fun request(): Boolean = suspendCancellableCoroutine { continuation ->
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

    override fun openSystemSettings() {
        // The app's page in Settings, which lists Notifications (the direct notification-settings URL needs iOS 16).
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}
