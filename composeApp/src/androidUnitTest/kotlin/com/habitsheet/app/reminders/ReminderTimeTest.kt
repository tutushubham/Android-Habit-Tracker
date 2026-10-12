package com.habitsheet.app.reminders

import com.habitsheet.domain.model.DailyReminder
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderTimeTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    /** [date] as yyyy-mm-dd and [time] as hh:mm, local to [zone]. */
    private fun at(zone: TimeZone, date: String, time: String): Long = Calendar.getInstance(zone).apply {
        val (year, month, day) = date.split("-").map(String::toInt)
        val (hour, minute) = time.split(":").map(String::toInt)
        clear()
        set(year, month - 1, day, hour, minute)
    }.timeInMillis

    @Test
    fun theNextReminderIsLaterTodayOrElseTomorrow() {
        val sevenThirty = DailyReminder(7 * 60 + 30)
        assertEquals(at(berlin, "2026-10-12", "07:30"), AndroidReminderScheduler.nextTriggerMillis(at(berlin, "2026-10-12", "06:00"), berlin, sevenThirty))
        assertEquals(at(berlin, "2026-10-13", "07:30"), AndroidReminderScheduler.nextTriggerMillis(at(berlin, "2026-10-12", "07:30"), berlin, sevenThirty))
        assertEquals(at(berlin, "2026-10-13", "07:30"), AndroidReminderScheduler.nextTriggerMillis(at(berlin, "2026-10-12", "21:00"), berlin, sevenThirty))
    }

    @Test
    fun theLocalTimeIsKeptAcrossADaylightSavingChange() {
        // Clocks go back on 25 October 2026 in Berlin: the reminder is still at 07:30 local time.
        val next = AndroidReminderScheduler.nextTriggerMillis(at(berlin, "2026-10-24", "22:00"), berlin, DailyReminder(7 * 60 + 30))
        assertEquals(at(berlin, "2026-10-25", "07:30"), next)
    }

    @Test
    fun anotherTimeZoneGivesAnotherInstant() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        val now = at(berlin, "2026-10-12", "06:00")
        assertEquals(at(tokyo, "2026-10-13", "07:30"), AndroidReminderScheduler.nextTriggerMillis(now, tokyo, DailyReminder(450)))
    }
}
