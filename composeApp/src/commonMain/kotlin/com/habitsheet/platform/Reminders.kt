package com.habitsheet.platform

import com.habitsheet.domain.model.DailyReminder

/** Schedules the one daily local notification. Scheduling again replaces it; nothing leaves the device. */
interface ReminderScheduler {
    fun schedule(reminder: DailyReminder, title: String, body: String)

    fun cancel()
}

enum class NotificationPermissionStatus { GRANTED, DENIED, NOT_ASKED }

/** The system's permission to show notifications (Android 13+ runtime permission, iOS authorisation). */
interface NotificationPermission {
    suspend fun status(): NotificationPermissionStatus

    /** Asks the person (the system dialog, shown at most once by the OS); true when notifications may be shown. */
    suspend fun request(): Boolean

    /** Opens the app's notification settings, for when the person said no earlier. */
    fun openSystemSettings()
}

/** The platform's reminder pieces, passed to the app graph. [NONE] for tests and platforms without reminders. */
class ReminderPlatform(val scheduler: ReminderScheduler, val permission: NotificationPermission) {
    companion object {
        val NONE = ReminderPlatform(
            object : ReminderScheduler {
                override fun schedule(reminder: DailyReminder, title: String, body: String) = Unit

                override fun cancel() = Unit
            },
            object : NotificationPermission {
                override suspend fun status() = NotificationPermissionStatus.DENIED

                override suspend fun request() = false

                override fun openSystemSettings() = Unit
            },
        )
    }
}
