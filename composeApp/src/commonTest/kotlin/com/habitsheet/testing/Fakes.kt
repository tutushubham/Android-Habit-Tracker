package com.habitsheet.testing

import com.habitsheet.domain.model.DailyReminder
import com.habitsheet.platform.LogLevel
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NotificationPermission
import com.habitsheet.platform.NotificationPermissionStatus
import com.habitsheet.platform.ReminderScheduler
import com.habitsheet.platform.formatLogLine
import com.habitsheet.presentation.DateProvider
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A [DateProvider] a test steers by hand. [today] and [nowMillis] are plain properties, so a test moves the clock by
 * assigning to them or with [advanceDays] / [advanceMillis]. [tickMillis] makes every call to [nowEpochMillis] return a
 * strictly later stamp (use 1 where a test depends on `updated_at` ordering, 0 where the stamp itself is irrelevant).
 */
class FakeDateProvider(
    var today: LocalDate = LocalDate(2026, 10, 1),
    var nowMillis: Long = 1_000L,
    private val tickMillis: Long = 0L,
) : DateProvider {
    override fun today(): LocalDate = today

    override fun nowEpochMillis(): Long {
        val stamp = nowMillis
        nowMillis += tickMillis
        return stamp
    }

    fun advanceDays(days: Int) {
        today = today.plus(DatePeriod(days = days))
    }

    fun advanceMillis(millis: Long) {
        nowMillis += millis
    }
}

/** Records everything logged so a test can assert on levels, tags and on what a platform logger would print. */
class FakeLogger : Logger {
    data class Entry(val level: LogLevel, val tag: String, val message: String, val throwable: Throwable?)

    val entries = mutableListOf<Entry>()

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        entries += Entry(level, tag, message, throwable)
    }

    fun at(level: LogLevel): List<Entry> = entries.filter { it.level == level }

    /** Everything a platform logger would print: tag, message and the redacted throwable summary. */
    fun printed(): String = entries.joinToString("\n") { "${it.tag} ${formatLogLine(it.message, it.throwable)}" }
}

/** Records what would be scheduled; [scheduled] is null when nothing is. */
class FakeReminderScheduler : ReminderScheduler {
    data class Scheduled(val reminder: DailyReminder, val title: String, val body: String)

    var scheduled: Scheduled? = null
        private set
    var calls = 0
        private set

    override fun schedule(reminder: DailyReminder, title: String, body: String) {
        calls++
        scheduled = Scheduled(reminder, title, body)
    }

    override fun cancel() {
        calls++
        scheduled = null
    }
}

/** The permission as the person would answer: [answer] is what [request] returns (and makes [status]). */
class FakeNotificationPermission(
    var current: NotificationPermissionStatus = NotificationPermissionStatus.NOT_ASKED,
    var answer: Boolean = true,
) : NotificationPermission {
    var requests = 0
        private set
    var settingsOpened = 0
        private set

    override suspend fun status() = current

    override suspend fun request(): Boolean {
        requests++
        current = if (answer) NotificationPermissionStatus.GRANTED else NotificationPermissionStatus.DENIED
        return answer
    }

    override fun openSystemSettings() {
        settingsOpened++
    }
}
