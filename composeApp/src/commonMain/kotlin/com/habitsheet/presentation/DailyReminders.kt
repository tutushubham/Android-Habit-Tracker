package com.habitsheet.presentation

import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.domain.repository.SettingsStore
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.NotificationPermissionStatus
import com.habitsheet.platform.ReminderPlatform
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.resources.*

/** What turning the reminder on led to. */
enum class ReminderOutcome { ON, OFF, PERMISSION_DENIED, FAILED }

/**
 * The daily reminder: the stored setting ([SettingsStore.getDailyReminder]) and the scheduled notification kept in
 * step. The notification text comes from the catalog, resolved now in the person's language.
 */
class DailyReminders(
    private val settings: SettingsStore,
    private val platform: ReminderPlatform,
    private val logger: Logger = NoOpLogger,
    private val resolveText: suspend (UiText) -> String = { it.resolve() },
) {
    suspend fun current(): DailyReminder? = settings.getDailyReminder()

    suspend fun permission(): NotificationPermissionStatus = platform.permission.status()

    /** Turns the reminder on at [reminder] (asking for permission when needed) or off for null. */
    suspend fun set(reminder: DailyReminder?): ReminderOutcome = runCatchingCancellable {
        when {
            reminder == null -> {
                platform.scheduler.cancel()
                settings.setDailyReminder(null)
                ReminderOutcome.OFF
            }

            !allowed() -> ReminderOutcome.PERMISSION_DENIED

            else -> {
                settings.setDailyReminder(reminder)
                schedule(reminder)
                ReminderOutcome.ON
            }
        }
    }.getOrElse {
        logger.e(TAG, "Changing the reminder failed", it)
        ReminderOutcome.FAILED
    }

    /**
     * At every start: schedules the stored reminder again (an Android reboot, an update or a language change may
     * have dropped or outdated it). Does nothing when it is off or notifications are not allowed.
     */
    suspend fun restore() {
        val reminder = runCatching { settings.getDailyReminder() }.getOrNull() ?: return
        if (platform.permission.status() == NotificationPermissionStatus.GRANTED) schedule(reminder)
    }

    fun openSystemSettings() = platform.permission.openSystemSettings()

    private suspend fun allowed(): Boolean = when (platform.permission.status()) {
        NotificationPermissionStatus.GRANTED -> true
        else -> platform.permission.request()
    }

    private suspend fun schedule(reminder: DailyReminder) {
        platform.scheduler.schedule(reminder, resolveText(UiText.of(Res.string.reminder_title)), resolveText(UiText.of(Res.string.reminder_body)))
    }

    private companion object {
        const val TAG = "Reminders"
    }
}
