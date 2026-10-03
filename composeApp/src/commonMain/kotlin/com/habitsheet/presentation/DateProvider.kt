package com.habitsheet.presentation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface DateProvider {
    /** The calendar date on the device right now, in the time zone the device currently uses. */
    fun today(): LocalDate
    fun nowEpochMillis(): Long
}

/**
 * [today] is always derived from [clock] in whatever [zone] returns at that moment; nothing is cached, so a
 * time-zone change or midnight is seen by the next call. Stored dates are plain [LocalDate]s: moving to another
 * zone never rewrites a date that was recorded earlier.
 */
class ClockDateProvider(
    private val clock: Clock = Clock.System,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : DateProvider {
    override fun today(): LocalDate = clock.todayIn(zone())
    override fun nowEpochMillis(): Long = clock.now().toEpochMilliseconds()
}

object SystemDateProvider : DateProvider by ClockDateProvider()
